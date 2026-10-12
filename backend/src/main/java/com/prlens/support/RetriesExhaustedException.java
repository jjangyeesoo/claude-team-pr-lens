package com.prlens.support;

/** 재시도 횟수를 모두 쓴 뒤에도 요청이 실패했다 (요구사항 21.6). */
public class RetriesExhaustedException extends PrLensException {

  private final String api;
  private final String lastStatus;
  private final int attempts;

  /**
   * @param lastStatus 마지막 상태 코드 또는 네트워크 오류 종류
   * @param attempts 첫 시도를 포함한 시도 횟수
   */
  public RetriesExhaustedException(String api, String lastStatus, int attempts) {
    super(api + " 요청이 " + attempts + "번 시도 뒤에도 실패했습니다 (마지막 응답: " + lastStatus + ")");
    this.api = api;
    this.lastStatus = lastStatus;
    this.attempts = attempts;
  }

  public String api() {
    return api;
  }

  public String lastStatus() {
    return lastStatus;
  }

  public int attempts() {
    return attempts;
  }
}
