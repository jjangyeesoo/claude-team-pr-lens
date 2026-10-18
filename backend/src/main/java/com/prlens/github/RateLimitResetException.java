package com.prlens.github;

import com.prlens.support.RetryAfterTooLongException;
import java.time.Instant;

/** rate limit이 풀리는 시각까지 60초를 넘어 재시도하지 않는다. */
public class RateLimitResetException extends RetryAfterTooLongException {

  public RateLimitResetException(long seconds, Instant resetAt) {
    super("GitHub", seconds);
  }

  public Instant resetAt() {
    return null;
  }
}
