package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Revision;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

// 작업 12.2: Property 27, 11, 34, 41, 44
// 입력 생성기는 Hunk 없는 별도 생성기가 아니라 줄 수만 맞춘 단순 Hunk를 쓴다. PullRequestSnapshotArbitraries.snapshots()는
// Hunk·경로·reportedChangedFiles가 서로 맞지 않아 쓰지 않는다 (12.2 주의).
class ReviewEnginePropertyTest {

  private static final String OK = "{\"summary\":\"s\",\"findings\":[]}";

  /** 호출마다 준비한 입력 토큰 수로 응답하고 받은 요청을 기록한다. */
  private static final class FakeLlm implements LlmClient {
    final List<LlmRequest> requests = new ArrayList<>();
    private final List<Long> inputTokens;

    FakeLlm(List<Long> inputTokens) {
      this.inputTokens = inputTokens;
    }

    @Override
    public LlmResponse send(LlmRequest request) {
      long tokens = inputTokens.get(Math.min(requests.size(), inputTokens.size() - 1));
      requests.add(request);
      return new LlmResponse("end_turn", OK, new LlmUsage(tokens, 0, 0, 0));
    }
  }

  private static final class Events implements ReviewListener {
    final List<Integer> exceededAtRemaining = new ArrayList<>();
    ReviewStats stats;

    @Override
    public void modeDecided(ReviewStats stats) {
      this.stats = stats;
    }

    @Override
    public void costThresholdExceeded(BigDecimal cumulative, BigDecimal limit, int remaining) {
      exceededAtRemaining.add(remaining);
    }
  }

  private static final ReviewContext CONTEXT =
      new ReviewContext(
          List.of(new ContextFile("CLAUDE.md", "규칙", ContextSource.CLAUDE_MD, Revision.BASE)));

  private static Configuration config(int sizeLimit, BigDecimal maxCost) {
    Configuration d = Configuration.defaults();
    return new Configuration(
        d.additionalExcludes(),
        d.removedExcludes(),
        sizeLimit,
        d.model(),
        d.effort(),
        d.maxOutputTokens(),
        d.maxRetries(),
        maxCost,
        d.pricing());
  }

  private static ChangedFile file(String path, int additions, String marker) {
    List<DiffLine> lines = new ArrayList<>();
    for (int i = 0; i < additions; i++) {
      lines.add(new DiffLine(LineKind.ADDED, marker + i, false));
    }
    Hunk hunk = new Hunk(0, 0, 1, additions, "", lines);
    return new ChangedFile(
        path, null, FileStatus.ADDED, additions, 0, new PatchContent.Parsed(List.of(hunk)));
  }

  private static PullRequestSnapshot snapshot(
      String title, String body, int reported, List<ChangedFile> files) {
    return new PullRequestSnapshot(
        new RepoRef("o", "r"), 1, title, body, "b".repeat(40), "h".repeat(40), reported, files);
  }

  private static List<ChangedFile> files(List<Integer> sizes, String marker) {
    List<ChangedFile> files = new ArrayList<>();
    for (int i = 0; i < sizes.size(); i++) {
      files.add(file("src/f" + i + ".java", sizes.get(i), marker));
    }
    return files;
  }

