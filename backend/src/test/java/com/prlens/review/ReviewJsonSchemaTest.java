package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.Severity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ReviewJsonSchemaTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  // design.md "구조화된 출력 JSON 스키마"의 스냅샷
  private static final String EXPECTED =
      """
      {
        "type": "object",
        "additionalProperties": false,
        "required": ["summary", "findings"],
        "properties": {
          "summary": { "type": "string" },
          "findings": {
            "type": "array",
            "items": {
              "type": "object",
              "additionalProperties": false,
              "required": ["file", "line", "severity", "category", "message", "suggestion", "basis"],
              "properties": {
                "file": { "type": "string" },
                "line": { "type": ["integer", "null"] },
                "severity": { "type": "string", "enum": ["blocker", "major", "minor", "nit"] },
                "category": { "type": "string", "enum": ["correctness", "security", "convention", "test", "design"] },
                "message": { "type": "string" },
                "suggestion": { "type": ["string", "null"] },
                "basis": {
                  "type": "object",
                  "additionalProperties": false,
                  "required": ["type", "ref"],
                  "properties": {
                    "type": { "type": "string", "enum": ["rule", "spec", "general"] },
                    "ref": { "type": ["string", "null"] }
                  }
                }
              }
            }
          }
        }
      }
      """;

  private static JsonNode schema() {
    return MAPPER.readTree(ReviewJsonSchema.JSON);
  }

  private static JsonNode finding() {
    return schema().get("properties").get("findings").get("items");
  }

  @Test
  void schemaMatchesDesignSnapshot() {
    assertThat(schema()).isEqualTo(MAPPER.readTree(EXPECTED));
  }

  @Test
  void everyObjectRequiresAllItsProperties() {
    List<JsonNode> objects = List.of(schema(), finding(), finding().get("properties").get("basis"));

    for (JsonNode object : objects) {
      List<String> required = new ArrayList<>();
      object.get("required").forEach(n -> required.add(n.asString()));
      List<String> properties = new ArrayList<>();
      object.get("properties").propertyNames().forEach(properties::add);
      assertThat(required).containsExactlyInAnyOrderElementsOf(properties);
      assertThat(object.get("additionalProperties").asBoolean(true)).isFalse();
    }
  }

  @Test
  void onlyLineSuggestionAndBasisRefAreNullable() {
    JsonNode props = finding().get("properties");

    assertThat(ReviewJsonSchema.JSON.split("\"null\"", -1).length - 1).isEqualTo(3);
    assertThat(props.get("line").get("type").toString()).contains("null");
    assertThat(props.get("suggestion").get("type").toString()).contains("null");
    assertThat(props.get("basis").get("properties").get("ref").get("type").toString())
        .contains("null");
  }

  @Test
  void hasNoUnsupportedNumericOrStringConstraints() {
    assertThat(ReviewJsonSchema.JSON)
        .doesNotContain("minimum", "minLength", "maximum", "maxLength");
  }

  @Test
  void enumValuesMatchModelEnumsInLowerCase() {
    JsonNode props = finding().get("properties");

    assertThat(strings(props.get("severity").get("enum")))
        .containsExactlyElementsOf(lower(Severity.values()));
    assertThat(strings(props.get("category").get("enum")))
        .containsExactlyElementsOf(lower(Category.values()));
    assertThat(strings(props.get("basis").get("properties").get("type").get("enum")))
        .containsExactlyElementsOf(lower(BasisType.values()));
  }

  private static List<String> strings(JsonNode array) {
    List<String> out = new ArrayList<>();
    array.forEach(n -> out.add(n.asString()));
    return out;
  }

  private static List<String> lower(Enum<?>[] values) {
    return Arrays.stream(values).map(v -> v.name().toLowerCase(Locale.ROOT)).toList();
  }
}
