package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.llm.LlmClient;
import com.prlens.llm.LlmRequest;
import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.DiffLine;
import com.prlens.model.FileStatus;
import com.prlens.model.Hunk;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineKind;
import com.prlens.model.LineVerdict;
import com.prlens.model.ModelPricing;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Revision;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.support.NonRetryableApiException;
import com.prlens.support.RetriesExhaustedException;
import com.prlens.support.RetryAfterTooLongException;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ReviewEngineTest {

  private static final LlmUsage USAGE = new LlmUsage(1_000_000, 0, 0, 0);

  /** 준비한 응답을 순서대로 돌려주고 받은 요청을 기록한다. */
  private static final class FakeLlm implements LlmClient {
    final Queue<Supplier<LlmResponse>> script = new ArrayDeque<>();
    final List<LlmRequest> requests = new ArrayList<>();

    FakeLlm respond(String json) {
      return respond(new LlmResponse("end_turn", json, USAGE));
    }

    FakeLlm respond(LlmResponse response) {
      script.add(() -> response);
      return this;
    }

    FakeLlm fail(RuntimeException e) {
      script.add(
          () -> {
            throw e;
          });
      return this;
    }

    @Override
    public LlmResponse send(LlmRequest request) {
      requests.add(request);
      return script.remove().get();
    }
  }

  private static final class RecordingListener implements ReviewListener {
    final List<String> events = new ArrayList<>();
    int exceededRemaining = -1;

    @Override
    public void modeDecided(ReviewStats stats) {
      events.add("mode:" + stats.mode() + ":" + stats.chunkCount());
    }

    @Override
    public void chunkStarted(int index, int total) {
      events.add("chunk:" + index + "/" + total);
    }

    @Override
    public void costThresholdExceeded(BigDecimal cumulative, BigDecimal limit, int remaining) {
      events.add("exceeded");
      exceededRemaining = remaining;
    }
  }

  private static String ok(String summary) {
    return "{\"summary\":\"" + summary + "\",\"findings\":[]}";
  }

  private static String withFinding(String file, Integer line, String severity) {
    return "{\"summary\":\"S\",\"findings\":[{\"file\":\""
        + file
        + "\",\"line\":"
        + line
        + ",\"severity\":\""
        + severity
        + "\",\"category\":\"correctness\",\"message\":\"m\",\"suggestion\":null,"
        + "\"basis\":{\"type\":\"general\",\"ref\":null}}]}";
  }

  private static Configuration config(int sizeLimit) {
    Configuration d = Configuration.defaults();
    return new Configuration(
        d.additionalExcludes(),
        d.removedExcludes(),
        sizeLimit,
        d.model(),
        d.effort(),
        d.maxOutputTokens(),
        d.maxRetries(),
        d.maxCostUsdPerReview(),
        d.pricing());
  }

  private static Configuration configWithoutPricing(String model) {
    Configuration d = config(400);
    SortedMap<String, ModelPricing> none = new TreeMap<>();
    return new Configuration(
        d.additionalExcludes(),
        d.removedExcludes(),
        400,
        model,
        d.effort(),
        d.maxOutputTokens(),
        d.maxRetries(),
        d.maxCostUsdPerReview(),
        none);
  }

  /** head 줄 1..additions를 추가하는 파일. additions가 0이면 삭제만 있는 Hunk다. */
  private static ChangedFile file(String path, int additions, int deletions) {
    List<DiffLine> lines = new ArrayList<>();
    for (int i = 0; i < additions; i++) {
      lines.add(new DiffLine(LineKind.ADDED, "line" + i, false));
    }
    for (int i = 0; i < deletions; i++) {
      lines.add(new DiffLine(LineKind.REMOVED, "old" + i, false));
    }
    Hunk hunk = new Hunk(1, deletions, additions == 0 ? 0 : 1, additions, "", lines);
    return new ChangedFile(
        path,
        null,
        deletions > 0 && additions == 0 ? FileStatus.REMOVED : FileStatus.MODIFIED,
        additions,
        deletions,
        new PatchContent.Parsed(List.of(hunk)));
  }

  private static PullRequestSnapshot snapshot(int reported, ChangedFile... files) {
    return new PullRequestSnapshot(
        new RepoRef("o", "r"),
        1,
        "title",
        "body",
        "b".repeat(40),
        "h".repeat(40),
        reported,
        List.of(files));
  }

  private static PullRequestSnapshot snapshot(ChangedFile... files) {
    return snapshot(files.length, files);
  }

  private static PullRequestSnapshot threeFiles() {
    return snapshot(file("a.java", 8, 0), file("b.java", 8, 0), file("c.java", 8, 0));
  }

  private static final ReviewContext CONTEXT =
      new ReviewContext(
          List.of(new ContextFile("CLAUDE.md", "규칙", ContextSource.CLAUDE_MD, Revision.BASE)));

  private static ReviewResult run(
      FakeLlm llm, RecordingListener listener, PullRequestSnapshot s, Configuration c) {
    return new ReviewEngine(llm, listener).review(s, CONTEXT, c);
  }

  // 요구사항 13.1, 9.1: SINGLE은 호출 1회, 대상은 입력 순서 그대로 한 요청에 담는다
  @Test
  void singleModeCallsLlmOnceWithAllTargetsInOriginalOrder() {
    FakeLlm llm = new FakeLlm().respond(ok("done"));

    ReviewResult result =
        run(
            llm,
            new RecordingListener(),
            snapshot(file("z/B.java", 2, 0), file("a/A.java", 2, 0)),
            config(400));

    assertThat(llm.requests).hasSize(1);
    String data = llm.requests.get(0).dataUserBlock();
    assertThat(data.indexOf("z/B.java")).isLessThan(data.indexOf("a/A.java"));
    assertThat(result.status()).isEqualTo(ResultStatus.COMPLETE);
    assertThat(result.summary()).isEqualTo("done");
    assertThat(result.stats().mode()).isEqualTo(ReviewMode.SINGLE);
  }

  // 요구사항 3.7
  @Test
  void excludedFilesAreNeverSentAndListedInResult() {
    FakeLlm llm = new FakeLlm().respond(ok("s"));

    ReviewResult result =
        run(
            llm,
            new RecordingListener(),
            snapshot(file("package-lock.json", 3, 0), file("src/A.java", 2, 0)),
            config(400));

    assertThat(llm.requests.get(0).dataUserBlock())
        .doesNotContain("package-lock.json")
        .contains("src/A.java");
    assertThat(result.excludedFiles()).containsExactly("package-lock.json");
  }

  // 요구사항 13.5
  @Test
  void splitModeMergesChunksInOrder() {
    FakeLlm llm = new FakeLlm().respond(ok("one")).respond(ok("two")).respond(ok("three"));

    ReviewResult result =
        run(
            llm,
            new RecordingListener(),
            snapshot(file("c.java", 8, 0), file("a.java", 8, 0), file("b.java", 8, 0)),
            config(10));

    assertThat(llm.requests).hasSize(3);
    assertThat(result.stats().mode()).isEqualTo(ReviewMode.SPLIT);
    assertThat(result.stats().chunkCount()).isEqualTo(3);
    assertThat(result.summary()).startsWith("[1/3] a.java\none").contains("[3/3] c.java\nthree");
    assertThat(result.usage().inputTokens()).isEqualTo(3_000_000);
  }

  // 요구사항 13.12
  @Test
  void listenerReceivesModeThenChunkStartedNOfN() {
    FakeLlm llm = new FakeLlm().respond(ok("1")).respond(ok("2")).respond(ok("3"));
    RecordingListener listener = new RecordingListener();

    run(llm, listener, threeFiles(), config(10));

    assertThat(listener.events)
        .containsSubsequence("mode:SPLIT:3", "chunk:1/3", "chunk:2/3", "chunk:3/3");
  }

  // 요구사항 13.6
  @Test
  void splitModeKeepsOtherChunksWhenOneIsIncomplete() {
    FakeLlm llm =
        new FakeLlm()
            .respond(withFinding("a.java", 1, "major"))
            .respond(new LlmResponse("refusal", "", USAGE))
            .respond(ok("three"));

    ReviewResult result = run(llm, new RecordingListener(), threeFiles(), config(10));

    assertThat(result.status()).isEqualTo(ResultStatus.INCOMPLETE);
    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.REFUSAL);
    assertThat(result.findings()).hasSize(1);
    assertThat(result.incompleteDetails().chunks()).hasSize(1);
    assertThat(result.incompleteDetails().chunks().get(0).files()).containsExactly("b.java");
  }

  // 요구사항 13.7, 주의(11.5~11.9 리뷰): lastStatus가 숫자일 때만 Integer로 넘긴다
  @Test
  void splitModeAbsorbsRetriesExhaustedAsChunkFailed() {
    FakeLlm llm =
        new FakeLlm()
            .respond(ok("one"))
            .fail(new RetriesExhaustedException("Claude", "503", 4))
            .respond(ok("three"));

    ReviewResult result = run(llm, new RecordingListener(), threeFiles(), config(10));

    assertThat(result.status()).isEqualTo(ResultStatus.INCOMPLETE);
    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.CHUNK_FAILED);
    assertThat(result.incompleteDetails().chunks().get(0).lastStatusCode()).isEqualTo(503);
    assertThat(result.summary()).contains("one").contains("three");
  }

  @Test
  void chunkFailedWithNetworkErrorKindHasNullStatus() {
    FakeLlm llm =
        new FakeLlm()
            .fail(new RetriesExhaustedException("Claude", "timeout", 4))
            .respond(ok("two"))
            .respond(ok("three"));

    ReviewResult result = run(llm, new RecordingListener(), threeFiles(), config(10));

    assertThat(result.incompleteDetails().chunks().get(0).lastStatusCode()).isNull();
  }

  // 요구사항 13.8, 주의(18.5 리뷰): 실패한 Chunk 수가 메시지에 있다
  @Test
  void allChunksFailedThrowsWithFailedCountInMessage() {
    FakeLlm llm =
        new FakeLlm()
            .fail(new RetriesExhaustedException("Claude", "503", 4))
            .fail(new RetriesExhaustedException("Claude", "503", 4))
            .fail(new RetriesExhaustedException("Claude", "500", 4));

    assertThatThrownBy(() -> run(llm, new RecordingListener(), threeFiles(), config(10)))
        .isInstanceOfSatisfying(
            AllChunksFailedException.class, e -> assertThat(e.failedChunks()).isEqualTo(3))
        .hasMessage(
            "분할 리뷰의 Chunk 3개가 모두 실패했습니다: "
                + new RetriesExhaustedException("Claude", "500", 4).getMessage())
        .hasCauseInstanceOf(RetriesExhaustedException.class);
  }

  // 요구사항 21.5, 21.8
  @Test
  void retryAfterTooLongAndNonRetryablePropagateImmediately() {
    FakeLlm tooLong = new FakeLlm().fail(new RetryAfterTooLongException("Claude", 120));

    assertThatThrownBy(() -> run(tooLong, new RecordingListener(), threeFiles(), config(10)))
        .isInstanceOf(RetryAfterTooLongException.class);
    assertThat(tooLong.requests).hasSize(1);

    FakeLlm fatal = new FakeLlm().fail(new NonRetryableApiException("Claude", 400));
    assertThatThrownBy(() -> run(fatal, new RecordingListener(), threeFiles(), config(10)))
        .isInstanceOf(NonRetryableApiException.class);
    assertThat(fatal.requests).hasSize(1);
  }

  @Test
  void singleModeRetriesExhaustedPropagates() {
    FakeLlm llm = new FakeLlm().fail(new RetriesExhaustedException("Claude", "503", 4));

    assertThatThrownBy(
            () -> run(llm, new RecordingListener(), snapshot(file("a.java", 2, 0)), config(400)))
        .isInstanceOf(RetriesExhaustedException.class);
  }

  // 요구사항 13.9
  @Test
  void summaryOnlyCallsOnceAndForcesEmptyFindings() {
    FakeLlm llm = new FakeLlm().respond(withFinding("a.java", 1, "blocker"));

    ReviewResult result =
        run(
            llm,
            new RecordingListener(),
            snapshot(file("a.java", 20, 0), file("b.java", 20, 0)),
            config(10));

    assertThat(llm.requests).hasSize(1);
    assertThat(result.stats().mode()).isEqualTo(ReviewMode.SUMMARY_ONLY);
    assertThat(result.findings()).isEmpty();
  }

  // 요구사항 13.11
  @Test
  void noTargetSkipsApiAndReturnsCompleteEmptyResult() {
    FakeLlm llm = new FakeLlm();

    ReviewResult result =
        run(llm, new RecordingListener(), snapshot(file("package-lock.json", 3, 0)), config(400));

    assertThat(llm.requests).isEmpty();
    assertThat(result.status()).isEqualTo(ResultStatus.COMPLETE);
    assertThat(result.findings()).isEmpty();
    assertThat(result.excludedFiles()).containsExactly("package-lock.json");
    assertThat(result.summary()).isEqualTo("리뷰 대상 파일이 없습니다.");
    assertThat(result.stats().mode()).isEqualTo(ReviewMode.NO_TARGET);
    assertThat(result.usage().inputTokens()).isZero();
    // 결정(12.1): null이면 CLI가 "단가 없음" 경고를 잘못 낸다
    assertThat(result.usage().estimatedCostUsd()).isNotNull();
    assertThat(result.usage().estimatedCostUsd()).isEqualByComparingTo("0");
    assertThat(result.usage().model()).isEqualTo(config(400).model());
  }

  // 요구사항 1.14, 13.11
  @Test
  void noTargetWithTruncationIsIncompleteWithFilesTruncated() {
    ReviewResult result =
        run(
            new FakeLlm(),
            new RecordingListener(),
            snapshot(5, file("package-lock.json", 3, 0)),
            config(400));

    assertThat(result.status()).isEqualTo(ResultStatus.INCOMPLETE);
    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.FILES_TRUNCATED);
    assertThat(result.incompleteDetails().fileCountGap().reported()).isEqualTo(5);
    assertThat(result.incompleteDetails().fileCountGap().received()).isEqualTo(1);
    assertThat(result.incompleteDetails().chunks()).isEmpty();
  }

  // 요구사항 13.12: NO_TARGET에서도 modeDecided
  @Test
  void modeDecidedFiresForNoTarget() {
    RecordingListener listener = new RecordingListener();

    run(new FakeLlm(), listener, snapshot(file("package-lock.json", 3, 0)), config(400));

    assertThat(listener.events).containsExactly("mode:NO_TARGET:0");
  }

  // 요구사항 1.14
  @Test
  void truncatedFileListIsIncompleteWithFileCountGap() {
    FakeLlm llm = new FakeLlm().respond(ok("s"));

    ReviewResult result =
        run(llm, new RecordingListener(), snapshot(4, file("a.java", 2, 0)), config(400));

    assertThat(result.status()).isEqualTo(ResultStatus.INCOMPLETE);
    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.FILES_TRUNCATED);
    assertThat(result.incompleteDetails().fileCountGap()).isNotNull();
  }

  // 주의(11.5~11.9 리뷰): files_truncated는 병합이 더하므로 Chunk 이슈로 기록되지 않는다
  @Test
  void truncationIsNotRecordedAsChunkIssue() {
    FakeLlm llm = new FakeLlm().respond(ok("s"));

    ReviewResult result =
        run(llm, new RecordingListener(), snapshot(9, file("a.java", 2, 0)), config(400));

    assertThat(result.incompleteDetails().chunks()).isEmpty();
  }

  @Test
  void noGapWhenReportedCountIsNotLarger() {
    FakeLlm llm = new FakeLlm().respond(ok("s"));

    ReviewResult result =
        run(llm, new RecordingListener(), snapshot(1, file("a.java", 2, 0)), config(400));

    assertThat(result.status()).isEqualTo(ResultStatus.COMPLETE);
    assertThat(result.incompleteDetails().fileCountGap()).isNull();
  }

  // 요구사항 12.3, 12.6, 주의(10 리뷰): 삭제된 파일도 대상 맵에 빈 구간으로 들어간다
  @Test
  void deletedFileGetsEmptyRangesSoFindingIsOutOfRange() {
    FakeLlm llm = new FakeLlm().respond(withFinding("gone.java", 1, "minor"));

    ReviewResult result =
        run(
            llm,
            new RecordingListener(),
            snapshot(file("gone.java", 0, 3), file("a.java", 2, 0)),
            config(400));

    assertThat(result.findings()).hasSize(1);
    assertThat(result.findings().get(0).verdict()).isEqualTo(LineVerdict.SUMMARY_ONLY);
    assertThat(result.findings().get(0).summaryOnlyReason())
        .isEqualTo(SummaryOnlyReason.OUT_OF_RANGE);
  }

  // 요구사항 12.3: 분할 모드에서도 대상 맵은 전체 파일 기준이다
  @Test
  void splitModeValidatesAgainstTargetsOfAllChunks() {
    FakeLlm llm =
        new FakeLlm().respond(withFinding("b.java", 1, "minor")).respond(ok("2")).respond(ok("3"));

    ReviewResult result = run(llm, new RecordingListener(), threeFiles(), config(10));

    assertThat(result.findings().get(0).verdict()).isEqualTo(LineVerdict.INLINE_ELIGIBLE);
  }

  // 요구사항 20.1, 20.2: 기본 단가의 입력은 4.00 USD/MTok
  @Test
  void usageHasModelAndCostFromPricing() {
    FakeLlm llm = new FakeLlm().respond(ok("s"));

    ReviewResult result =
        run(llm, new RecordingListener(), snapshot(file("a.java", 2, 0)), config(400));

    assertThat(result.usage().model()).isEqualTo("claude-opus-5-5");
    assertThat(result.usage().estimatedCostUsd()).isEqualByComparingTo("4.0000");
  }

  // 요구사항 20.5
  @Test
  void missingPricingGivesNullCost() {
    FakeLlm llm = new FakeLlm().respond(ok("s"));

    ReviewResult result =
        run(
            llm,
            new RecordingListener(),
            snapshot(file("a.java", 2, 0)),
            configWithoutPricing("unknown-model"));

    assertThat(result.usage().inputTokens()).isEqualTo(1_000_000);
    assertThat(result.usage().estimatedCostUsd()).isNull();
  }

  // 요구사항 20.4: Chunk마다 4.00 USD라 상한 0.50을 첫 Chunk 직후 한 번만 알린다
  @Test
  void costThresholdExceededFiresOnceInSplitMode() {
    FakeLlm llm = new FakeLlm().respond(ok("1")).respond(ok("2")).respond(ok("3"));
    RecordingListener listener = new RecordingListener();

    run(llm, listener, threeFiles(), config(10));

    assertThat(listener.events.stream().filter("exceeded"::equals)).hasSize(1);
    assertThat(listener.exceededRemaining).isEqualTo(2);
  }
}