  private static int count(String text, String part) {
    int n = 0;
    for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
      n++;
    }
    return n;
  }

  @Provide
  Arbitrary<List<Integer>> fileSizes() {
    return Arbitraries.integers().between(1, 40).list().ofMinSize(1).ofMaxSize(8);
  }

  @Provide
  Arbitrary<Integer> sizeLimits() {
    return Arbitraries.integers().between(1, 60);
  }

  // Feature: pr-lens-p1-cli, Property 27: 리뷰 모드와 API 호출 수
  @Property(tries = 200)
  void modeAndCallCountFollowChangedLineCount(
      @ForAll("fileSizes") List<Integer> sizes, @ForAll("sizeLimits") int limit) {
    FakeLlm llm = new FakeLlm(List.of(1000L));
    Events events = new Events();
    PullRequestSnapshot snapshot = snapshot("t", "b", sizes.size(), files(sizes, "x"));
    int count = sizes.stream().mapToInt(Integer::intValue).sum();

    ReviewResult result =
        new ReviewEngine(llm, events).review(snapshot, CONTEXT, config(limit, new BigDecimal("1")));

    ReviewMode expected =
        count <= limit
            ? ReviewMode.SINGLE
            : count <= 3L * limit ? ReviewMode.SPLIT : ReviewMode.SUMMARY_ONLY;
    assertThat(events.stats.mode()).isEqualTo(expected);
    assertThat(result.stats().mode()).isEqualTo(expected);
    int expectedCalls =
        expected == ReviewMode.SPLIT ? ChunkPlanner.plan(snapshot.files(), limit).size() : 1;
    assertThat(llm.requests).hasSize(expectedCalls);
    if (expected == ReviewMode.SUMMARY_ONLY) {
      assertThat(result.findings()).isEmpty();
    }
  }

  // Feature: pr-lens-p1-cli, Property 11: 제외 파일 내용 미전송
  @Property(tries = 100)
  void excludedFileContentIsNeverSent(
      @ForAll("fileSizes") List<Integer> sizes, @ForAll("sizeLimits") int limit) {
    String secret = "SECRET-" + UUID.randomUUID();
    List<ChangedFile> all = new ArrayList<>(files(sizes, "x"));
    all.add(file("package-lock.json", 3, secret));
    all.add(file("config/secrets/key.txt", 2, secret));
    FakeLlm llm = new FakeLlm(List.of(1000L));

    ReviewResult result =
        new ReviewEngine(llm, new Events())
            .review(
                snapshot("t", "b", all.size(), all), CONTEXT, config(limit, new BigDecimal("1")));

    for (LlmRequest request : llm.requests) {
      assertThat(request.system()).doesNotContain(secret).doesNotContain("package-lock.json");
      assertThat(request.cachedUserBlock()).doesNotContain(secret);
      assertThat(request.dataUserBlock())
          .doesNotContain(secret)
          .doesNotContain("package-lock.json")
          .doesNotContain("secrets/key.txt");
    }
    assertThat(result.excludedFiles())
        .containsExactly("package-lock.json", "config/secrets/key.txt");
  }

  // Feature: pr-lens-p1-cli, Property 34: 프롬프트 배치와 캐시 앞부분 동일성
  // 표식은 접두어와 UUID라 system 프롬프트의 문구와 겹칠 수 없다. 태그 수는 요청 전체가 아니라 블록별로 센다
  @Property(tries = 100)
  void markersStayAfterCachePointAndPrefixIsIdenticalAcrossChunks(
      @ForAll("fileSizes") List<Integer> sizes, @ForAll("sizeLimits") int limit) {
    String id = UUID.randomUUID().toString();
    String title = "TITLE-" + id;
    String body = "BODY-" + id;
    String diffMarker = "DIFF-" + id + "-";
    FakeLlm llm = new FakeLlm(List.of(1000L));
    Events events = new Events();
    new ReviewEngine(llm, events)
        .review(
            snapshot(title, body, sizes.size(), files(sizes, diffMarker)),
            CONTEXT,
            config(limit, new BigDecimal("1")));

    assertThat(llm.requests).isNotEmpty();
    LlmRequest first = llm.requests.get(0);
    for (LlmRequest request : llm.requests) {
      assertThat(request.system()).doesNotContain(id);
      assertThat(request.cachedUserBlock()).doesNotContain(id);
      assertThat(request.dataUserBlock()).contains(title).contains(body);
      if (events.stats.mode() != ReviewMode.SUMMARY_ONLY) {
        assertThat(request.dataUserBlock()).contains(diffMarker);
      }
      assertThat(count(request.cachedUserBlock(), "<prlens:team_context>")).isEqualTo(1);
      assertThat(count(request.cachedUserBlock(), "</prlens:team_context>")).isEqualTo(1);
      assertThat(count(request.dataUserBlock(), "<prlens:pr_title>")).isEqualTo(1);
      assertThat(count(request.dataUserBlock(), "</prlens:pr_title>")).isEqualTo(1);
      assertThat(request.system()).isEqualTo(first.system());
      assertThat(request.cachedUserBlock()).isEqualTo(first.cachedUserBlock());
    }
  }

  // Feature: pr-lens-p1-cli, Property 41: 누적 비용 경고 1회
  // 토큰이 1,000의 배수이고 입력 단가가 4.00 USD/MTok이라 Chunk 비용은 정확히 토큰 × 0.000004이다
  @Property(tries = 200)
  void cumulativeCostWarningFiresOnceAtFirstCrossing(
      @ForAll("chunkTokens") List<Long> tokens, @ForAll("limits") BigDecimal limit) {
    // 10줄 파일 3개(30줄)는 L=10에서 분할 모드이고 파일마다 Chunk 하나가 된다
    int n = tokens.size();
    List<ChangedFile> files = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      files.add(file("src/f" + i + ".java", 10, "x"));
    }
    FakeLlm llm = new FakeLlm(tokens);
    Events events = new Events();

    ReviewResult result =
        new ReviewEngine(llm, events)
            .review(snapshot("t", "b", n, files), CONTEXT, config(10, limit));

    assertThat(events.stats.mode()).isEqualTo(ReviewMode.SPLIT);
    assertThat(llm.requests).hasSize(n);
    BigDecimal cumulative = BigDecimal.ZERO;
    List<Integer> expected = new ArrayList<>();
    for (int i = 0; i < n; i++) {
      cumulative =
          cumulative.add(BigDecimal.valueOf(tokens.get(i)).multiply(new BigDecimal("0.000004")));
      if (expected.isEmpty() && cumulative.compareTo(limit) > 0) {
        expected.add(n - (i + 1));
      }
    }
    assertThat(events.exceededAtRemaining).isEqualTo(expected);
    assertThat(result.status()).isEqualTo(ResultStatus.COMPLETE);
  }

  @Provide
  Arbitrary<List<Long>> chunkTokens() {
    return Arbitraries.longs().between(0, 500).map(t -> t * 1000).list().ofSize(3);
  }

  @Provide
  Arbitrary<BigDecimal> limits() {
    return Arbitraries.integers().between(0, 4000).map(v -> BigDecimal.valueOf(v, 4));
  }

  // Feature: pr-lens-p1-cli, Property 44: 파일 수 부족 판정
  @Property(tries = 200)
  void filesTruncatedExactlyWhenReportedCountExceedsReceived(
      @ForAll("fileSizes") List<Integer> sizes,
      @ForAll boolean withExcludedOnly,
      @ForAll("deltas") int delta) {
    List<ChangedFile> files =
        withExcludedOnly ? List.of(file("package-lock.json", 2, "x")) : files(sizes, "x");
    int reported = Math.max(0, files.size() + delta);
    FakeLlm llm = new FakeLlm(List.of(1000L));

    ReviewResult result =
        new ReviewEngine(llm, new Events())
            .review(snapshot("t", "b", reported, files), CONTEXT, config(400, new BigDecimal("1")));

    boolean truncated = reported > files.size();
    assertThat(result.incompleteReasons().contains(IncompleteReason.FILES_TRUNCATED))
        .isEqualTo(truncated);
    assertThat(result.status())
        .isEqualTo(truncated ? ResultStatus.INCOMPLETE : ResultStatus.COMPLETE);
    if (truncated) {
      assertThat(result.incompleteDetails().fileCountGap().reported()).isEqualTo(reported);
      assertThat(result.incompleteDetails().fileCountGap().received()).isEqualTo(files.size());
    } else {
      assertThat(result.incompleteDetails().fileCountGap()).isNull();
    }
  }

  @Provide
  Arbitrary<Integer> deltas() {
    // 같음, 1 작음, 1 큼을 자주 섞고 큰 차이도 만든다
    return Arbitraries.frequencyOf(
        Tuple.of(3, Arbitraries.just(0)),
        Tuple.of(3, Arbitraries.just(-1)),
        Tuple.of(3, Arbitraries.just(1)),
        Tuple.of(1, Arbitraries.integers().between(2, 3000)));
  }
}
