package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ChunkIssue;
import com.prlens.model.Configuration;
import com.prlens.model.FileCountGap;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Usage;
import com.prlens.support.MaskingPrintStream;
import com.prlens.support.SecretMasker;
import com.prlens.support.Warning;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class StderrReporterTest {

  private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
  private final PrintStream err = new PrintStream(bytes, false, StandardCharsets.UTF_8);
  private final StderrReporter reporter = new StderrReporter(err);

  private String written() {
    return bytes.toString(StandardCharsets.UTF_8);
  }

  private static Configuration config(String maxCost) {
    return new Configuration(
        List.of(),
        List.of(),
        1000,
        "claude-opus-5-5",
        "medium",
        16000,
        3,
        new BigDecimal(maxCost),
        new TreeMap<>());
  }

  private static ReviewResult result(
      List<IncompleteReason> reasons, IncompleteDetails details, BigDecimal cost) {
    return new ReviewResult(
        reasons.isEmpty() ? ResultStatus.COMPLETE : ResultStatus.INCOMPLETE,
        "요약",
        List.of(),
        List.of(),
        List.of(),
        new Usage(10, 20, 0, 0, "claude-opus-5-5", cost),
        reasons,
        details,
        new ReviewStats(ReviewMode.SINGLE, 120, 1000, 1));
  }

  private static ReviewResult complete(BigDecimal cost) {
    return result(List.of(), new IncompleteDetails(List.of(), null), cost);
  }

  // 요구사항 3.10
  @Test
  void warningSinkWritesOneLine() {
    reporter.warn(new Warning("spec_not_found", "스펙 파일을 찾지 못했습니다: docs/a.md"));

    assertThat(written()).isEqualTo("경고[spec_not_found]: 스펙 파일을 찾지 못했습니다: docs/a.md\n");
  }

  @Test
  void multilineMessageStaysOneLine() {
    reporter.warn(new Warning("c", "첫 줄\r\n둘째 줄\n셋째 줄"));

    assertThat(written()).isEqualTo("경고[c]: 첫 줄 둘째 줄 셋째 줄\n");
  }

  // 요구사항 13.12
  @Test
  void modeDecidedPrintsModeLinesAndChunkCount() {
    reporter.modeDecided(new ReviewStats(ReviewMode.SPLIT, 2500, 1000, 3));

    assertThat(written()).isEqualTo("리뷰 모드: split, 변경 줄 수: 2500, 크기 상한: 1000, Chunk 수: 3\n");
  }

  @Test
  void chunkStartedPrintsNOfN() {
    reporter.chunkStarted(2, 3);

    assertThat(written()).isEqualTo("Chunk 2/3 리뷰 중\n");
  }

  // 요구사항 1.14, 16.6, 15.8
  @Test
  void fileCountGapWarnsWithBothCounts() {
    reporter.reportResult(
        result(
            List.of(IncompleteReason.FILES_TRUNCATED),
            new IncompleteDetails(List.of(), new FileCountGap(3500, 3000)),
            new BigDecimal("0.1")),
        config("0.5"));

    assertThat(written())
        .isEqualTo(
            "경고[files_truncated]: 변경 파일 3500개 중 3000개만 받았습니다\n"
                + "경고[incomplete]: 리뷰 결과가 불완전합니다. 사유: files_truncated\n");
  }

  @Test
  void incompleteResultWarnsWithReasons() {
    ChunkIssue issue =
        new ChunkIssue(2, List.of("a.java", "b.java"), IncompleteReason.CHUNK_FAILED, 529, null);
    reporter.reportResult(
        result(
            List.of(IncompleteReason.CHUNK_FAILED),
            new IncompleteDetails(List.of(issue), null),
            new BigDecimal("0.1")),
        config("0.5"));

    assertThat(written())
        .isEqualTo(
            "경고[incomplete]: 리뷰 결과가 불완전합니다. 사유: chunk_failed\n"
                + "경고[incomplete]: Chunk 2 (a.java, b.java) chunk_failed, 마지막 상태 529\n");
  }

  @Test
  void incompleteChunkWithoutStatusCodeOmitsStatus() {
    ChunkIssue issue =
        new ChunkIssue(1, List.of("a.java"), IncompleteReason.MAX_TOKENS, null, null);
    reporter.reportResult(
        result(
            List.of(IncompleteReason.MAX_TOKENS),
            new IncompleteDetails(List.of(issue), null),
            new BigDecimal("0.1")),
        config("0.5"));

    assertThat(written())
        .isEqualTo(
            "경고[incomplete]: 리뷰 결과가 불완전합니다. 사유: max_tokens\n"
                + "경고[incomplete]: Chunk 1 (a.java) max_tokens\n");
  }

  @Test
  void completeResultPrintsNothing() {
    reporter.reportResult(complete(new BigDecimal("0.1")), config("0.5"));

    assertThat(written()).isEmpty();
  }

  // 요구사항 20.3
  @Test
  void costOverLimitWarnsWithCostAndLimit() {
    reporter.reportResult(complete(new BigDecimal("0.6")), config("0.5"));

    assertThat(written())
        .isEqualTo("경고[cost_limit_exceeded]: 추정 비용 0.6000 USD가 1회 상한 0.5000 USD를 넘었습니다\n");
  }

  @Test
  void costEqualToLimitDoesNotWarn() {
    reporter.reportResult(complete(new BigDecimal("0.5")), config("0.5"));

    assertThat(written()).isEmpty();
  }

  // 요구사항 20.4
  @Test
  void cumulativeCostWarnsWithRemainingChunks() {
    reporter.costThresholdExceeded(new BigDecimal("0.55"), new BigDecimal("0.5"), 2);

    assertThat(written())
        .isEqualTo(
            "경고[cumulative_cost_exceeded]: 누적 추정 비용 0.5500 USD가 상한 0.5000 USD를 넘었습니다."
                + " 남은 Chunk 2개를 계속 리뷰합니다\n");
  }

  // 요구사항 20.5
  @Test
  void unknownPricingWarns() {
    reporter.reportResult(complete(null), config("0.5"));

    assertThat(written())
        .isEqualTo("경고[pricing_missing]: 모델 claude-opus-5-5의 단가가 없어 추정 비용을 알 수 없습니다\n");
  }

  // 요구사항 21.7
  @Test
  void retryLineHasApiAttemptStatusWait() {
    reporter.onRetry("GitHub", 2, 4, "503", Duration.ofSeconds(2));

    assertThat(written()).isEqualTo("재시도: GitHub 2/4, 마지막 상태: 503, 대기 2초\n");
  }

  @Test
  void retryWaitZeroAndFractionalNotTruncated() {
    reporter.onRetry("Anthropic", 2, 4, "429", Duration.ZERO);
    reporter.onRetry("Anthropic", 3, 4, "429", Duration.ofMillis(1500));
    reporter.onRetry("Anthropic", 4, 4, "429", Duration.ofMillis(250));

    assertThat(written().lines().toList())
        .containsExactly(
            "재시도: Anthropic 2/4, 마지막 상태: 429, 대기 0초",
            "재시도: Anthropic 3/4, 마지막 상태: 429, 대기 1.5초",
            "재시도: Anthropic 4/4, 마지막 상태: 429, 대기 0.25초");
  }

  @Test
  void negativeWaitClampedToZero() {
    reporter.onRetry("GitHub", 2, 4, "timeout", Duration.ofSeconds(-3));

    assertThat(written()).endsWith("대기 0초\n");
  }

  @Test
  void everyMessageEndsWithNewlineAndFlushes() {
    AtomicInteger flushes = new AtomicInteger();
    ByteArrayOutputStream target =
        new ByteArrayOutputStream() {
          @Override
          public void flush() {
            flushes.incrementAndGet();
          }
        };
    StderrReporter countingReporter =
        new StderrReporter(new PrintStream(target, false, StandardCharsets.UTF_8));

    countingReporter.chunkStarted(1, 2);

    assertThat(target.toString(StandardCharsets.UTF_8)).isEqualTo("Chunk 1/2 리뷰 중\n");
    assertThat(flushes.get()).isPositive();
  }

  @Test
  void maskedStreamPrintsMessageEndingWithSecretInitial() {
    ByteArrayOutputStream target = new ByteArrayOutputStream();
    PrintStream masked =
        new MaskingPrintStream(target, new SecretMasker(List.of("sk-secret-value")));
    StderrReporter maskedReporter = new StderrReporter(masked);

    maskedReporter.warn(new Warning("c", "끝 글자가 비밀값 첫 글자와 같은 s"));
    maskedReporter.warn(new Warning("c", "키 sk-secret-value 포함"));

    String text = target.toString(StandardCharsets.UTF_8);
    assertThat(text).contains("같은 s\n").doesNotContain("sk-secret-value");
  }
}
