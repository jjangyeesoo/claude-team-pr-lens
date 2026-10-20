package com.prlens.testkit;

import com.prlens.diff.DiffParser;
import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.ChangedFile;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ReviewContext;
import com.prlens.model.Revision;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 골든 픽스처({@code src/test/resources/fixtures/<name>/})를 읽는다 (테스트 전용).
 *
 * <p>{@code snapshot.json}의 {@code patch}는 GitHub 응답과 같은 unified diff 문자열이고 {@code null}이면 patch가
 * 없는 파일이다. {@code llm-responses/*.json}은 {@code {stopReason, text, usage}}이며 파일 이름 순서가 호출 순서다.
 * {@code text}가 객체면 압축 JSON 문자열로 바꾼다. enum 값은 소문자 snake_case다.
 */
public final class FixtureLoader {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private FixtureLoader() {}

  /** 한 픽스처의 입력과 기대값. */
  public record Fixture(
      String name,
      PullRequestSnapshot snapshot,
      ReviewContext context,
      List<LlmResponse> llmResponses,
      String expectedResultJson,
      String expectedMarkdown) {}

  /** 픽스처 폴더 이름을 정렬해서 돌려준다. */
  public static List<String> names() {
    try (Stream<Path> dirs = Files.list(root())) {
      return dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString()).sorted().toList();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  public static Fixture load(String name) {
    Path dir = root().resolve(name);
    return new Fixture(
        name,
        snapshot(MAPPER.readTree(read(dir.resolve("snapshot.json")))),
        context(MAPPER.readTree(read(dir.resolve("context.json")))),
        llmResponses(dir.resolve("llm-responses")),
        read(dir.resolve("expected-result.json")),
        read(dir.resolve("expected.md")));
  }

  /** 픽스처 폴더. 기대 파일을 새로 만드는 도구가 쓴다. */
  public static Path directory(String name) {
    return root().resolve(name);
  }

  private static Path root() {
    try {
      return Path.of(FixtureLoader.class.getResource("/fixtures").toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static String read(Path file) {
    try {
      return Files.readString(file, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static PullRequestSnapshot snapshot(JsonNode node) {
    List<ChangedFile> files = new ArrayList<>();
    for (JsonNode file : node.get("files")) {
      files.add(changedFile(file));
    }
    return new PullRequestSnapshot(
        new RepoRef(
            node.get("repo").get("owner").stringValue(),
            node.get("repo").get("name").stringValue()),
        node.get("number").intValue(),
        node.get("title").stringValue(),
        text(node.get("body")),
        node.get("baseSha").stringValue(),
        node.get("headSha").stringValue(),
        node.get("reportedChangedFiles").intValue(),
        files);
  }

  private static ChangedFile changedFile(JsonNode node) {
    String path = node.get("path").stringValue();
    JsonNode patch = node.get("patch");
    PatchContent content =
        patch == null || patch.isNull()
            ? new PatchContent.Absent()
            : new PatchContent.Parsed(DiffParser.parse(path, patch.stringValue()));
    return new ChangedFile(
        path,
        text(node.get("previousPath")),
        FileStatus.valueOf(upper(node.get("status"))),
        node.get("additions").intValue(),
        node.get("deletions").intValue(),
        content);
  }

  private static ReviewContext context(JsonNode node) {
    List<ContextFile> files = new ArrayList<>();
    for (JsonNode file : node.get("files")) {
      files.add(
          new ContextFile(
              file.get("path").stringValue(),
              file.get("content").stringValue(),
              ContextSource.valueOf(upper(file.get("source"))),
              Revision.valueOf(upper(file.get("revision")))));
    }
    return new ReviewContext(files);
  }

  private static List<LlmResponse> llmResponses(Path dir) {
    List<LlmResponse> responses = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
        JsonNode node = MAPPER.readTree(read(file));
        JsonNode text = node.get("text");
        JsonNode usage = node.get("usage");
        responses.add(
            new LlmResponse(
                node.get("stopReason").stringValue(),
                text.isString() ? text.stringValue() : MAPPER.writeValueAsString(text),
                new LlmUsage(
                    usage.get("inputTokens").longValue(),
                    usage.get("outputTokens").longValue(),
                    usage.get("cacheWriteTokens").longValue(),
                    usage.get("cacheReadTokens").longValue())));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return responses;
  }

  private static String text(JsonNode node) {
    return node == null || node.isNull() ? null : node.stringValue();
  }

  private static String upper(JsonNode node) {
    return node.stringValue().toUpperCase(Locale.ROOT);
  }
}
