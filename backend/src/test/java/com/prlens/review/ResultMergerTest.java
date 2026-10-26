package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ChunkIssue;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Severity;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResultMergerTest {

  private static final ReviewStats SPLIT_STATS = new ReviewStats(ReviewMode.SPLIT, 900, 400, 3);
  private static final ReviewStats SINGLE_STATS = new ReviewStats(ReviewMode.SINGLE, 10, 400, 1);

  private static Finding finding(String file, String message) {
    return new Finding(
        file,
        3,
        Severity.MINOR,
        Category.DESIGN,
        message,
        null,
        new Basis(BasisType.GENERAL, null),
        null,
        LineVerdict.INLINE_ELIGIBLE,
        null);
  }

  private static Usage usage(long in, long out, long write, long read, String cost) {
    return new Usage(
        in, out, write, read, "claude-opus-5-5", cost == null ? null : new BigDecimal(cost));
  }

  private static ChunkOutcome ok(
      int index, String file, String summary, Usage usage, Finding... findings) {
    return new ChunkOutcome(
        index, List.of(file), summary, List.of(findings), null, null, usage, null);
  }

  private static ChunkOutcome incomplete(
      int index, String file, IncompleteReason reason, String excerpt, Usage usage) {
    return new ChunkOutcome(index, List.of(file), "", List.of(), reason, excerpt, usage, null);
  }

  private static ReviewResult merge(ReviewStats stats, ChunkOutcome... outcomes) {
    return ResultMerger.merge(List.of(outcomes), List.of(), null, stats);
  }

  // 요구사항 13.5
  @Test
  void concatenatesFindingsInChunkOrderAndPrefixesSummariesWithChunkHeaders() {
    ReviewResult result =
        merge(
            SPLIT_STATS,
            ok(1, "a/A.java", "첫째 요약", usage(1, 1, 0, 0, "0.0100"), finding("a/A.java", "m1")),
            ok(2, "b/B.java", "둘째 요약", usage(1, 1, 0, 0, "0.0100"), finding("b/B.java", "m2")),
            ok(3, "c/C.java", "셋째 요약", usage(1, 1, 0, 0, "0.0100")));

    assertThat(result.findings()).extracting(Finding::message).containsExactly("m1", "m2");
    assertThat(result.summary())
        .isEqualTo("[1/3] a/A.java\n첫째 요약\n\n[2/3] b/B.java\n둘째 요약\n\n[3/3] c/C.java\n셋째 요약");
    assertThat(result.status()).isEqualTo(ResultStatus.COMPLETE);
    assertThat(result.stats()).isEqualTo(SPLIT_STATS);
  }

  @Test
  void joinsMultipleChunkFilesWithCommaInTheHeader() {
    ChunkOutcome multi =
        new ChunkOutcome(
            1,
            List.of("a.java", "b.java"),
            "요약",
            List.of(),
            null,
            null,
            usage(1, 1, 0, 0, "0"),
            null);

    ReviewResult result =
        merge(SPLIT_STATS, multi, ok(2, "c.java", "다른 요약", usage(1, 1, 0, 0, "0")));

    assertThat(result.summary()).startsWith("[1/2] a.java, b.java\n요약\n\n[2/2] c.java");
  }

  // 요구사항 13.5, 20.10 + tasks.md 11.8 주의(20.1 리뷰): Chunk별 반올림한 비용의 합
  @Test
  void sumsTokensAndPerChunkRoundedCosts() {
    ReviewResult result =
        merge(
            SPLIT_STATS,
            ok(1, "a", "s", usage(10_000, 400, 2_000, 5_000, "0.0834")),
            ok(2, "b", "s", usage(10_000, 400, 2_600, 4_800, "0.0482")),
            ok(3, "c", "s", usage(9_400, 350, 2_600, 4_600, "0.0478")));

    Usage usage = result.usage();
    assertThat(usage.inputTokens()).isEqualTo(29_400);
    assertThat(usage.outputTokens()).isEqualTo(1_150);
    assertThat(usage.cacheWriteTokens()).isEqualTo(7_200);
    assertThat(usage.cacheReadTokens()).isEqualTo(14_400);
    assertThat(usage.model()).isEqualTo("claude-opus-5-5");
    assertThat(usage.estimatedCostUsd().toPlainString()).isEqualTo("0.1794");
  }

  // 요구사항 20.5
  @Test
  void totalCostIsNullWhenAnyChunkCostIsNull() {
    ReviewResult result =
        merge(
            SPLIT_STATS,
            ok(1, "a", "s", usage(1, 1, 1, 1, "0.0100")),
            ok(2, "b", "s", usage(1, 1, 1, 1, null)));

    assertThat(result.usage().estimatedCostUsd()).isNull();
    assertThat(result.usage().inputTokens()).isEqualTo(2);
  }

  // 요구사항 13.7: 재시도를 소진한 Chunk는 토큰 0, 비용 0으로 더하고 summary에서는 뺀다
  @Test
  void failedChunkAddsZeroUsageAndIsOmittedFromSummary() {
    ReviewResult result =
        merge(
            SPLIT_STATS,
            ok(1, "a", "요약1", usage(5, 5, 0, 0, "0.0100"), finding("a", "m1")),
            ChunkOutcome.failed(2, List.of("b", "c"), 503),
            ok(3, "d", "요약3", usage(5, 5, 0, 0, "0.0200")));

    assertThat(result.usage().estimatedCostUsd().toPlainString()).isEqualTo("0.0300");
    assertThat(result.usage().inputTokens()).isEqualTo(10);
    assertThat(result.summary()).contains("[1/3] a").contains("[3/3] d").doesNotContain("[2/3]");
    assertThat(result.status()).isEqualTo(ResultStatus.INCOMPLETE);
    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.CHUNK_FAILED);
    assertThat(result.incompleteDetails().chunks())
        .containsExactly(
            new ChunkIssue(2, List.of("b", "c"), IncompleteReason.CHUNK_FAILED, 503, null));
    assertThat(result.findings()).hasSize(1);
  }

  // 요구사항 13.6, 16.5
  @Test
  void incompleteReasonsAreDeduplicatedInFirstAppearanceOrderWithChunkIssues() {
    ReviewResult result =
        merge(
            SPLIT_STATS,
            incomplete(1, "a", IncompleteReason.MAX_TOKENS, null, usage(1, 1, 0, 0, "0")),
            incomplete(2, "b", IncompleteReason.REFUSAL, null, usage(1, 1, 0, 0, "0")),
            incomplete(3, "c", IncompleteReason.MAX_TOKENS, null, usage(1, 1, 0, 0, "0")));

    assertThat(result.status()).isEqualTo(ResultStatus.INCOMPLETE);
    assertThat(result.incompleteReasons())
        .containsExactly(IncompleteReason.MAX_TOKENS, IncompleteReason.REFUSAL);
    assertThat(result.incompleteDetails().chunks())
        .extracting(ChunkIssue::chunkIndex, ChunkIssue::files, ChunkIssue::reason)
        .containsExactly(
            tuple(1, List.of("a"), IncompleteReason.MAX_TOKENS),
            tuple(2, List.of("b"), IncompleteReason.REFUSAL),
            tuple(3, List.of("c"), IncompleteReason.MAX_TOKENS));
  }

  // tasks.md 11.8 주의(10 리뷰)
  @Test
  void rawExcerptIsCopiedOnlyForSchemaViolation() {
    ReviewResult result =
        merge(
            SPLIT_STATS,
            incomplete(1, "a", IncompleteReason.SCHEMA_VIOLATION, "원본 앞부분", usage(1, 1, 0, 0, "0")),
            incomplete(2, "b", IncompleteReason.MAX_TOKENS, "무시돼야 함", usage(1, 1, 0, 0, "0")));

    assertThat(result.incompleteDetails().chunks())
        .extracting(ChunkIssue::rawResponseExcerpt)
        .containsExactly("원본 앞부분", null);
  }

  @Test
  void singleChunkSummaryHasNoHeaderAndChunkIndexIsOne() {
    ReviewResult incomplete =
        merge(
            SINGLE_STATS,
            incomplete(1, "a", IncompleteReason.REFUSAL, null, usage(1, 1, 0, 0, "0")));
    ReviewResult complete = merge(SINGLE_STATS, ok(1, "a", "그대로", usage(1, 1, 0, 0, "0")));

    assertThat(incomplete.incompleteDetails().chunks())
        .extracting(ChunkIssue::chunkIndex)
        .containsExactly(1);
    assertThat(complete.summary()).isEqualTo("그대로");
  }

  // 요구사항 1.14, 16.5: files_truncated는 Chunk 사유 뒤에 둔다
  @Test
  void fileCountGapAddsFilesTruncatedAfterChunkReasons() {
    FileCountGap gap = new FileCountGap(3_500, 3_000);

    ReviewResult result =
        ResultMerger.merge(
            List.of(incomplete(1, "a", IncompleteReason.REFUSAL, null, usage(1, 1, 0, 0, "0"))),
            List.of(),
            gap,
            SINGLE_STATS);

    assertThat(result.incompleteReasons())
        .containsExactly(IncompleteReason.REFUSAL, IncompleteReason.FILES_TRUNCATED);
    assertThat(result.incompleteDetails().fileCountGap()).isEqualTo(gap);
    assertThat(result.incompleteDetails().chunks()).hasSize(1);
  }

  // 요구사항 13.5: excludedFiles 중복 제거
  @Test
  void deduplicatesExcludedFilesByPathKeepingFirst() {
    ReviewResult result =
        ResultMerger.merge(
            List.of(ok(1, "a", "s", usage(1, 1, 0, 0, "0"))),
            List.of(
                new ExcludedFile("gen/x.java", "**/generated/**"),
                new ExcludedFile("bin.dat", "binary_or_too_large"),
                new ExcludedFile("gen/x.java", "다른 사유")),
            null,
            SINGLE_STATS);

    assertThat(result.excludedFiles()).containsExactly("gen/x.java", "bin.dat");
    assertThat(result.excludedFileDetails().get(0).reason()).isEqualTo("**/generated/**");
  }

  // 12.1은 merge 전에 AllChunksFailedException을 던지므로, 입력이 없거나 usage가 하나도 없으면 거부한다
  @Test
  void rejectsEmptyOutcomesAndOutcomesWithoutAnyUsage() {
    assertThatThrownBy(() -> ResultMerger.merge(List.of(), List.of(), null, SINGLE_STATS))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> merge(SPLIT_STATS, ChunkOutcome.failed(1, List.of("a"), 503)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
