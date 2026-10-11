package com.prlens.llm;

import com.prlens.support.PrLensException;

/** Claude API 호출 실패. {@code RetryingLlmClient}가 재시도 여부를 분류한다. */
public class LlmApiException extends PrLensException {

  private final Integer status;
  private final String errorKind;
  private final String retryAfter;

  public LlmApiException(
      String message, Integer status, String errorKind, String retryAfter, Throwable cause) {
    super(message, cause);
    this.status = status;
    this.errorKind = errorKind;
    this.retryAfter = retryAfter;
  }

  /** HTTP 상태 코드. 연결 실패나 제한 시간 초과면 null이다. */
  public Integer status() {
    return status;
  }

  /** 상태 코드 문자열 또는 네트워크 오류 종류. */
  public String errorKind() {
    return errorKind;
  }

  /** {@code retry-after} 헤더 원문. 없으면 null이다. */
  public String retryAfter() {
    return retryAfter;
  }
}
