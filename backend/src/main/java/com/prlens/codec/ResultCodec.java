package com.prlens.codec;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.util.DefaultIndenter;
import tools.jackson.core.util.DefaultPrettyPrinter;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * ReviewResult와 JSON 문자열을 서로 바꾼다 (요구사항 10).
 *
 * <p>Jackson 트리 모델로 직접 매핑한다. 필드 경로(예: {@code findings[2].severity})를 직접 만들고, 모르는 필드는 읽지 않는다. 선택 필드는
 * 키를 유지하고 값만 null로 쓴다. 읽을 때는 선택 필드의 키가 없어도 null로 받는다.
 */
public final class ResultCodec {

  private static final int SCHEMA_VERSION = 1;
  private static final String ROOT = "$";

  private static final JsonMapper MAPPER =
      JsonMapper.builder()
          .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
          .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
          .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
          .build();

  private static final ObjectWriter WRITER =
      MAPPER
          .writer()
          .with(
              new DefaultPrettyPrinter()
                  .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                  .withArrayIndenter(new DefaultIndenter("  ", "\n")));

  public String write(ReviewResult result) {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("schemaVersion", SCHEMA_VERSION);
    root.put("status", name(result.status()));
    ArrayNode reasons = root.putArray("incompleteReasons");
    result.incompleteReasons().forEach(reason -> reasons.add(name(reason)));
    writeDetails(root.putObject("incompleteDetails"), result.incompleteDetails());
    ObjectNode stats = root.putObject("stats");
    stats.put("mode", name(result.stats().mode()));
    stats.put("changedLineCount", result.stats().changedLineCount());
    stats.put("sizeLimit", result.stats().sizeLimit());
    stats.put("chunkCount", result.stats().chunkCount());
    root.put("summary", result.summary());
    ArrayNode findings = root.putArray("findings");
    result.findings().forEach(finding -> writeFinding(findings.addObject(), finding));
    ArrayNode excluded = root.putArray("excludedFiles");
    result.excludedFiles().forEach(excluded::add);
    ArrayNode details = root.putArray("excludedFileDetails");
    for (ExcludedFile file : result.excludedFileDetails()) {
      details.addObject().put("path", file.path()).put("reason", file.reason());
    }
    Usage usage = result.usage();
    ObjectNode usageNode = root.putObject("usage");
    usageNode.put("inputTokens", usage.inputTokens());
    usageNode.put("outputTokens", usage.outputTokens());
    usageNode.put("cacheWriteTokens", usage.cacheWriteTokens());
    usageNode.put("cacheReadTokens", usage.cacheReadTokens());
    usageNode.put("model", usage.model());
    usageNode.put("estimatedCostUsd", usage.estimatedCostUsd());
    return WRITER.writeValueAsString(root);
  }

  private static void writeDetails(ObjectNode node, IncompleteDetails details) {
    ArrayNode chunks = node.putArray("chunks");
    for (ChunkIssue chunk : details.chunks()) {
      ObjectNode item = chunks.addObject();
      item.put("chunkIndex", chunk.chunkIndex());
      ArrayNode files = item.putArray("files");
      chunk.files().forEach(files::add);
      item.put("reason", name(chunk.reason()));
      item.put("lastStatusCode", chunk.lastStatusCode());
      item.put("rawResponseExcerpt", chunk.rawResponseExcerpt());
    }
    if (details.fileCountGap() == null) {
      node.putNull("fileCountGap");
    } else {
      node.putObject("fileCountGap")
          .put("reported", details.fileCountGap().reported())
          .put("received", details.fileCountGap().received());
    }
  }

