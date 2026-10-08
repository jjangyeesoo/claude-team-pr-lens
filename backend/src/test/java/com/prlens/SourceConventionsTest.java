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

  // "%%"는 리터럴 %이므로 짝수 개의 % 뒤에 오는 %n만 줄바꿈 지정자다.
  private static final Pattern PERCENT_N = Pattern.compile("(?<!%)(?:%%)*%n");

  @Test
  void mainSourcesDoNotUsePercentN() throws IOException {
    List<String> violations = new ArrayList<>();
    List<Path> sources;
    try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
      sources = files.filter(p -> p.toString().endsWith(".java")).sorted().toList();
    }
    for (Path source : sources) {
      for (int line : linesWithPercentN(Files.readString(source, StandardCharsets.UTF_8))) {
        violations.add(source + ":" + line);
      }
    }

    assertThat(sources).isNotEmpty();
    assertThat(violations).as("줄바꿈 지정자 대신 \\n을 직접 붙인다 (요구사항 22.5)").isEmpty();
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
    List<Integer> found = new ArrayList<>();
    String[] lines = source.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (PERCENT_N.matcher(lines[i]).find()) {
        found.add(i + 1);
      }
    }
    return found;
  }
}
