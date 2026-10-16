package com.prlens.review;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Delimiter_Tag 모양의 문자열을 가역적으로 이스케이프한다 (요구사항 14.5, 14.8).
 *
 * <p>바이트 스터핑이다. {@code <} 뒤에 {@code ~}가 0개 이상, 이어서 {@code /}(선택)와 {@code prlens:}(대소문자 무시)가 오면
 * {@code <} 바로 뒤에 {@code ~}를 하나 더한다. 코드에 흔한 {@code <}, {@code &}는 건드리지 않는다.
 */
final class DelimiterCodec {

  private static final Pattern TAG_LIKE =
      Pattern.compile("<(~*)(/?prlens:)", Pattern.CASE_INSENSITIVE);
  private static final Pattern ESCAPED =
      Pattern.compile("<~(~*)(/?prlens:)", Pattern.CASE_INSENSITIVE);

  private DelimiterCodec() {}

  static String escape(String text) {
    return TAG_LIKE.matcher(text).replaceAll(r -> "<~" + r.group(1) + r.group(2));
  }

  static String unescape(String text) {
    Matcher matcher = ESCAPED.matcher(text);
    return matcher.replaceAll(r -> "<" + r.group(1) + r.group(2));
  }
}
