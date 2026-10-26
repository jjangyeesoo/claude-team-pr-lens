package com.prlens.llm;

import com.prlens.support.PrLensException;

/** Claude API 호출 실패. {@code RetryingLlmClient}가 재시도 여부를 분류한다. */
public class LlmApiException extends PrLensException {

  private final Integer status;
  private final String errorKind;
  private final String retryAfter;
  private final String detail;

  public LlmApiException(
      String message, Integer status, String errorKind, String retryAfter, Throwable cause) {
    this(message, status, errorKind, retryAfter, null, cause);
  }

  /**
   * @param detail 응답 본문이 알려 준 사유(오류 종류와 메시지). 없으면 null이다
   */
  public LlmApiException(
      String message,
      Integer status,
      String errorKind,
      String retryAfter,
      String detail,
      Throwable cause) {
    super(message, cause);
    this.status = status;
    this.errorKind = errorKind;
    this.retryAfter = retryAfter;
    this.detail = detail;
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

  /** 응답 본문이 알려 준 사유. {@code Options.masker}로 가렸다(빈 마스커면 가리지 않는다). 없으면 null이다. */
  public String detail() {
    return detail;
  }
}