  private static void writeFinding(ObjectNode node, Finding finding) {
    node.put("file", finding.file());
    node.put("line", finding.line());
    node.put("severity", name(finding.severity()));
    node.put("category", name(finding.category()));
    node.put("message", finding.message());
    node.put("suggestion", finding.suggestion());
    node.putObject("basis")
        .put("type", name(finding.basis().type()))
        .put("ref", finding.basis().ref());
    if (finding.demotion() == null) {
      node.putNull("demotion");
    } else {
      node.putObject("demotion")
          .put("originalType", name(finding.demotion().originalType()))
          .put("originalRef", finding.demotion().originalRef());
    }
    node.put("verdict", name(finding.verdict()));
    node.put(
        "summaryOnlyReason",
        finding.summaryOnlyReason() == null ? null : name(finding.summaryOnlyReason()));
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  public ReviewResult read(String json) {
    JsonNode root = parse(json);
    requireObject(root, ROOT);
    int version = intField(root, "schemaVersion", "schemaVersion");
    if (version != SCHEMA_VERSION) {
      throw fail("schemaVersion", "지원하는 값은 " + SCHEMA_VERSION + "입니다: " + version);
    }
    ResultStatus status = enumField(root, "status", "status", ResultStatus.class);
    List<IncompleteReason> reasons =
        listField(
            root,
            "incompleteReasons",
            "incompleteReasons",
            (node, path) -> enumValue(node, path, IncompleteReason.class));
    IncompleteDetails details = readDetails(object(root, "incompleteDetails", "incompleteDetails"));
    ReviewStats stats = readStats(object(root, "stats", "stats"));
    String summary = stringField(root, "summary", "summary");
    List<Finding> findings = listField(root, "findings", "findings", ResultCodec::readFinding);
    List<String> excludedFiles =
        listField(root, "excludedFiles", "excludedFiles", ResultCodec::stringValue);
    List<ExcludedFile> excludedDetails =
        listField(root, "excludedFileDetails", "excludedFileDetails", ResultCodec::readExcluded);
    Usage usage = readUsage(object(root, "usage", "usage"));

    if ((status == ResultStatus.COMPLETE) != reasons.isEmpty()) {
      throw fail("status", "status와 incompleteReasons가 맞지 않습니다: " + status + ", " + reasons);
    }
    if (!excludedFiles.equals(excludedDetails.stream().map(ExcludedFile::path).toList())) {
      throw fail("excludedFiles", "excludedFileDetails의 path 목록과 다릅니다");
    }
    try {
      return new ReviewResult(
          status,
          summary,
          findings,
          excludedFiles,
          excludedDetails,
          usage,
          reasons,
          details,
          stats);
    } catch (IllegalArgumentException | NullPointerException e) {
      throw fail(ROOT, e.getMessage());
    }
  }

  private static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (JacksonException e) {
      TokenStreamLocation location = e.getLocation();
      if (location == null) {
        throw new CodecException(
            "JSON을 읽을 수 없습니다: " + e.getOriginalMessage(), null, List.of(), null, null);
      }
      int line = location.getLineNr();
      int column = location.getColumnNr();
      String message = "JSON 문법 오류 (줄 " + line + ", 열 " + column + "): " + e.getOriginalMessage();
      throw new CodecException(message, null, List.of(), line, column);
    }
  }

  private static IncompleteDetails readDetails(JsonNode node) {
    List<ChunkIssue> chunks =
        listField(node, "chunks", "incompleteDetails.chunks", ResultCodec::readChunk);
    FileCountGap gap = null;
    JsonNode gapNode = node.get("fileCountGap");
    if (gapNode != null && !gapNode.isNull()) {
      String path = "incompleteDetails.fileCountGap";
      requireObject(gapNode, path);
      gap =
          new FileCountGap(
              intField(gapNode, "reported", path + ".reported"),
              intField(gapNode, "received", path + ".received"));
    }
    return new IncompleteDetails(chunks, gap);
  }

  private static ChunkIssue readChunk(JsonNode node, String path) {
    requireObject(node, path);
    return new ChunkIssue(
        intField(node, "chunkIndex", path + ".chunkIndex"),
        listField(node, "files", path + ".files", ResultCodec::stringValue),
        enumField(node, "reason", path + ".reason", IncompleteReason.class),
        optionalInt(node, "lastStatusCode", path + ".lastStatusCode"),
        optionalString(node, "rawResponseExcerpt", path + ".rawResponseExcerpt"));
  }

  private static ReviewStats readStats(JsonNode node) {
    return new ReviewStats(
        enumField(node, "mode", "stats.mode", ReviewMode.class),
        intField(node, "changedLineCount", "stats.changedLineCount"),
        intField(node, "sizeLimit", "stats.sizeLimit"),
        intField(node, "chunkCount", "stats.chunkCount"));
  }

  private static Finding readFinding(JsonNode node, String path) {
    requireObject(node, path);
    Integer line = optionalInt(node, "line", path + ".line");
    LineVerdict verdict = enumField(node, "verdict", path + ".verdict", LineVerdict.class);
    SummaryOnlyReason reason =
        optionalEnum(node, "summaryOnlyReason", path, SummaryOnlyReason.class);
    if ((verdict == LineVerdict.SUMMARY_ONLY) != (reason != null)) {
      throw fail(path + ".summaryOnlyReason", "verdict와 맞지 않습니다: " + verdict + ", " + reason);
    }
    if (verdict == LineVerdict.INLINE_ELIGIBLE && line == null) {
      throw fail(path + ".line", "inline_eligible이면 line이 있어야 합니다");
    }
    if (reason == SummaryOnlyReason.LINE_MISSING && line != null) {
      throw fail(path + ".line", "line_missing이면 line이 null이어야 합니다");
    }
    JsonNode basisNode = object(node, "basis", path + ".basis");
    Basis basis =
        new Basis(
            enumField(basisNode, "type", path + ".basis.type", BasisType.class),
            optionalString(basisNode, "ref", path + ".basis.ref"));
    Demotion demotion = null;
    JsonNode demotionNode = node.get("demotion");
    if (demotionNode != null && !demotionNode.isNull()) {
      String demotionPath = path + ".demotion";
      requireObject(demotionNode, demotionPath);
      demotion =
          new Demotion(
              enumField(
                  demotionNode, "originalType", demotionPath + ".originalType", BasisType.class),
              optionalString(demotionNode, "originalRef", demotionPath + ".originalRef"));
    }
    return new Finding(
        stringField(node, "file", path + ".file"),
        line,
        enumField(node, "severity", path + ".severity", Severity.class),
        enumField(node, "category", path + ".category", Category.class),
        stringField(node, "message", path + ".message"),
        optionalString(node, "suggestion", path + ".suggestion"),
        basis,
        demotion,
        verdict,
        reason);
  }

