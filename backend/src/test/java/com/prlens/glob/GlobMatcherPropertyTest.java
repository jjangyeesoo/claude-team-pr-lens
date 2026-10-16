package com.prlens.glob;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** {@code GlobMatcher}의 중괄호 모델 기반 속성 테스트 (design.md Property 12). */
class GlobMatcherPropertyTest {

  /** 중괄호 트리. 리터럴에는 {@code **}가 생기지 않는다(어떤 리터럴도 {@code *}로 끝나지 않는다). */
  private interface Node {}

  private record Lit(String text) implements Node {}

  private record Group(List<List<Node>> alternatives) implements Node {}

  /**
   * @param expansions 트리에서 계산한 모델 펼침
   */
  private record Case(String pattern, String path, List<String> expansions) {}

  private static String render(List<Node> nodes) {
    StringBuilder sb = new StringBuilder();
    for (Node node : nodes) {
      if (node instanceof Lit lit) {
        sb.append(lit.text());
      } else if (node instanceof Group group) {
        sb.append('{');
        for (int i = 0; i < group.alternatives().size(); i++) {
          if (i > 0) {
            sb.append(',');
          }
          sb.append(render(group.alternatives().get(i)));
        }
        sb.append('}');
      }
    }
    return sb.toString();
  }

  /** 중괄호를 모두 펼친 패턴들 (모델). */
  private static List<String> expand(List<Node> nodes) {
    List<String> results = new ArrayList<>(List.of(""));
    for (Node node : nodes) {
      List<String> options = new ArrayList<>();
      if (node instanceof Lit lit) {
        options.add(lit.text());
      } else if (node instanceof Group group) {
        group.alternatives().forEach(alt -> options.addAll(expand(alt)));
      }
      List<String> next = new ArrayList<>();
      for (String prefix : results) {
        for (String option : options) {
          next.add(prefix + option);
        }
      }
      results = next;
    }
    return results;
  }

  private static Arbitrary<Node> literal() {
    return Arbitraries.of("a", "b", "ab", "c", "x", "?", "*.", "/", "d.e").map(Lit::new);
  }

  /** 앞에 {@code a}를 붙여 패턴이 {@code /}만으로 시작하지 않게 한다. 펼친 개수는 최대 64개다. */
  @Provide
  Arbitrary<Case> cases() {
    Arbitrary<Node> innerGroup =
        literal().list().ofMinSize(1).ofMaxSize(2).list().ofMinSize(2).ofMaxSize(2).map(Group::new);
    Arbitrary<Node> altNode = Arbitraries.oneOf(literal(), innerGroup);
    Arbitrary<Node> topGroup =
        altNode.list().ofMinSize(1).ofMaxSize(2).list().ofMinSize(1).ofMaxSize(2).map(Group::new);
    Arbitrary<List<Node>> pattern =
        Arbitraries.oneOf(literal(), topGroup)
            .list()
            .ofMinSize(1)
            .ofMaxSize(2)
            .map(
                parts -> {
                  List<Node> all = new ArrayList<>();
                  all.add(new Lit("a"));
                  all.addAll(parts);
                  return all;
                });
    Arbitrary<String> randomPath =
        Arbitraries.of("a", "b", "c", "x", ".", "d.e", "ab")
            .list()
            .ofMinSize(1)
            .ofMaxSize(4)
            .list()
            .ofMinSize(1)
            .ofMaxSize(3)
            .map(
                segments ->
                    String.join(
                        "/", segments.stream().map(parts -> String.join("", parts)).toList()));
    return Combinators.combine(
            pattern,
            randomPath,
            Arbitraries.integers().between(0, 1_000),
            Arbitraries.of("", "a", "bc", "x."),
            Arbitraries.of(true, false))
        .as(
            (nodes, random, pick, fill, fromModel) -> {
              List<String> expansions = expand(nodes);
              String path = random;
              if (fromModel) {
                String chosen = expansions.get(pick % expansions.size());
                path = chosen.replace("*", fill).replace("?", "q");
              }
              return new Case(render(nodes), path, expansions);
            });
  }

  // Feature: pr-lens-p1-cli, Property 12: glob 중괄호 모델 기반
  @Property(tries = 300)
  void braceMatchEqualsAnyExpansionMatch(@ForAll("cases") Case testCase) {
    List<String> expansions = expandPattern(testCase.pattern());
    assertThat(expansions).containsExactlyInAnyOrderElementsOf(testCase.expansions());

    boolean expected =
        expansions.stream().anyMatch(expanded -> GlobMatcher.matches(expanded, testCase.path()));

    assertThat(GlobMatcher.matches(testCase.pattern(), testCase.path())).isEqualTo(expected);
  }

  /** 렌더링한 패턴 문자열을 독립적으로 다시 펼친다 (트리가 아니라 문자열 기준 모델). */
  private static List<String> expandPattern(String pattern) {
    int open = pattern.indexOf('{');
    if (open < 0) {
      return List.of(pattern);
    }
    int depth = 0;
    int close = -1;
    for (int i = open; i < pattern.length(); i++) {
      if (pattern.charAt(i) == '{') {
        depth++;
      } else if (pattern.charAt(i) == '}' && --depth == 0) {
        close = i;
        break;
      }
    }
    List<String> inner = new ArrayList<>();
    int start = open + 1;
    depth = 0;
    for (int i = open + 1; i < close; i++) {
      char ch = pattern.charAt(i);
      if (ch == '{') {
        depth++;
      } else if (ch == '}') {
        depth--;
      } else if (ch == ',' && depth == 0) {
        inner.add(pattern.substring(start, i));
        start = i + 1;
      }
    }
    inner.add(pattern.substring(start, close));
    List<String> results = new ArrayList<>();
    for (String alternative : inner) {
      results.addAll(
          expandPattern(pattern.substring(0, open) + alternative + pattern.substring(close + 1)));
    }
    return results;
  }
}
