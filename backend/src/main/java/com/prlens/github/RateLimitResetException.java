package com.prlens.github;

import com.prlens.support.RetryAfterTooLongException;
import java.time.Instant;

/** rate limit이 풀리는 시각까지 60초를 넘어 재시도하지 않는다 (요구사항 21.10). */
public class RateLimitResetException extends RetryAfterTooLongException {

  private final Instant resetAt;

  public RateLimitResetException(long seconds, Instant resetAt) {
    super("GitHub", seconds);
    this.resetAt = resetAt;
  }

  public Instant resetAt() {
    return resetAt;
  }

  @Override
  public String getMessage() {
    return "GitHub API 요청 한도가 " + resetAt + "에 풀립니다 (" + seconds() + "초 뒤). 그 뒤에 다시 실행하세요";
  }
}
