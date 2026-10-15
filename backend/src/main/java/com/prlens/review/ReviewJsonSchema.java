package com.prlens.review;

/**
 * 구조화된 출력({@code output_config.format})에 전달하는 JSON 스키마 (design.md "구조화된 출력 JSON 스키마").
 *
 * <p>스키마를 바꾸면 프롬프트 캐시가 무효화되므로 키 순서까지 고정된 문자열로 둔다. Java SDK는 {@code minimum}, {@code minLength}를 지우지
 * 않으므로 넣지 않는다. {@code line >= 1}과 {@code message} 비어 있음은 {@link ResponseParser}가 검증한다.
 */
final class ReviewJsonSchema {

  static final String JSON =
      """
      {"type":"object","additionalProperties":false,"required":["summary","findings"],\
      "properties":{"summary":{"type":"string"},\
      "findings":{"type":"array","items":{"type":"object","additionalProperties":false,\
      "required":["file","line","severity","category","message","suggestion","basis"],\
      "properties":{"file":{"type":"string"},\
      "line":{"type":["integer","null"]},\
      "severity":{"type":"string","enum":["blocker","major","minor","nit"]},\
      "category":{"type":"string","enum":["correctness","security","convention","test","design"]},\
      "message":{"type":"string"},\
      "suggestion":{"type":["string","null"]},\
      "basis":{"type":"object","additionalProperties":false,"required":["type","ref"],\
      "properties":{"type":{"type":"string","enum":["rule","spec","general"]},\
      "ref":{"type":["string","null"]}}}}}}}}""";

  private ReviewJsonSchema() {}
}
