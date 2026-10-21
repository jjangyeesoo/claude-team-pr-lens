package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.IncompleteReason;
import com.prlens.model.Severity;
import org.junit.jupiter.api.Test;

class ResponseParserTest {

  private static final LlmUsage USAGE = new LlmUsage(100, 20, 5, 7);
  private final ResponseParser parser = new ResponseParser();

  private static String finding(String severity, String category, String basisType, String ref) {
    return """
        {"file":"src/A.java","line":12,"severity":"%s","category":"%s","message":"문제","suggestion":null,\
        "basis":{"type":"%s","ref":%s}}"""
        .formatted(severity, category, basisType, ref);
  }

  private static String body(String summary, String... findings) {
    return "{\"summary\":\"" + summary + "\",\"findings\":[" + String.join(",", findings) + "]}";
  }

  private ParsedResponse parse(String stopReason, String text) {
    return parser.parse(new LlmResponse(stopReason, text, USAGE));
  }

  // 요구사항 9.4
  @Test
  void endTurnValidResponseIsComplete() {
    ParsedResponse r =
        parse(
            "end_turn", body("요약", finding("major", "security", "rule", "\".claude/rules/a.md\"")));

    assertThat(r.incompleteReason()).isNull();
    assertThat(r.summary()).isEqualTo("요약");
    assertThat(r.usage()).isEqualTo(USAGE);
    assertThat(r.findings()).hasSize(1);
    RawFinding f = r.findings().get(0);
    assertThat(f.file()).isEqualTo("src/A.java");
    assertThat(f.line()).isEqualTo(12);
    assertThat(f.severity()).isEqualTo(Severity.MAJOR);
    assertThat(f.category()).isEqualTo(Category.SECURITY);
    assertThat(f.message()).isEqualTo("문제");
    assertThat(f.suggestion()).isNull();
    assertThat(f.basisType()).isEqualTo(BasisType.RULE);
    assertThat(f.basisRef()).isEqualTo(".claude/rules/a.md");
  }

  // 요구사항 9.5
  @Test
  void emptyFindingsIsComplete() {
    ParsedResponse r = parse("end_turn", body("지적 없음"));

    assertThat(r.incompleteReason()).isNull();
    assertThat(r.findings()).isEmpty();
  }

  @Test
  void nullLineIsAllowed() {
    String f = finding("nit", "design", "general", "null").replace("\"line\":12", "\"line\":null");

    ParsedResponse r = parse("end_turn", body("s", f));

    assertThat(r.incompleteReason()).isNull();
    assertThat(r.findings().get(0).line()).isNull();
  }

  // 요구사항 9.6
  @Test
  void invalidJsonIsSchemaViolationWithTruncatedExcerpt() {
    String text = "x".repeat(2500);

    ParsedResponse r = parse("end_turn", text);

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
    assertThat(r.findings()).isEmpty();
    assertThat(r.rawResponseExcerpt()).isEqualTo("x".repeat(2000));
    assertThat(r.usage()).isEqualTo(USAGE);
  }

  // 설계 G-7
  @Test
  void enumCaseIsIgnoredAndNormalized() {
    ParsedResponse r =
        parse("end_turn", body("s", finding("MAJOR", "Security", "GENERAL", "null")));

    assertThat(r.incompleteReason()).isNull();
    assertThat(r.findings().get(0).severity()).isEqualTo(Severity.MAJOR);
    assertThat(r.findings().get(0).category()).isEqualTo(Category.SECURITY);
    assertThat(r.findings().get(0).basisType()).isEqualTo(BasisType.GENERAL);
  }

  @Test
  void unknownEnumValueIsSchemaViolation() {
    ParsedResponse r =
        parse("end_turn", body("s", finding("critical", "security", "general", "null")));

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
    assertThat(r.findings()).isEmpty();
  }

