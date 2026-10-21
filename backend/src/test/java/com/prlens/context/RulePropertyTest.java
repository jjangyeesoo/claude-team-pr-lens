package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FrontmatterParser.Frontmatter;
import com.prlens.context.RuleSelector.RuleFile;
import com.prlens.context.RuleSelector.RuleSelection;
import com.prlens.glob.GlobMatcher;
import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * rule 선택과 프런트매터 해석의 속성 테스트 (design.md Property 14, Property 7의 프런트매터 부분). 제외 파일은 선택기에 넘기지 않는 파일로
 * 나타낸다. 무효 패턴과 프런트매터 오류는 예시 테스트({@code RuleSelectorTest}, {@code FrontmatterParserTest})가 다룬다.
 */
class RulePropertyTest {

  private static final PatchContent PARSED = new PatchContent.Parsed(List.of());

  /** 생성한 rule과 그 rule의 패턴. {@code patterns}가 비었으면 {@code paths}가 없다. */
  record GeneratedRule(RuleFile file, List<String> patterns) {}

  record Scenario(
      List<ChangedFile> targets, List<ChangedFile> excluded, List<GeneratedRule> rules) {}

  // Feature: pr-lens-p1-cli, Property 14: rule 선택
  @Property(tries = 200)
  void ruleSelection(@ForAll("scenarios") Scenario scenario) {
    List<RuleFile> files = scenario.rules().stream().map(GeneratedRule::file).toList();

    RuleSelection selection = RuleSelector.select(files, scenario.targets());

    List<RuleFile> expected = new ArrayList<>();
    for (GeneratedRule rule : scenario.rules()) {
      if (rule.patterns().isEmpty() || matchesAnyTarget(rule.patterns(), scenario.targets())) {
        expected.add(rule.file());
      }
    }
    assertThat(selection).isNotNull();
    assertThat(selection.selected()).doesNotHaveDuplicates().containsExactlyElementsOf(expected);
    assertThat(selection.warnings()).isEmpty();
  }

  // Feature: pr-lens-p1-cli, Property 7: 줄바꿈 무관 해석 (프런트매터 부분)
  @Property(tries = 200)
  void frontmatterLineEndingStyleDoesNotChangeResult(
      @ForAll("frontmatterTexts") String lf, @ForAll Random random) {
    Frontmatter expected = FrontmatterParser.parse(lf);

    StringBuilder mixed = new StringBuilder();
    for (char c : lf.toCharArray()) {
      if (c == '\n') {
        mixed.append(new String[] {"\n", "\r\n", "\r"}[random.nextInt(3)]);
      } else {
        mixed.append(c);
      }
    }

    assertThat(expected).isNotNull();
    assertThat(expected.problem()).isEmpty();
    assertThat(expected.paths()).isPresent();
    assertThat(expected.paths().get()).noneMatch(pattern -> pattern.indexOf('\r') >= 0);
    assertThat(FrontmatterParser.parse(lf.replace("\n", "\r\n"))).isEqualTo(expected);
    assertThat(FrontmatterParser.parse(lf.replace("\n", "\r"))).isEqualTo(expected);
    assertThat(FrontmatterParser.parse(mixed.toString())).isEqualTo(expected);
  }

