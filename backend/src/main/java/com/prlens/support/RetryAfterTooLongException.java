package com.prlens.support;

/** 다음 시도까지의 대기 시간이 60초를 넘어 재시도하지 않는다 (요구사항 21.5, 21.10). */
public class RetryAfterTooLongException extends PrLensException {

  private final String api;
  private final long seconds;

  /**
   * @param seconds {@code retry-after} 값 또는 호출자가 정한 대기 시간(초 단위로 올림)
   */
  public RetryAfterTooLongException(String api, long seconds) {
    super(api + " 요청의 대기 시간이 " + seconds + "초로 60초를 넘어 재시도하지 않습니다");
    this.api = api;
    this.seconds = seconds;
  }

  public String api() {
    return api;
  }

  public long seconds() {
    return seconds;
  }
}
