package com.prlens.codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ChunkIssue;
import com.prlens.model.Demotion;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Severity;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class ResultCodecTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final ResultCodec codec = new ResultCodec();

  private static Finding inlineFinding() {
    return new Finding(
        "backend/src/A.java",
        42,
        Severity.MAJOR,
        Category.CONVENTION,
        "메시지",
        null,
        new Basis(BasisType.GENERAL, null),
        new Demotion(BasisType.RULE, ".claude/rules/backend/nope.md"),
        LineVerdict.INLINE_ELIGIBLE,
        null);
  }

  private static Finding summaryOnlyFinding() {
    return new Finding(
        "README.md",
        null,
        Severity.NIT,
        Category.DESIGN,
        "라인 없음",
        "제안",
        new Basis(BasisType.SPEC, "docs/specs/x/design.md"),
        null,
        LineVerdict.SUMMARY_ONLY,
        SummaryOnlyReason.LINE_MISSING);
  }

  private static ReviewResult complete() {
    return new ReviewResult(
        ResultStatus.COMPLETE,
        "요약",
        List.of(inlineFinding(), summaryOnlyFinding()),
        List.of("package-lock.json"),
        List.of(new ExcludedFile("package-lock.json", "**/package-lock.json")),
        new Usage(21_000, 3_100, 0, 9_000, "claude-opus-5-5", new BigDecimal("0.1478")),
        List.of(),
        new IncompleteDetails(List.of(), null),
        new ReviewStats(ReviewMode.SINGLE, 120, 400, 1));
  }

  private static ReviewResult incomplete() {
    return new ReviewResult(
        ResultStatus.INCOMPLETE,
        "",
        List.of(),
        List.of(),
        List.of(),
        new Usage(1, 2, 3, 4, "m", null),
        List.of(IncompleteReason.CHUNK_FAILED, IncompleteReason.FILES_TRUNCATED),
        new IncompleteDetails(
            List.of(
                new ChunkIssue(2, List.of("web/a.tsx"), IncompleteReason.CHUNK_FAILED, 529, null),
                new ChunkIssue(3, List.of(), IncompleteReason.SCHEMA_VIOLATION, null, "{\"bad\"")),
            new FileCountGap(3100, 3000)),
        new ReviewStats(ReviewMode.SPLIT, 812, 400, 3));
  }

  private static ObjectNode tree(ReviewResult result) {
    return (ObjectNode) MAPPER.readTree(new ResultCodec().write(result));
  }

  private ReviewResult readTree(JsonNode node) {
    return codec.read(MAPPER.writeValueAsString(node));
  }

  // 10.1, 10.2
  @Test
  void writesAllFieldsWithCamelCaseAndSnakeCaseEnums() {
    ObjectNode root = tree(complete());

    assertThat(root.get("schemaVersion").intValue()).isEqualTo(1);
    assertThat(root.get("status").asString()).isEqualTo("complete");
    assertThat(root.get("summary").asString()).isEqualTo("요약");
    assertThat(root.get("excludedFiles").get(0).asString()).isEqualTo("package-lock.json");
    assertThat(root.get("excludedFileDetails").get(0).get("reason").asString())
        .isEqualTo("**/package-lock.json");
    assertThat(root.get("stats").get("mode").asString()).isEqualTo("single");
    assertThat(root.get("stats").get("changedLineCount").intValue()).isEqualTo(120);
    JsonNode first = root.get("findings").get(0);
    assertThat(first.get("severity").asString()).isEqualTo("major");
    assertThat(first.get("category").asString()).isEqualTo("convention");
    assertThat(first.get("basis").get("type").asString()).isEqualTo("general");
    assertThat(first.get("demotion").get("originalType").asString()).isEqualTo("rule");
    assertThat(first.get("verdict").asString()).isEqualTo("inline_eligible");
    JsonNode second = root.get("findings").get(1);
    assertThat(second.get("verdict").asString()).isEqualTo("summary_only");
    assertThat(second.get("summaryOnlyReason").asString()).isEqualTo("line_missing");
    JsonNode usage = root.get("usage");
    assertThat(usage.get("inputTokens").longValue()).isEqualTo(21_000);
    assertThat(usage.get("cacheReadTokens").longValue()).isEqualTo(9_000);
    assertThat(usage.get("model").asString()).isEqualTo("claude-opus-5-5");
    assertThat(usage.get("estimatedCostUsd").isNumber()).isTrue();
    assertThat(usage.get("estimatedCostUsd").decimalValue()).isEqualByComparingTo("0.1478");
  }

  // 10.3
  @Test
  void writesAbsentOptionalFieldsAsNull() {
    ObjectNode root = tree(incomplete());
    JsonNode chunk = root.get("incompleteDetails").get("chunks").get(0);
    assertThat(chunk.has("rawResponseExcerpt")).isTrue();
    assertThat(chunk.get("rawResponseExcerpt").isNull()).isTrue();
    assertThat(root.get("usage").get("estimatedCostUsd").isNull()).isTrue();
    assertThat(root.get("incompleteDetails").get("chunks").get(1).get("lastStatusCode").isNull())
        .isTrue();

    JsonNode finding = tree(complete()).get("findings").get(1);
    assertThat(finding.get("demotion").isNull()).isTrue();
    assertThat(finding.get("basis").get("ref").asString()).isEqualTo("docs/specs/x/design.md");
    JsonNode inline = tree(complete()).get("findings").get(0);
    assertThat(inline.get("line").intValue()).isEqualTo(42);
    assertThat(inline.get("suggestion").isNull()).isTrue();
    assertThat(inline.get("basis").get("ref").isNull()).isTrue();
    assertThat(inline.get("summaryOnlyReason").isNull()).isTrue();
    assertThat(finding.get("line").isNull()).isTrue();
    assertThat(tree(complete()).get("incompleteDetails").get("fileCountGap").isNull()).isTrue();
  }

  // 10.4
  @Test
  void writesEmptyListsAsEmptyArrays() {
    ObjectNode root = tree(incomplete());
    assertThat(root.get("findings").isArray()).isTrue();
    assertThat(root.get("findings")).isEmpty();
    assertThat(root.get("excludedFiles")).isEmpty();
    assertThat(root.get("excludedFileDetails")).isEmpty();
    assertThat(root.get("incompleteDetails").get("chunks").get(1).get("files").isArray()).isTrue();
    assertThat(tree(complete()).get("incompleteReasons").isArray()).isTrue();
    assertThat(tree(complete()).get("incompleteReasons")).isEmpty();
  }

  // 10.5
  @Test
  void readsWhatItWrote() {
    assertThat(codec.read(codec.write(complete()))).isEqualTo(complete());
    assertThat(codec.read(codec.write(incomplete()))).isEqualTo(incomplete());
  }

  // 10.6
  @Test
  void ignoresUnknownFields() {
    ObjectNode root = tree(complete());
    root.put("extra", "x");
    ((ObjectNode) root.get("findings").get(0)).putObject("nested").put("a", 1);
    ((ObjectNode) root.get("usage")).put("unknown", true);
    assertThat(readTree(root)).isEqualTo(complete());
  }

  // 10.7
  @Test
  void reportsPathForMissingRequiredField() {
    ObjectNode root = tree(complete());
    ((ObjectNode) root.get("findings").get(1)).remove("severity");

    assertThatThrownBy(() -> readTree(root))
        .isInstanceOfSatisfying(
            CodecException.class,
            e -> {
              assertThat(e.path()).isEqualTo("findings[1].severity");
              assertThat(e.getMessage()).contains("findings[1].severity");
            });
  }

  // 10.7
  @Test
  void reportsPathAndAllowedValuesForBadEnum() {
    ObjectNode root = tree(complete());
    ((ObjectNode) root.get("findings").get(0)).put("severity", "BLOCKER");

    assertThatThrownBy(() -> readTree(root))
        .isInstanceOfSatisfying(
            CodecException.class,
            e -> {
              assertThat(e.path()).isEqualTo("findings[0].severity");
              assertThat(e.allowed()).containsExactly("blocker", "major", "minor", "nit");
              assertThat(e.getMessage()).contains("blocker", "nit");
            });
  }

  // 10.8
  @Test
  void reportsLineAndColumnForSyntaxError() {
    assertThatThrownBy(() -> codec.read("{\n  \"status\": \n  ]"))
        .isInstanceOfSatisfying(
            CodecException.class,
            e -> {
              assertThat(e.line()).isEqualTo(3);
              assertThat(e.column()).isPositive();
              assertThat(e.getMessage()).contains("3");
            });
  }

  // 10.9
  @Test
  void reportsPathForNegativeTokens() {
    ObjectNode root = tree(complete());
    ((ObjectNode) root.get("usage")).put("cacheWriteTokens", -1);

    assertThatThrownBy(() -> readTree(root))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("usage.cacheWriteTokens"));
  }

  // 10.11
  @Test
  void preservesHangulQuotesBackslashNewlineTabEmoji() {
    String text = "한글 \"따옴표\" \\ 역슬래시\n줄바꿈\r\n탭\t이모지 😀👍🏽";
    ReviewResult base = complete();
    ReviewResult result =
        new ReviewResult(
            base.status(),
            text,
            List.of(
                new Finding(
                    "경로/한글.java",
                    1,
                    Severity.NIT,
                    Category.TEST,
                    text,
                    text,
                    new Basis(BasisType.GENERAL, null),
                    null,
                    LineVerdict.INLINE_ELIGIBLE,
                    null)),
            base.excludedFiles(),
            base.excludedFileDetails(),
            base.usage(),
            base.incompleteReasons(),
            base.incompleteDetails(),
            base.stats());

    ReviewResult read = codec.read(codec.write(result));
    assertThat(read.summary().codePoints().toArray()).isEqualTo(text.codePoints().toArray());
    assertThat(read).isEqualTo(result);
  }

  // 11.7, 12.9, 16.4
  @Test
  void roundTripsDemotionVerdictAndIncompleteDetails() {
    ReviewResult complete = codec.read(codec.write(complete()));
    assertThat(complete.findings().get(0).demotion())
        .isEqualTo(new Demotion(BasisType.RULE, ".claude/rules/backend/nope.md"));
    assertThat(complete.findings().get(1).summaryOnlyReason())
        .isEqualTo(SummaryOnlyReason.LINE_MISSING);
    ReviewResult incomplete = codec.read(codec.write(incomplete()));
    assertThat(incomplete.incompleteReasons())
        .containsExactly(IncompleteReason.CHUNK_FAILED, IncompleteReason.FILES_TRUNCATED);
    assertThat(incomplete.incompleteDetails().fileCountGap())
        .isEqualTo(new FileCountGap(3100, 3000));
    assertThat(incomplete.incompleteDetails().chunks().get(1).rawResponseExcerpt())
        .isEqualTo("{\"bad\"");
  }

  @Test
  void readsTokensAsLong() {
    ReviewResult base = complete();
    ReviewResult result =
        new ReviewResult(
            base.status(),
            base.summary(),
            base.findings(),
            base.excludedFiles(),
            base.excludedFileDetails(),
            new Usage(Long.MAX_VALUE, Long.MAX_VALUE, 0, 1L << 40, "m", null),
            base.incompleteReasons(),
            base.incompleteDetails(),
            base.stats());
    assertThat(codec.read(codec.write(result)).usage()).isEqualTo(result.usage());
  }

  @Test
  void readsCostWithoutDoublePrecisionLoss() {
    BigDecimal cost = new BigDecimal("12345678901234.5678");
    ReviewResult base = complete();
    ReviewResult result =
        new ReviewResult(
            base.status(),
            base.summary(),
            base.findings(),
            base.excludedFiles(),
            base.excludedFileDetails(),
            new Usage(1, 1, 1, 1, "m", cost),
            base.incompleteReasons(),
            base.incompleteDetails(),
            base.stats());
    String json = codec.write(result);
    assertThat(json).contains("12345678901234.5678");
    assertThat(codec.read(json).usage().estimatedCostUsd()).isEqualTo(cost);
  }

  @Test
  void readsCostScaleNormalizedToFourDigits() {
    ObjectNode root = tree(complete());
    ((ObjectNode) root.get("usage")).put("estimatedCostUsd", new BigDecimal("0.1"));
    assertThat(readTree(root).usage().estimatedCostUsd()).isEqualTo(new BigDecimal("0.1000"));
  }

  @Test
  void rejectsInvariantViolationsWithCodecException() {
    ObjectNode statusMismatch = tree(complete());
    statusMismatch.put("status", "incomplete");
    assertThatThrownBy(() -> readTree(statusMismatch))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("status"));

    ObjectNode verdictMismatch = tree(complete());
    ((ObjectNode) verdictMismatch.get("findings").get(0)).put("summaryOnlyReason", "out_of_range");
    assertThatThrownBy(() -> readTree(verdictMismatch))
        .isInstanceOfSatisfying(
            CodecException.class,
            e -> assertThat(e.path()).isEqualTo("findings[0].summaryOnlyReason"));

    ObjectNode excludedMismatch = tree(complete());
    ((ArrayNode) excludedMismatch.get("excludedFiles")).removeAll();
    assertThatThrownBy(() -> readTree(excludedMismatch))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("excludedFiles"));

    ObjectNode nullRequired = tree(complete());
    nullRequired.putNull("summary");
    assertThatThrownBy(() -> readTree(nullRequired))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("summary"));
  }

  // 미정(15.1): 판정과 line이 어긋난 JSON은 거부한다
  @Test
  void rejectsInlineEligibleWithoutLine() {
    ObjectNode root = tree(complete());
    ((ObjectNode) root.get("findings").get(0)).putNull("line");
    assertThatThrownBy(() -> readTree(root))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("findings[0].line"));
  }

  @Test
  void rejectsLineMissingWithLine() {
    ObjectNode root = tree(complete());
    ((ObjectNode) root.get("findings").get(1)).put("line", 7);
    assertThatThrownBy(() -> readTree(root))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("findings[1].line"));
  }

  // 제안(15.1): schemaVersion이 1이 아니면 거부한다
  @Test
  void rejectsUnsupportedSchemaVersion() {
    ObjectNode root = tree(complete());
    root.put("schemaVersion", 2);
    assertThatThrownBy(() -> readTree(root))
        .isInstanceOfSatisfying(
            CodecException.class, e -> assertThat(e.path()).isEqualTo("schemaVersion"));
  }
}
