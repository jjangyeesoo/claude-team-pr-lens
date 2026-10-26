package com.prlens.support;

import java.time.Duration;
import java.util.function.Supplier;

/** GitHub와 Claude 호출에 공통으로 쓰는 재시도 실행기 (design.md "재시도 정책"). */
public final class RetryExecutor {

  private final RetryPolicy policy;
  private final Sleeper sleeper;
  private final RetryListener listener;

  public RetryExecutor(RetryPolicy policy, Sleeper sleeper, RetryListener listener) {
    this.policy = policy;
    this.sleeper = sleeper;
    this.listener = listener;
  }

  /**
   * 호출자가 돌려준 {@link Attempt}로 재시도 여부를 판단한다. 호출자가 던진 예외는 그대로 통과시킨다.
   *
   * @param api 오류 메시지와 {@link RetryListener}에 쓰는 API 이름. {@link Attempt.Fatal#api()}는 쓰지 않는다
   */
  public <T> T execute(String api, Supplier<Attempt<T>> call) {
    for (int attempt = 1; ; attempt++) {
      Attempt<T> outcome = call.get();
      if (outcome instanceof Attempt.Success<T> success) {
        return success.value();
      }
      if (outcome instanceof Attempt.Fatal<T> fatal) {
        throw new NonRetryableApiException(api, fatal.status(), fatal.reason());
      }
      Attempt.Retryable<T> retryable = (Attempt.Retryable<T>) outcome;
      Duration wait = policy.waitAfter(attempt, retryable);
      // 마지막 시도 여부보다 먼저 검사한다. 순서가 반대면 같은 응답의 결과가 시도 순번에 따라 달라진다
      if (wait.compareTo(RetryPolicy.MAX_WAIT) > 0) {
        throw new RetryAfterTooLongException(api, ceilSeconds(wait));
      }
      if (attempt >= policy.maxAttempts()) {
        throw new RetriesExhaustedException(api, retryable.statusOrError(), attempt);
      }
      listener.onRetry(api, attempt + 1, policy.maxAttempts(), retryable.statusOrError(), wait);
      sleeper.sleep(wait);
    }
  }

  private static long ceilSeconds(Duration wait) {
    return wait.getNano() > 0 ? wait.toSeconds() + 1 : wait.toSeconds();
  }
}
