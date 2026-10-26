package com.prlens.llm;

import com.prlens.support.Attempt;
import com.prlens.support.RetryExecutor;

/**
 * {@link LlmClient}에 재시도를 입히는 데코레이터 (요구사항 21).
 *
 * <p>429, 5xx, 연결 실패, 제한 시간 초과는 재시도하고 429 외 4xx는 바로 실패시킨다. 상태 코드가 없는 그 밖의 오류(응답 해석 실패 등)는 재시도 없이
 * 그대로 던진다.
 */
public final class RetryingLlmClient implements LlmClient {

  private static final String API = "Claude";

  private final LlmClient delegate;
  private final RetryExecutor retry;

  public RetryingLlmClient(LlmClient delegate, RetryExecutor retry) {
    this.delegate = delegate;
    this.retry = retry;
  }

  @Override
  public LlmResponse send(LlmRequest request) {
    return retry.execute(API, () -> attempt(request));
  }

  private Attempt<LlmResponse> attempt(LlmRequest request) {
    try {
      return new Attempt.Success<>(delegate.send(request));
    } catch (LlmApiException e) {
      Integer status = e.status();
      if (status == null) {
        // 예외로 던지면 실행기가 재시도 없이 통과시키므로, 재시도할 네트워크 오류는 Retryable로 돌려준다
        if (AnthropicLlmClient.TIMEOUT.equals(e.errorKind())
            || AnthropicLlmClient.CONNECTION_ERROR.equals(e.errorKind())) {
          return new Attempt.Retryable<>(e.errorKind(), null, null);
        }
        throw e;
      }
      if (status == 429 || status >= 500) {
        // retry-after는 가공하지 않고 넘긴다. 유효한지는 실행기가 판단한다
        return new Attempt.Retryable<>(String.valueOf(status), e.retryAfter(), null);
      }
      return new Attempt.Fatal<>(API, status);
    }
  }
}
