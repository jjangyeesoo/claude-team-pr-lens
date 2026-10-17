package com.prlens.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.model.ReviewResult;
import com.prlens.testkit.ReviewResultArbitraries;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ResultCodecPropertyTest {

  // 비용이 double을 거치면 유효 숫자 18자리가 깨지므로 ResultCodec과 같은 설정을 쓴다.
  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
          .build();
  private static final List<String> TOKEN_KEYS =
      List.of("inputTokens", "outputTokens", "cacheWriteTokens", "cacheReadTokens");

  private final ResultCodec codec = new ResultCodec();

  @Provide
  Arbitrary<ReviewResult> results() {
    return ReviewResultArbitraries.reviewResults();
  }

  private ObjectNode tree(ReviewResult result) {
    return (ObjectNode) MAPPER.readTree(codec.write(result));
  }

  // Feature: pr-lens-p1-cli, Property 19: ReviewResult JSON round-trip
  @Property(tries = 300)
  void roundTripKeepsEveryField(@ForAll("results") ReviewResult result) {
    String json = codec.write(result);

    assertThat(codec.read(json)).isEqualTo(result);
    assertThat(codec.write(codec.read(json))).isEqualTo(json);
    assertThat(json).doesNotContain("\r");
  }

  // Feature: pr-lens-p1-cli, Property 19: ReviewResult JSON round-trip
  @Property(tries = 300)
  void outputHasEveryOptionalAndListKey(@ForAll("results") ReviewResult result) {
    ObjectNode root = tree(result);

    assertThat(root.propertyNames())
        .containsExactlyInAnyOrder(
            "schemaVersion",
            "status",
            "incompleteReasons",
            "incompleteDetails",
            "stats",
            "summary",
            "findings",
            "excludedFiles",
            "excludedFileDetails",
            "usage");
    assertThat(root.get("incompleteDetails").propertyNames())
        .containsExactlyInAnyOrder("chunks", "fileCountGap");
    assertThat(root.get("usage").propertyNames())
        .containsExactlyInAnyOrder(
            "inputTokens",
            "outputTokens",
            "cacheWriteTokens",
            "cacheReadTokens",
            "model",
            "estimatedCostUsd");
    for (JsonNode finding : root.get("findings")) {
      assertThat(finding.propertyNames())
          .containsExactlyInAnyOrder(
              "file",
              "line",
              "severity",
              "category",
              "message",
              "suggestion",
              "basis",
              "demotion",
              "verdict",
              "summaryOnlyReason");
      assertThat(finding.get("basis").propertyNames()).containsExactlyInAnyOrder("type", "ref");
    }
    for (JsonNode chunk : root.get("incompleteDetails").get("chunks")) {
      assertThat(chunk.propertyNames())
          .containsExactlyInAnyOrder(
              "chunkIndex", "files", "reason", "lastStatusCode", "rawResponseExcerpt");
    }
  }

  // Feature: pr-lens-p1-cli, Property 20: 모르는 필드 무시
  @Property(tries = 300)
  void unknownFieldsAreIgnoredAtAnyPosition(
      @ForAll("results") ReviewResult result,
      @ForAll("unknownValues") JsonNode value,
      @ForAll("positions") List<Integer> positions) {
    ObjectNode root = tree(result);
    List<ObjectNode> objects = new ArrayList<>();
    collectObjects(root, objects);
    for (int position : positions) {
      objects.get(position % objects.size()).set("unknown_field_" + position, value);
    }

    assertThat(codec.read(MAPPER.writeValueAsString(root))).isEqualTo(result);
  }

  @Provide
  Arbitrary<JsonNode> unknownValues() {
    return Arbitraries.of(
        MAPPER.readTree("\"text\""),
        MAPPER.readTree("12"),
        MAPPER.readTree("null"),
        MAPPER.readTree("[1, {\"a\": []}]"),
        MAPPER.readTree("{\"severity\": \"nope\", \"line\": -1}"));
  }

  @Provide
  Arbitrary<List<Integer>> positions() {
    return Arbitraries.integers().between(0, 1000).list().ofMinSize(1).ofMaxSize(4);
  }

  private static void collectObjects(JsonNode node, List<ObjectNode> out) {
    if (node instanceof ObjectNode object) {
      out.add(object);
    }
    node.forEach(child -> collectObjects(child, out));
  }

  private record Target(ObjectNode parent, String key, String path) {}

  private static String child(String path, String key) {
    return path.isEmpty() ? key : path + "." + key;
  }

  private static void addKeys(List<Target> out, JsonNode node, String path, String... keys) {
    for (String key : keys) {
      out.add(new Target((ObjectNode) node, key, child(path, key)));
    }
  }

  /** 필수 필드(선택 필드 제외)와 그 경로. */
  private static List<Target> requiredFields(ObjectNode root) {
    List<Target> out = new ArrayList<>();
    addKeys(
        out,
        root,
        "",
        "schemaVersion",
        "status",
        "incompleteReasons",
        "incompleteDetails",
        "stats",
        "summary",
        "findings",
        "excludedFiles",
        "excludedFileDetails",
        "usage");
    addKeys(out, root.get("stats"), "stats", "mode", "changedLineCount", "sizeLimit", "chunkCount");
    addKeys(out, root.get("usage"), "usage", "inputTokens", "outputTokens", "model");
    addKeys(out, root.get("usage"), "usage", "cacheWriteTokens", "cacheReadTokens");
    addKeys(out, root.get("incompleteDetails"), "incompleteDetails", "chunks");
    for (int i = 0; i < root.get("incompleteDetails").get("chunks").size(); i++) {
      JsonNode chunk = root.get("incompleteDetails").get("chunks").get(i);
      addKeys(out, chunk, "incompleteDetails.chunks[" + i + "]", "chunkIndex", "files", "reason");
    }
    for (int i = 0; i < root.get("findings").size(); i++) {
      JsonNode finding = root.get("findings").get(i);
      String path = "findings[" + i + "]";
      addKeys(out, finding, path, "file", "severity", "category", "message", "basis", "verdict");
      addKeys(out, finding.get("basis"), path + ".basis", "type");
    }
    for (int i = 0; i < root.get("excludedFileDetails").size(); i++) {
      addKeys(
          out,
          root.get("excludedFileDetails").get(i),
          "excludedFileDetails[" + i + "]",
          "path",
          "reason");
    }
    return out;
  }

  // Feature: pr-lens-p1-cli, Property 21: 역직렬화 오류 경로
  @Property(tries = 300)
  void missingRequiredFieldReportsItsPath(
      @ForAll("results") ReviewResult result, @ForAll("positions") List<Integer> pick) {
    ObjectNode root = tree(result);
    List<Target> targets = requiredFields(root);
    Target target = targets.get(pick.get(0) % targets.size());
    target.parent().remove(target.key());

    assertThatThrownBy(() -> codec.read(MAPPER.writeValueAsString(root)))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo(target.path()));
  }

  /** 열거형 필드와 허용 값. */
  private static List<Target> enumFields(ObjectNode root) {
    List<Target> out = new ArrayList<>();
    addKeys(out, root, "", "status");
    addKeys(out, root.get("stats"), "stats", "mode");
    ArrayNode reasons = (ArrayNode) root.get("incompleteReasons");
    for (int i = 0; i < reasons.size(); i++) {
      out.add(new Target(null, String.valueOf(i), "incompleteReasons[" + i + "]"));
    }
    for (int i = 0; i < root.get("incompleteDetails").get("chunks").size(); i++) {
      addKeys(
          out,
          root.get("incompleteDetails").get("chunks").get(i),
          "incompleteDetails.chunks[" + i + "]",
          "reason");
    }
    for (int i = 0; i < root.get("findings").size(); i++) {
      JsonNode finding = root.get("findings").get(i);
      String path = "findings[" + i + "]";
      addKeys(out, finding, path, "severity", "category", "verdict");
      addKeys(out, finding.get("basis"), path + ".basis", "type");
    }
    return out;
  }

  // Feature: pr-lens-p1-cli, Property 21: 역직렬화 오류 경로
  @Property(tries = 300)
  void badEnumValueReportsPathAndAllowedValues(
      @ForAll("results") ReviewResult result, @ForAll("positions") List<Integer> pick) {
    ObjectNode root = tree(result);
    List<Target> targets = enumFields(root);
    Target target = targets.get(pick.get(0) % targets.size());
    JsonNode original;
    if (target.parent() == null) {
      int index = Integer.parseInt(target.key());
      original = root.get("incompleteReasons").get(index);
      ((ArrayNode) root.get("incompleteReasons")).set(index, "NOT_A_VALUE");
    } else {
      original = target.parent().get(target.key());
      target.parent().put(target.key(), original.stringValue().toUpperCase(Locale.ROOT));
    }

    assertThatThrownBy(() -> codec.read(MAPPER.writeValueAsString(root)))
        .isInstanceOfSatisfying(
            CodecException.class,
            e -> {
              assertThat(e.path()).isEqualTo(target.path());
              assertThat(e.allowed()).isNotEmpty().contains(original.stringValue());
              assertThat(e.getMessage()).contains(e.allowed());
            });
  }

  // Feature: pr-lens-p1-cli, Property 21: 역직렬화 오류 경로
  @Property(tries = 300)
  void negativeTokenReportsItsPath(
      @ForAll("results") ReviewResult result,
      @ForAll("positions") List<Integer> pick,
      @ForAll("negatives") long negative) {
    ObjectNode root = tree(result);
    String key = TOKEN_KEYS.get(pick.get(0) % TOKEN_KEYS.size());
    ((ObjectNode) root.get("usage")).put(key, negative);

    assertThatThrownBy(() -> codec.read(MAPPER.writeValueAsString(root)))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("usage." + key));
  }

  @Provide
  Arbitrary<Long> negatives() {
    return Arbitraries.longs().between(Long.MIN_VALUE, -1);
  }
}
