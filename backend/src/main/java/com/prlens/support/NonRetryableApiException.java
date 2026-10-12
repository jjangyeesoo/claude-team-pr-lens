package com.prlens.support;

/** 재시도 대상이 아닌 응답(429 외 4xx 등)으로 요청이 실패했다 (요구사항 21.8). */
public class NonRetryableApiException extends PrLensException {

  private final String api;
  private final int status;

  public NonRetryableApiException(String api, int status) {
    super(api + " 요청이 실패했습니다 (상태 코드 " + status + ")");
    this.api = api;
    this.status = status;
  }

  public String api() {
    return api;
  }

  public int status() {
    return status;
  }
}