  private static boolean matchesAnyTarget(List<String> patterns, List<ChangedFile> targets) {
    for (ChangedFile target : targets) {
      List<String> paths = new ArrayList<>(List.of(target.path()));
      if (target.status() == FileStatus.RENAMED) {
        paths.add(target.previousPath());
      }
      for (String pattern : patterns) {
        for (String path : paths) {
          if (GlobMatcher.matches(pattern, path)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<List<ChangedFile>> targets = changedFiles().list().ofMinSize(1).ofMaxSize(5);
    Arbitrary<List<ChangedFile>> excluded = changedFiles().list().ofMaxSize(3);
    Arbitrary<List<GeneratedRule>> rules =
        ruleShapes()
            .list()
            .ofMinSize(1)
            .ofMaxSize(5)
            .map(
                shapes -> {
                  List<GeneratedRule> generated = new ArrayList<>();
                  for (int i = 0; i < shapes.size(); i++) {
                    RuleShape shape = shapes.get(i);
                    generated.add(
                        new GeneratedRule(
                            new RuleFile(".claude/rules/r" + i + ".md", shape.content()),
                            shape.patterns()));
                  }
                  return generated;
                });
    return Combinators.combine(targets, excluded, rules).as(Scenario::new);
  }

  @Provide
  Arbitrary<String> frontmatterTexts() {
    Arbitrary<String> paths =
        Arbitraries.oneOf(
            patterns().map(p -> "paths: \"" + p + "\"\n"),
            patterns().list().ofMinSize(1).ofMaxSize(3).map(RulePropertyTest::blockList),
            patterns().list().ofMinSize(1).ofMaxSize(3).map(RulePropertyTest::flowList));
    Arbitrary<List<String>> otherKeys =
        Arbitraries.of("title: 규칙\n", "description: \"설명 두 단어\"\n", "tags: [a, b]\n")
            .list()
            .ofMaxSize(2);
    Arbitrary<List<String>> body = Arbitraries.of("# 제목", "본문 줄", "- 항목", "").list().ofMaxSize(3);
    return Combinators.combine(paths, otherKeys, body)
        .as(
            (p, keys, lines) -> {
              StringBuilder text = new StringBuilder("---\n");
              if (!keys.isEmpty()) {
                text.append(keys.get(0));
              }
              text.append(p);
              for (int i = 1; i < keys.size(); i++) {
                text.append(keys.get(i));
              }
              text.append("---\n");
              for (String line : lines) {
                text.append(line).append('\n');
              }
              return text.toString();
            });
  }

  /** rule 내용과 그 패턴. 형태는 프런트매터 없음, {@code paths} 없는 프런트매터, 문자열 {@code paths}, 목록 {@code paths}다. */
  record RuleShape(String content, List<String> patterns) {}

  private static Arbitrary<RuleShape> ruleShapes() {
    return Arbitraries.oneOf(
        Arbitraries.just(new RuleShape("# 규칙\n본문\n", List.of())),
        Arbitraries.just(new RuleShape("---\ntitle: 규칙\n---\n본문\n", List.of())),
        patterns().map(p -> new RuleShape("---\npaths: \"" + p + "\"\n---\n본문\n", List.of(p))),
        patterns()
            .list()
            .ofMinSize(1)
            .ofMaxSize(3)
            .map(ps -> new RuleShape("---\n" + blockList(ps) + "---\n본문\n", ps)));
  }

  private static String blockList(List<String> patterns) {
    StringBuilder text = new StringBuilder("paths:\n");
    for (String pattern : patterns) {
      text.append("  - \"").append(pattern).append("\"\n");
    }
    return text.toString();
  }

  private static String flowList(List<String> patterns) {
    List<String> quoted = patterns.stream().map(p -> "\"" + p + "\"").toList();
    return "paths: [" + String.join(", ", quoted) + "]\n";
  }

  /** 구간 {@code a}, {@code b}, {@code src}와 {@code *}, {@code **}, {@code ?}, 중괄호로 만든 유효한 패턴. */
  private static Arbitrary<String> patterns() {
    Arbitrary<String> segment =
        Arbitraries.of(
            "a", "b", "src", "*", "**", "*.java", "*.md", "?.md", "{a,b}", "{a,src}.java", "s?c");
    return segment.list().ofMinSize(1).ofMaxSize(3).map(parts -> String.join("/", parts));
  }

  private static Arbitrary<ChangedFile> changedFiles() {
    Arbitrary<String> kind = Arbitraries.of("MODIFIED", "ADDED", "REMOVED", "RENAMED", "COPIED");
    return Combinators.combine(kind, filePaths(), filePaths())
        .as(
            (k, path, previous) ->
                switch (k) {
                  case "RENAMED" ->
                      new ChangedFile(path, previous, FileStatus.RENAMED, 0, 0, PARSED);
                  case "COPIED" -> new ChangedFile(path, previous, FileStatus.ADDED, 0, 0, PARSED);
                  default -> new ChangedFile(path, null, FileStatus.valueOf(k), 0, 0, PARSED);
                });
  }

  /** 폴더 {@code a}, {@code b}, {@code src}로 된 깊이 0~2와 확장자 {@code java}, {@code md}의 파일 경로. */
  private static Arbitrary<String> filePaths() {
    Arbitrary<List<String>> folders = Arbitraries.of("a", "b", "src").list().ofMaxSize(2);
    Arbitrary<String> name = Arbitraries.of("a", "b", "src").flatMap(RulePropertyTest::extension);
    return Combinators.combine(folders, name)
        .as(
            (segments, file) -> {
              List<String> parts = new ArrayList<>(segments);
              parts.add(file);
              return String.join("/", parts);
            });
  }

  private static Arbitrary<String> extension(String name) {
    return Arbitraries.of(name + ".java", name + ".md");
  }
}
