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
import net.jqwik.api.Assume;
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

  /** enum 이름을 소문자로 쓰되 seed 문자열의 글자 코드에 따라 글자마다 대소문자를 섞는다. */
  private static String mixCase(String name, String seed) {
    StringBuilder out = new StringBuilder();
    String lower = name.toLowerCase(Locale.ROOT);
    for (int i = 0; i < lower.length(); i++) {
      boolean upper = !seed.isEmpty() && (seed.charAt(i % seed.length()) + i) % 2 == 0;
      out.append(upper ? Character.toUpperCase(lower.charAt(i)) : lower.charAt(i));
    }
    return out.toString();
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
      o.put("severity", mixCase(f.severity().name(), f.message()));
      o.put("category", mixCase(f.category().name(), f.message()));
      o.put("message", f.message());
      if (f.suggestion() == null) {
        o.putNull("suggestion");
      } else {
        o.put("suggestion", f.suggestion());
      }
      ObjectNode b = o.putObject("basis");
      b.put("type", mixCase(f.basisType().name(), f.message()));
      if (f.basisRef() == null) {
        b.putNull("ref");
      } else {
        b.put("ref", f.basisRef());
      }
    }
    JsonNode node = root;
    return MAPPER.writeValueAsString(node);
  }

  /** 앞 2,000자. 끝이 서로게이트 쌍을 가르면 한 글자 줄인다. */
  private static String excerptOf(String text) {
    int end = Math.min(2000, text.length());
    if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) {
      end--;
    }
    return text.substring(0, end);
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
    assertThat(r.rawResponseExcerpt()).isEqualTo(excerptOf(text));
    assertThat(r.usage()).isEqualTo(USAGE);
  }

  private record Mutation(int findingIndex, int kind) {}

  private static final String[] REMOVABLE = {
    "file",
    "line",
    "severity",
    "category",
    "message",
    "suggestion",
    "basis",
    "basis.type",
    "basis.ref"
  };

  @Provide
  Arbitrary<Mutation> mutations() {
    // 0..8 필수 필드 삭제, 9 summary 삭제, 10~12 허용되지 않는 enum, 13 line <= 0, 14 빈 message
    return Combinators.combine(
            Arbitraries.integers().between(0, 5), Arbitraries.integers().between(0, 14))
        .as(Mutation::new);
  }

  // Feature: pr-lens-p1-cli, Property 31: 스키마 위반 처리 (유효 응답을 한 군데 깨뜨린 경우)
  @Property
  void mutatedValidResponseIsSchemaViolationWithExcerpt(
      @ForAll("findingLists") List<RawFinding> findings, @ForAll("mutations") Mutation mutation) {
    Assume.that(!findings.isEmpty());
    ObjectNode root = (ObjectNode) MAPPER.readTree(json("요약", findings));
    ObjectNode target =
        (ObjectNode) root.get("findings").get(mutation.findingIndex() % findings.size());
    int kind = mutation.kind();
    if (kind < REMOVABLE.length) {
      String[] path = REMOVABLE[kind].split("\\.");
      ObjectNode owner = path.length == 1 ? target : (ObjectNode) target.get(path[0]);
      owner.remove(path[path.length - 1]);
    } else if (kind == 9) {
      root.remove("summary");
    } else if (kind == 10) {
      target.put("severity", "bogus");
    } else if (kind == 11) {
      target.put("category", "bogus");
    } else if (kind == 12) {
      ((ObjectNode) target.get("basis")).put("type", "bogus");
    } else if (kind == 13) {
      target.put("line", -mutation.findingIndex());
    } else {
      target.put("message", "");
    }
    String text = MAPPER.writeValueAsString(root);

    ParsedResponse r = parse("end_turn", text);

    assertThat(r.incompleteReason()).isEqualTo(IncompleteReason.SCHEMA_VIOLATION);
    assertThat(r.findings()).isEmpty();
    assertThat(r.rawResponseExcerpt()).isEqualTo(excerptOf(text));
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
    // 절단 위치 앞에서 완전히 닫힌 Finding은 모두 남는다 (k번째 Finding의 닫는 }는 앞 k개 JSON 길이 - 3 위치)
    int closed = 0;
    for (int k = 1; k <= findings.size(); k++) {
      if (json("요약", findings.subList(0, k)).length() - 2 <= cut) {
        closed = k;
      }
    }
    assertThat(r.findings()).containsExactlyElementsOf(findings.subList(0, closed));
    assertThat(r.summary()).isIn("", "요약");
    if (cut >= "{\"summary\":\"요약\"".length()) {
      assertThat(r.summary()).isEqualTo("요약");
    }
  }

  @Provide
  Arbitrary<Double> cutRatio() {
    return Arbitraries.doubles().between(0.0, 1.0);
  }
}
