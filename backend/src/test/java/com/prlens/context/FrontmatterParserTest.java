package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FrontmatterParser.Frontmatter;
import com.prlens.context.FrontmatterParser.Kind;
import com.prlens.context.FrontmatterParser.Problem;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** {@link FrontmatterParser} (요구사항 5.2, 5.3, 5.6, 5.9, 5.10, 22.4). */
class FrontmatterParserTest {

  private static final Frontmatter NO_PATHS = new Frontmatter(Optional.empty(), Optional.empty());

  private static Frontmatter paths(String... patterns) {
    return new Frontmatter(Optional.of(List.of(patterns)), Optional.empty());
  }

  /** 문제가 있는 결과를 돌려준다. {@code paths}는 비어 있어야 한다. */
  private static Problem problemOf(String content) {
    Frontmatter result = FrontmatterParser.parse(content);
    assertThat(result).isNotNull();
    assertThat(result.paths()).isEmpty();
    assertThat(result.problem()).isPresent();
    return result.problem().get();
  }

  @Test
  void parsesListPathsBetweenDashLines() {
    String content = "---\npaths:\n  - \"src/**\"\n  - \"**/*.java\"\n---\n# 규칙\n본문\n";

    assertThat(FrontmatterParser.parse(content)).isEqualTo(paths("src/**", "**/*.java"));
  }

  @Test
  void singleStringPathsBecomesOneElementList() {
    assertThat(FrontmatterParser.parse("---\npaths: \"src/**\"\n---\n본문\n"))
        .isEqualTo(paths("src/**"));
    assertThat(FrontmatterParser.parse("---\ntitle: t\npaths: src/main\n---"))
        .isEqualTo(paths("src/main"));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "# t\n---\npaths: x\n---\n",
        " ---\npaths: x\n---\n",
        "--- \npaths: x\n---\n",
        "----\npaths: x\n---\n",
        "\uFEFF---\npaths: x\n---\n"
      })
  void frontmatterStartsOnlyWhenFirstLineIsExactlyDashes(String content) {
    assertThat(FrontmatterParser.parse(content)).isEqualTo(NO_PATHS);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"", "# 규칙\n본문\n", "---\ntitle: t\n---\n본문\n", "---\n---\n", "---\n\n---\n"})
  void frontmatterWithoutPathsOrEmptyHasNoPatterns(String content) {
    assertThat(FrontmatterParser.parse(content)).isEqualTo(NO_PATHS);
  }

  @ParameterizedTest
  @ValueSource(strings = {"---\npaths: a\n본문\n", "---", "---\n", "---\npaths: a\n --- \n"})
  void missingClosingDashesIsUnclosed(String content) {
    assertThat(problemOf(content).kind()).isEqualTo(Kind.UNCLOSED);
  }

  @ParameterizedTest
  @ValueSource(strings = {"---\npaths: [a\n---\n", "---\npaths: *.java\n---\n"})
  void yamlSyntaxErrorIsReportedOnOneLine(String content) {
    Problem problem = problemOf(content);

    assertThat(problem.kind()).isEqualTo(Kind.YAML_ERROR);
    assertThat(problem.detail()).isNotBlank().doesNotContain("\n").doesNotContain("\r");
  }

  @ParameterizedTest
  @ValueSource(strings = {"---\n- a\n- b\n---\n", "---\n그냥 글\n---\n"})
  void nonMappingFrontmatterIsYamlError(String content) {
    assertThat(problemOf(content).kind()).isEqualTo(Kind.YAML_ERROR);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "paths: []",
        "paths:",
        "paths: 1",
        "paths: true",
        "paths: [a, 1]",
        "paths: [a, [b]]",
        "paths: {a: b}"
      })
  void invalidPathsValuesAreReported(String line) {
    assertThat(problemOf("---\n" + line + "\n---\n").kind()).isEqualTo(Kind.INVALID_PATHS);
  }

  @Test
  void lastDuplicatePathsKeyWins() {
    assertThat(FrontmatterParser.parse("---\npaths: a\npaths: [b, c]\n---\n"))
        .isEqualTo(paths("b", "c"));
  }

  @Test
  void crlfAndMixedLineEndingsParseLikeLf() {
    String lf = "---\ntitle: t\npaths:\n  - \"src/**\"\n  - lib/*.java\n---\n# 규칙\n";
    Frontmatter expected = FrontmatterParser.parse(lf);

    assertThat(expected).isEqualTo(paths("src/**", "lib/*.java"));
    assertThat(FrontmatterParser.parse(lf.replace("\n", "\r\n"))).isEqualTo(expected);
    assertThat(FrontmatterParser.parse(lf.replace("\n", "\r"))).isEqualTo(expected);
    assertThat(
            FrontmatterParser.parse(
                "---\r\ntitle: t\rpaths:\n  - \"src/**\"\r\n  - lib/*.java\r---\n# 규칙\r\n"))
        .isEqualTo(expected);
  }
}
