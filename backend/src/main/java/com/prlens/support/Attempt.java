package com.prlens.support;

import java.time.Duration;
import java.util.Objects;

/** 재시도 실행기와 호출자({@code HttpGitHubClient}, {@code RetryingLlmClient}) 사이의 계약. 호출자가 응답을 분류해 돌려준다. */
public sealed interface Attempt<T> {
  record Success<T>(T value) implements Attempt<T> {}

  /**
   * @param retryAfter {@code retry-after} 헤더 원문. 없으면 null이고, 유효한지는 실행기가 판단한다
   * @param delay 호출자가 정한 대기 시간(GitHub rate limit). 없으면 null이고 실행기가 계산한다
   */
  record Retryable<T>(String statusOrError, String retryAfter, Duration delay)
      implements Attempt<T> {
    public Retryable {
      Objects.requireNonNull(statusOrError, "statusOrError");
    }
  }

  /** 재시도하지 않는 응답. 실행기가 {@code NonRetryableApiException}으로 던진다. */
  record Fatal<T>(String api, int status) implements Attempt<T> {
    public Fatal {
      Objects.requireNonNull(api, "api");
    }
  }
}
