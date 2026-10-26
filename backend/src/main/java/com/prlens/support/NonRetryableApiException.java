package com.prlens.support;

/** 재시도 대상이 아닌 응답(429 외 4xx 등)으로 요청이 실패했다 (요구사항 21.8). */
public class NonRetryableApiException extends PrLensException {

  private final String api;
  private final int status;
  private final String reason;

  public NonRetryableApiException(String api, int status) {
    this(api, status, null);
  }

  /**
   * @param reason 응답이 알려 준 사유(예: 오류 종류와 메시지). 없으면 null이다
   */
  public NonRetryableApiException(String api, int status, String reason) {
    super(api + " 요청이 실패했습니다 (상태 코드 " + status + (reason == null ? "" : ", " + reason) + ")");
    this.api = api;
    this.status = status;
    this.reason = reason;
  }

  public String api() {
    return api;
  }

  public int status() {
    return status;
  }

  /** 응답이 알려 준 사유. 없으면 null이다. */
  public String reason() {
    return reason;
  }
}
