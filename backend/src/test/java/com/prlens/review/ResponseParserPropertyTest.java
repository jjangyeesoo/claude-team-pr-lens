package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.IncompleteReason;
import com.prlens.model.Severity;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

// 작업 10.3: Property 30(정상 응답 변환), 31(스키마 위반), 32(max_tokens 절단 복구)
class ResponseParserPropertyTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final LlmUsage USAGE = new LlmUsage(1, 2, 3, 4);
  private final ResponseParser parser = new ResponseParser();

  @Provide
  Arbitrary<RawFinding> rawFindings() {
    Arbitrary<String> text = Arbitraries.strings().ofMinLength(1).ofMaxLength(20);
    return Combinators.combine(
            text,
            Arbitraries.integers().between(1, 5000).injectNull(0.2),
            Arbitraries.of(Severity.values()),
            Arbitraries.of(Category.values()),
            text,
            text.injectNull(0.3),
            Arbitraries.of(BasisType.values()),
            text.injectNull(0.3))
        .as(RawFinding::new);
  }

  @Provide
  Arbitrary<List<RawFinding>> findingLists() {
    return rawFindings().list().ofMaxSize(6);
  }

  private static String json(String summary, List<RawFinding> findings) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("summary", summary);
    ArrayNode array = root.putArray("findings");
    for (RawFinding f : findings) {
      ObjectNode o = array.addObject();
      o.put("file", f.file());
      if (f.line() == null) {
        o.putNull("line");
      } else {
        o.put("line", f.line());
      }
      o.put("severity", f.severity().name().toLowerCase(Locale.ROOT));
      o.put("category", f.category().name().toLowerCase(Locale.ROOT));
      o.put("message", f.message());
      if (f.suggestion() == null) {
        o.putNull("suggestion");
      } else {
        o.put("suggestion", f.suggestion());
      }
      ObjectNode b = o.putObject("basis");
      b.put("type", f.basisType().name().toLowerCase(Locale.ROOT));
      if (f.basisRef() == null) {
        b.putNull("ref");
      } else {
        b.put("ref", f.basisRef());
      }
    }
    JsonNode node = root;
    return MAPPER.writeValueAsString(node);
  }

  private ParsedResponse parse(String stop, String text) {
    return parser.parse(new LlmResponse(stop, text, USAGE));
  }

  // Feature: pr-lens-p1-cli, Property 30: 정상 응답 변환
  @Property
  void validResponseConvertsToCompleteResult(
      @ForAll("findingLists") List<RawFinding> findings, @ForAll String summary) {
    ParsedResponse r = parse("end_turn", json(summary, findings));

    assertThat(r.incompleteReason()).isNull();
    assertThat(r.summary()).isEqualTo(summary);
    assertThat(r.findings()).containsExactlyElementsOf(findings);
    assertThat(r.usage()).isEqualTo(USAGE);
  }

  // Feature: pr-lens-p1-cli, Property 31: 스키마 위반 처리
  @Property
  void nonJsonOrWrongShapeIsSchemaViolationWithExcerpt(@ForAll("garbage") String text) {
    ParsedResponse r = parse("end_turn", text);

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
    assertThat(r.findings()).isEmpty();
    assertThat(r.rawResponseExcerpt()).isEqualTo(text.substring(0, Math.min(2000, text.length())));
    assertThat(r.usage()).isEqualTo(USAGE);
  }

  @Provide
  Arbitrary<String> garbage() {
    // 객체가 아니거나 필수 필드가 없는 응답
    return Arbitraries.oneOf(
        Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(3000),
        Arbitraries.strings().alpha().ofMaxLength(10).map(s -> "[\"" + s + "\"]"),
        Arbitraries.strings().alpha().ofMaxLength(10).map(s -> "{\"" + s + "x\":1}"));
  }

  // Feature: pr-lens-p1-cli, Property 32: max_tokens 절단 복구
  @Property
  void truncatedResponseKeepsPrefixOfOriginalFindings(
      @ForAll("findingLists") List<RawFinding> findings, @ForAll("cutRatio") double ratio) {
    String full = json("요약", findings);
    int cut = Math.min((int) Math.floor(full.length() * ratio), full.length() - 1);

    ParsedResponse r = parse("max_tokens", full.substring(0, cut));

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.MAX_TOKENS);
    assertThat(r.summary()).isIn("", "요약");
    assertThat(r.findings().size()).isLessThanOrEqualTo(findings.size());
    assertThat(r.findings()).containsExactlyElementsOf(findings.subList(0, r.findings().size()));
  }

  @Provide
  Arbitrary<Double> cutRatio() {
    return Arbitraries.doubles().between(0.0, 1.0);
  }
}
