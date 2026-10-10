package com.prlens.glob;

import com.prlens.support.PrLensException;

/** glob 패턴의 문법 오류. 어떤 경우에 던지는지는 작업 6.1에서 채운다. */
public class GlobSyntaxException extends PrLensException {

  private final String pattern;
  private final String reason;

  public GlobSyntaxException(String pattern, String reason) {
    super("glob 패턴이 유효하지 않습니다: " + pattern + " (" + reason + ")");
    this.pattern = pattern;
    this.reason = reason;
  }

  public String pattern() {
    return pattern;
  }

  public String reason() {
    return reason;
  }
}