  @Test
  void lineBelowOneIsSchemaViolation() {
    String f = finding("minor", "test", "general", "null").replace("\"line\":12", "\"line\":0");

    assertThat(parse("end_turn", body("s", f)).incompleteReason())
        .isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
  }

  @Test
  void emptyMessageIsSchemaViolation() {
    String f = finding("minor", "test", "general", "null").replace("\"문제\"", "\"\"");

    assertThat(parse("end_turn", body("s", f)).incompleteReason())
        .isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
  }

  @Test
  void missingRequiredFieldIsSchemaViolation() {
    String f = "{\"file\":\"a\",\"line\":1,\"severity\":\"nit\",\"category\":\"test\"}";

    assertThat(parse("end_turn", body("s", f)).incompleteReason())
        .isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
  }

  // 요구사항 16.1
  @Test
  void refusalDoesNotParseBody() {
    ParsedResponse r = parse("refusal", body("s", finding("major", "security", "general", "null")));

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.REFUSAL);
    assertThat(r.findings()).isEmpty();
    assertThat(r.usage()).isEqualTo(USAGE);
  }

  // 요구사항 16.2
  @Test
  void maxTokensKeepsOnlyCompletelyClosedValidFindings() {
    String f1 = finding("major", "security", "general", "null");
    String f2 = finding("minor", "design", "general", "null");
    String truncated = "{\"summary\":\"요약\",\"findings\":[" + f1 + "," + f2.substring(0, 30);

    ParsedResponse r = parse("max_tokens", truncated);

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.MAX_TOKENS);
    assertThat(r.summary()).isEqualTo("요약");
    assertThat(r.findings()).hasSize(1);
    assertThat(r.findings().get(0).severity()).isEqualTo(Severity.MAJOR);
  }

  @Test
  void maxTokensDropsClosedFindingWithInvalidEnum() {
    String bad = finding("critical", "security", "general", "null");
    String good = finding("nit", "test", "general", "null");

    ParsedResponse r =
        parse("max_tokens", "{\"summary\":\"s\",\"findings\":[" + bad + "," + good + ",{\"fi");

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.MAX_TOKENS);
    assertThat(r.findings()).hasSize(1);
    assertThat(r.findings().get(0).severity()).isEqualTo(Severity.NIT);
  }

  @Test
  void maxTokensWithoutSummaryUsesEmptyString() {
    ParsedResponse r = parse("max_tokens", "{\"summ");

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.MAX_TOKENS);
    assertThat(r.summary()).isEmpty();
    assertThat(r.findings()).isEmpty();
  }

  @Test
  void contextWindowExceededUsesMaxTokensReason() {
    ParsedResponse r = parse("model_context_window_exceeded", "{\"summary\":\"s\",\"findings\":[");

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.MAX_TOKENS);
    assertThat(r.summary()).isEqualTo("s");
  }

  @Test
  void otherStopReasonsAreSchemaViolation() {
    for (String reason : new String[] {"stop_sequence", "tool_use", "pause_turn"}) {
      ParsedResponse r = parse(reason, body("s"));

      assertThat(r.incompleteReason()).as(reason).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
      assertThat(r.findings()).isEmpty();
      assertThat(r.rawResponseExcerpt()).isEqualTo(body("s"));
    }
  }

  @Test
  void nullStopReasonOrTextIsSchemaViolationWithEmptyExcerpt() {
    ParsedResponse nullText = parse("end_turn", null);
    ParsedResponse nullStop = parse(null, body("s"));

    assertThat(nullText.incompleteReason()).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
    assertThat(nullText.rawResponseExcerpt()).isEmpty();
    assertThat(nullStop.incompleteReason()).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
    assertThat(nullStop.rawResponseExcerpt()).isEqualTo(body("s"));
  }

  @Test
  void excerptDoesNotSplitSurrogatePair() {
    String text = "x".repeat(1999) + "😀" + "y";

    ParsedResponse r = parse("end_turn", text);

    assertThat(r.rawResponseExcerpt()).isEqualTo("x".repeat(1999));
  }
}