  private static ExcludedFile readExcluded(JsonNode node, String path) {
    requireObject(node, path);
    return new ExcludedFile(
        stringField(node, "path", path + ".path"), stringField(node, "reason", path + ".reason"));
  }

  private static Usage readUsage(JsonNode node) {
    BigDecimal cost = null;
    JsonNode costNode = node.get("estimatedCostUsd");
    if (costNode != null && !costNode.isNull()) {
      if (!costNode.isNumber()) {
        throw fail("usage.estimatedCostUsd", "숫자여야 합니다");
      }
      cost = costNode.decimalValue();
      if (cost.signum() < 0) {
        throw fail("usage.estimatedCostUsd", "음수일 수 없습니다: " + cost);
      }
    }
    return new Usage(
        tokens(node, "inputTokens"),
        tokens(node, "outputTokens"),
        tokens(node, "cacheWriteTokens"),
        tokens(node, "cacheReadTokens"),
        stringField(node, "model", "usage.model"),
        cost);
  }

  private static long tokens(JsonNode usage, String key) {
    String path = "usage." + key;
    JsonNode value = usage.get(key);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong()) {
      throw fail(path, "정수여야 합니다");
    }
    if (value.longValue() < 0) {
      throw fail(path, "음수일 수 없습니다: " + value.longValue());
    }
    return value.longValue();
  }

  private static void requireObject(JsonNode node, String path) {
    if (node == null || !node.isObject()) {
      throw fail(path, "객체여야 합니다");
    }
  }

  private static JsonNode object(JsonNode parent, String key, String path) {
    JsonNode node = parent.get(key);
    requireObject(node, path);
    return node;
  }

  private static String stringField(JsonNode parent, String key, String path) {
    return stringValue(parent.get(key), path);
  }

  private static String stringValue(JsonNode node, String path) {
    if (node == null || !node.isString()) {
      throw fail(path, "문자열이어야 합니다");
    }
    return node.stringValue();
  }

  private static String optionalString(JsonNode parent, String key, String path) {
    JsonNode node = parent.get(key);
    return node == null || node.isNull() ? null : stringValue(node, path);
  }

  private static int intField(JsonNode parent, String key, String path) {
    JsonNode node = parent.get(key);
    if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
      throw fail(path, "정수여야 합니다");
    }
    return node.intValue();
  }

  private static Integer optionalInt(JsonNode parent, String key, String path) {
    JsonNode node = parent.get(key);
    return node == null || node.isNull() ? null : intField(parent, key, path);
  }

  private static <E extends Enum<E>> E enumField(
      JsonNode parent, String key, String path, Class<E> type) {
    return enumValue(parent.get(key), path, type);
  }

  private static <E extends Enum<E>> E optionalEnum(
      JsonNode parent, String key, String parentPath, Class<E> type) {
    JsonNode node = parent.get(key);
    return node == null || node.isNull() ? null : enumValue(node, parentPath + "." + key, type);
  }

  private static <E extends Enum<E>> E enumValue(JsonNode node, String path, Class<E> type) {
    List<String> allowed = Arrays.stream(type.getEnumConstants()).map(ResultCodec::name).toList();
    if (node != null && node.isString()) {
      for (E constant : type.getEnumConstants()) {
        if (name(constant).equals(node.stringValue())) {
          return constant;
        }
      }
    }
    String actual = node == null ? "없음" : node.toString();
    throw new CodecException(
        path + ": 허용되지 않은 값입니다 (" + actual + "). 허용 값: " + String.join(", ", allowed),
        path,
        allowed,
        null,
        null);
  }

  private static <T> List<T> listField(
      JsonNode parent, String key, String path, BiFunction<JsonNode, String, T> reader) {
    JsonNode node = parent.get(key);
    if (node == null || !node.isArray()) {
      throw fail(path, "배열이어야 합니다");
    }
    List<T> items = new ArrayList<>();
    for (int i = 0; i < node.size(); i++) {
      items.add(reader.apply(node.get(i), path + "[" + i + "]"));
    }
    return items;
  }

  private static CodecException fail(String path, String reason) {
    return new CodecException(path + ": " + reason, path, List.of(), null, null);
  }
}
