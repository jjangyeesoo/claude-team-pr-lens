package com.prlens.cli;

import com.prlens.model.ChunkIssue;
import com.prlens.model.Configuration;
import com.prlens.model.FileCountGap;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Usage;
import com.prlens.review.ReviewListener;
import com.prlens.support.RetryListener;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 진행, 재시도, 경고를 표준 오류에 한 줄씩 쓴다 (요구사항 15.8). 모든 메시지는 줄바꿈 없는 한 줄에 {@code \n}을 붙여 쓰고 바로 flush한다. 줄이 끝나지
 * 않은 채 남기면 끝 글자가 비밀값의 앞부분과 같을 때 {@code MaskingPrintStream}이 다음 쓰기까지 내보내지 않기 때문이다.
 */
public final class StderrReporter implements ReviewListener, RetryListener, WarningSink {

  private final PrintStream err;

  public StderrReporter(PrintStream err) {
    this.err = err;
  }

  @Override
  public void warn(Warning warning) {
    warn(warning.code(), warning.message());
  }

  @Override
  public void modeDecided(ReviewStats stats) {
    line(
        "리뷰 모드: "
            + name(stats.mode())
            + ", 변경 줄 수: "
            + stats.changedLineCount()
            + ", 크기 상한: "
            + stats.sizeLimit()
            + ", Chunk 수: "
            + stats.chunkCount());
  }

  @Override
  public void chunkStarted(int index, int total) {
    // 요구사항 13.12: n/N 표시는 분할 모드만. 엔진은 단일·요약 전용 모드에서도 (1, 1)을 부른다
    if (total <= 1) {
      return;
    }
    line("Chunk " + index + "/" + total + " 리뷰 중");
  }

  @Override
  public void costThresholdExceeded(BigDecimal cumulative, BigDecimal limit, int remainingChunks) {
    warn(
        "cumulative_cost_exceeded",
        "누적 추정 비용 "
            + usd(cumulative)
            + "가 상한 "
            + usd(limit)
            + "를 넘었습니다. 남은 Chunk "
            + remainingChunks
            + "개를 계속 리뷰합니다");
  }

  /** {@code wait}가 0이거나 음수여도, 초 단위로 떨어지지 않아도 그대로 보이게 소수로 쓴다. */
  @Override
  public void onRetry(
      String api, int nextAttempt, int maxAttempts, String lastStatusOrError, Duration wait) {
    line(
        "재시도: "
            + api
            + " "
            + nextAttempt
            + "/"
            + maxAttempts
            + ", 마지막 상태: "
            + lastStatusOrError
            + ", 대기 "
            + seconds(wait)
            + "초");
  }

  /** 결과로 알 수 있는 경고를 낸다: 파일 수 부족, 불완전 사유, 비용 상한 초과, 단가 없음. 종료 코드는 바꾸지 않는다. */
  public void reportResult(ReviewResult result, Configuration config) {
    FileCountGap gap = result.incompleteDetails().fileCountGap();
    if (gap != null) {
      warn("files_truncated", "변경 파일 " + gap.reported() + "개 중 " + gap.received() + "개만 받았습니다");
    }
    if (!result.incompleteReasons().isEmpty()) {
      warn(
          "incomplete",
          "리뷰 결과가 불완전합니다. 사유: "
              + result.incompleteReasons().stream()
                  .map(StderrReporter::name)
                  .collect(Collectors.joining(", ")));
    }
    for (ChunkIssue issue : result.incompleteDetails().chunks()) {
      String status = issue.lastStatusCode() == null ? "" : ", 마지막 상태 " + issue.lastStatusCode();
      warn(
          "incomplete",
          "Chunk "
              + issue.chunkIndex()
              + " ("
              + String.join(", ", issue.files())
              + ") "
              + name(issue.reason())
              + status);
    }
    Usage usage = result.usage();
    if (usage.estimatedCostUsd() == null) {
      warn("pricing_missing", "모델 " + usage.model() + "의 단가가 없어 추정 비용을 알 수 없습니다");
    } else if (usage.estimatedCostUsd().compareTo(config.maxCostUsdPerReview()) > 0) {
      warn(
          "cost_limit_exceeded",
          "추정 비용 "
              + usd(usage.estimatedCostUsd())
              + "가 1회 상한 "
              + usd(config.maxCostUsdPerReview())
              + "를 넘었습니다");
    }
  }

  private void warn(String code, String message) {
    line("경고[" + code + "]: " + message);
  }

  private synchronized void line(String message) {
    err.print(message.replaceAll("\\R", " ") + "\n");
    err.flush();
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  private static String usd(BigDecimal amount) {
    return amount.setScale(4, RoundingMode.HALF_UP).toPlainString() + " USD";
  }

  private static String seconds(Duration wait) {
    long millis = Math.max(0, wait.toMillis());
    return BigDecimal.valueOf(millis).movePointLeft(3).stripTrailingZeros().toPlainString();
  }
}
