package com.prlens.support;

import java.time.Duration;

public interface RetryListener {
  /**
   * @param wait 정책이 계산한 대기 시간. {@link Sleeper}가 실제로는 더 짧게 자더라도 이 값을 그대로 전달한다
   */
  void onRetry(
      String api, int nextAttempt, int maxAttempts, String lastStatusOrError, Duration wait);
}
