package com.prlens.llm;

import com.prlens.support.RetryExecutor;

/** {@link LlmClient}에 재시도를 입히는 데코레이터. 본문은 작업 13.2에서 구현한다. */
public final class RetryingLlmClient implements LlmClient {

  private final LlmClient delegate;
  private final RetryExecutor retry;

  public RetryingLlmClient(LlmClient delegate, RetryExecutor retry) {
    this.delegate = delegate;
    this.retry = retry;
  }

  @Override
  public LlmResponse send(LlmRequest request) {
    throw new UnsupportedOperationException("작업 13.2에서 구현한다");
  }
}
