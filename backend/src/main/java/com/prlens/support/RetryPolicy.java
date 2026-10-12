package com.prlens.support;

import java.time.Duration;
import java.util.regex.Pattern;

/**
 * 재시도 정책 (요구사항 21).
 *
 * @param maxRetries 첫 시도 뒤에 다시 시도하는 횟수(0~10)
 */
public record RetryPolicy(int maxRetries) {

  /** 이보다 길게 기다려야 하면 재시도하지 않는다. */
  static final Duration MAX_WAIT = Duration.ofSeconds(60);

  private static final long MAX_BACKOFF_SECONDS = 30;
  private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("[0-9]+");

  public RetryPolicy {
    if (maxRetries < 0 || maxRetries > 10) {
      throw new IllegalArgumentException("maxRetries는 0~10이어야 합니다: " + maxRetries);
    }
  }

  public int maxAttempts() {
    return maxRetries + 1;
  }

  /**
   * 실패한 시도 뒤에 기다릴 시간. 호출자가 정한 값, 유효한 {@code retry-after}, 지수 백오프 순서로 고른다. {@link #MAX_WAIT}를 넘는지는
   * 실행기가 검사한다.
   *
   * @param attempt 방금 실패한 시도의 순번(1부터)
   */
  Duration waitAfter(int attempt, Attempt.Retryable<?> outcome) {
    if (outcome.delay() != null) {
      return outcome.delay().isNegative() ? Duration.ZERO : outcome.delay();
    }
    Duration retryAfter = parseRetryAfter(outcome.retryAfter());
    if (retryAfter != null) {
      return retryAfter;
    }
    return Duration.ofSeconds(Math.min(1L << (attempt - 1), MAX_BACKOFF_SECONDS));
  }

  /** 0 이상 정수 초만 유효하다. HTTP 날짜, 음수, 소수는 null을 돌려준다. */
  private static Duration parseRetryAfter(String retryAfter) {
    if (retryAfter == null || !NON_NEGATIVE_INTEGER.matcher(retryAfter).matches()) {
      return null;
    }
    try {
      return Duration.ofSeconds(Long.parseLong(retryAfter));
    } catch (NumberFormatException tooLarge) {
      return Duration.ofSeconds(Long.MAX_VALUE);
    }
  }
}
