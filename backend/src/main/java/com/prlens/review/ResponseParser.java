package com.prlens.review;

import com.prlens.llm.LlmResponse;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.IncompleteReason;
import com.prlens.model.Severity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM 응답을 {@link ParsedResponse}로 바꾼다 (요구사항 9.4~9.6, 16.1, 16.2).
 *
 * <p>스키마 검증은 구조화된 출력에 맡기지 않고 여기서 다시 한다. enum은 대소문자를 무시하고 소문자로 정규화한다 (설계 G-7). 이 클래스는 basis와 라인을
 * 판정하지 않는다({@code FindingValidator}의 일).
 */
final class ResponseParser {

  private static final int EXCERPT_LENGTH = 2000;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  ParsedResponse parse(LlmResponse response) {
    String text = response.text() == null ? "" : response.text();
    String stopReason = response.stopReason() == null ? "" : response.stopReason();
    return switch (stopReason) {
      case "end_turn" -> parseComplete(response, text);
      case "refusal" ->
          new ParsedResponse("", List.of(), IncompleteReason.REFUSAL, null, response.usage());
      case "max_tokens", "model_context_window_exceeded" -> parseTruncated(response, text);
      default -> violation(response, text);
    };
  }

  private ParsedResponse parseComplete(LlmResponse response, String text) {
    try {
      JsonNode root = MAPPER.readTree(text);
      if (!root.isObject()
          || !root.path("summary").isString()
          || !root.path("findings").isArray()) {
        return violation(response, text);
      }
      List<RawFinding> findings = new ArrayList<>();
      for (JsonNode node : root.get("findings")) {
        RawFinding finding = toFinding(node);
        if (finding == null) {
          return violation(response, text);
        }
        findings.add(finding);
      }
      return new ParsedResponse(
          root.get("summary").asString(), findings, null, null, response.usage());
    } catch (JacksonException e) {
      return violation(response, text);
    }
  }

  /** 잘린 응답에서 완전히 닫힌 유효 Finding만 남긴다. 잘린 지점의 예외는 정상 경로다. */
  private ParsedResponse parseTruncated(LlmResponse response, String text) {
    String summary = "";
    List<RawFinding> findings = new ArrayList<>();
    try (JsonParser parser = MAPPER.createParser(text)) {
      if (parser.nextToken() == JsonToken.START_OBJECT) {
        while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
          String name = parser.currentName();
          JsonToken value = parser.nextToken();
          if ("summary".equals(name) && value == JsonToken.VALUE_STRING) {
            summary = parser.getString();
          } else if ("findings".equals(name) && value == JsonToken.START_ARRAY) {
            while (parser.nextToken() == JsonToken.START_OBJECT) {
              RawFinding finding = toFinding(parser.readValueAsTree());
              if (finding != null) {
                findings.add(finding);
              }
            }
          } else if (value != null) {
            parser.skipChildren();
          }
        }
      }
    } catch (JacksonException e) {
      // 출력이 잘린 지점이다. 그때까지 읽은 것을 쓴다.
    }
    return new ParsedResponse(
        summary, findings, IncompleteReason.MAX_TOKENS, null, response.usage());
  }

  private static ParsedResponse violation(LlmResponse response, String text) {
    String excerpt = text.substring(0, Math.min(EXCERPT_LENGTH, text.length()));
    return new ParsedResponse(
        "", List.of(), IncompleteReason.SCHEMA_VIOLATION, excerpt, response.usage());
  }

  /** 필드가 모두 갖춰지고 값이 유효하면 {@link RawFinding}, 아니면 null. */
  private static RawFinding toFinding(JsonNode node) {
    if (!node.isObject()) {
      return null;
    }
    JsonNode file = node.path("file");
    JsonNode line = node.path("line");
    JsonNode message = node.path("message");
    JsonNode suggestion = node.path("suggestion");
    JsonNode basis = node.path("basis");
    if (!file.isString() || !message.isString() || message.asString().isEmpty()) {
      return null;
    }
    if (!(line.isNull()
        || (line.isIntegralNumber() && line.canConvertToInt() && line.asInt() >= 1))) {
      return null;
    }
    if (!(suggestion.isNull() || suggestion.isString())) {
      return null;
    }
    if (!basis.isObject() || !basis.path("type").isString()) {
      return null;
    }
    JsonNode ref = basis.path("ref");
    if (!(ref.isNull() || ref.isString())) {
      return null;
    }
    Severity severity = enumValue(Severity.class, node.path("severity"));
    Category category = enumValue(Category.class, node.path("category"));
    BasisType basisType = enumValue(BasisType.class, basis.path("type"));
    if (severity == null || category == null || basisType == null) {
      return null;
    }
    return new RawFinding(
        file.asString(),
        line.isNull() ? null : line.asInt(),
        severity,
        category,
        message.asString(),
        suggestion.isNull() ? null : suggestion.asString(),
        basisType,
        ref.isNull() ? null : ref.asString());
  }

  private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node) {
    if (!node.isString()) {
      return null;
    }
    String normalized = node.asString().toUpperCase(Locale.ROOT);
    for (E constant : type.getEnumConstants()) {
      if (constant.name().equals(normalized)) {
        return constant;
      }
    }
    return null;
  }
}
