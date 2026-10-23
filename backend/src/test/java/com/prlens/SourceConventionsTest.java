package com.prlens;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** ArchUnit이 문자열 리터럴을 보지 못해 소스 파일을 읽어 검사하는 규칙. */
class SourceConventionsTest {

  private static final Path MAIN_SOURCES = Path.of("src/main/java");
  private static final Path TEST_SOURCES = Path.of("src/test/java");

  // "%%"는 리터럴 %이므로 짝수 개의 % 뒤에 오는 %n만 줄바꿈 지정자다.
  private static final Pattern PERCENT_N = Pattern.compile("(?<!%)(?:%%)*%n");

  // BOM, 줄·문단 구분자, NBSP, 폭 없는 공백. 화면에서 보이지 않아 리뷰에서 놓친다.
  private static final Pattern INVISIBLE = Pattern.compile("[\\uFEFF\\u2028\\u2029\\u00A0\\u200B]");

  @Test
  void mainSourcesDoNotUsePercentN() throws IOException {
    List<String> violations = new ArrayList<>();
    List<Path> sources = javaSources(MAIN_SOURCES);
    for (Path source : sources) {
      for (int line : linesWithPercentN(Files.readString(source, StandardCharsets.UTF_8))) {
        violations.add(source + ":" + line);
      }
    }

    assertThat(sources).isNotEmpty();
    assertThat(violations).as("줄바꿈 지정자 대신 \\n을 직접 붙인다 (요구사항 22.5)").isEmpty();
  }

  @Test
  void sourcesDoNotContainRawInvisibleCharacters() throws IOException {
    List<String> violations = new ArrayList<>();
    List<Path> sources = new ArrayList<>(javaSources(MAIN_SOURCES));
    sources.addAll(javaSources(TEST_SOURCES));
    for (Path source : sources) {
      String text = Files.readString(source, StandardCharsets.UTF_8);
      for (int line : linesMatching(INVISIBLE, text)) {
        violations.add(source + ":" + line);
      }
    }

    assertThat(violations).as("보이지 않는 문자는 \\uFEFF처럼 이스케이프로 쓴다").isEmpty();
  }

  @Test
  void invisiblePatternFindsEachCharacterButNotEscapes() {
    String source = "a\n\uFEFF\nb\u2028\nc\u00A0\nd\u200B\ne\u2029\n\"\\uFEFF\"";

    assertThat(linesMatching(INVISIBLE, source)).containsExactly(2, 3, 4, 5, 6);
  }

  @Test
  void linesWithPercentNReportsOneBasedLineNumbers() {
    String source =
        "class A {\n  String a = \"x%n\";\n  String b = \"y\";\n  String c = \"%d%n\";\n}";

    assertThat(linesWithPercentN(source)).containsExactly(2, 4);
  }

  @Test
  void linesWithPercentNIgnoresEscapedPercentFollowedByN() {
    assertThat(linesWithPercentN("String a = \"100%%n\";")).isEmpty();
  }

  @Test
  void linesWithPercentNFindsPercentNAfterEscapedPercent() {
    assertThat(linesWithPercentN("String a = \"100%%%n\";")).containsExactly(1);
  }

  @Test
  void linesWithPercentNHandlesCrlfSources() {
    assertThat(linesWithPercentN("a\r\n\"%n\"\r\n")).containsExactly(2);
  }

  static List<Integer> linesWithPercentN(String source) {
    return linesMatching(PERCENT_N, source);
  }

  private static List<Integer> linesMatching(Pattern pattern, String source) {
    List<Integer> found = new ArrayList<>();
    String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (pattern.matcher(lines[i]).find()) {
        found.add(i + 1);
      }
    }
    return found;
  }

  private static List<Path> javaSources(Path root) throws IOException {
    try (Stream<Path> files = Files.walk(root)) {
      return files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
    }
  }
}
