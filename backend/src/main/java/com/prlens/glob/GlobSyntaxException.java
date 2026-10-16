package com.prlens.glob;

import com.prlens.support.PrLensException;

/**
 * glob 패턴의 문법 오류. 짝이 맞지 않는 중괄호, 중괄호 중첩 32단계 초과, 중괄호를 펼친 개수 256 초과, 백슬래시, 앞의 {@code ./}·{@code /}를 뗀
 * 뒤 빈 패턴(중괄호를 펼친 패턴 하나라도 비면 포함), 구간 일부로 쓰인 {@code **}일 때 {@code GlobMatcher}가 던진다.
 */
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
