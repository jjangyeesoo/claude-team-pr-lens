package com.prlens.support;

import java.util.function.Supplier;

/** GitHub와 Claude 호출에 공통으로 쓰는 재시도 실행기. 본문은 작업 3.1에서 구현한다. */
public final class RetryExecutor {

  private final RetryPolicy policy;
  private final Sleeper sleeper;
  private final RetryListener listener;

  public RetryExecutor(RetryPolicy policy, Sleeper sleeper, RetryListener listener) {
    this.policy = policy;
    this.sleeper = sleeper;
    this.listener = listener;
  }

  public <T> T execute(String api, Supplier<Attempt<T>> call) {
    throw new UnsupportedOperationException("작업 3.1에서 구현한다");
  }
}
