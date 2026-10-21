package com.prlens.testkit;

import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/**
 * PR 본문 생성기 (design.md Property 17). 본문과 함께 모델 기대값을 낸다. 저장소는 항상 {@code o/r}이다. ref 구간에 {@code
 * docs}·{@code specs}는 쓰지 않는다(ref 안의 {@code docs/specs} 구간은 URL만으로 구별할 수 없어 다루지 않는다).
 */
public final class SpecLinkArbitraries {

  /** 한 조각의 본문과 기대값. */
  private record Fragment(String text, List<String> paths, int otherRepo, int outside) {}

  /**
   * @param body 조각을 공백 또는 줄바꿈으로 이은 본문
   * @param expectedPaths 유효 경로를 첫 등장 순으로 중복 제거한 전체 목록(20개 상한 적용 전)
   * @param otherRepo 다른 저장소 URL 조각 수
   * @param outside {@code ..}를 넣은 조각 수
   */
  public record SpecBody(String body, List<String> expectedPaths, int otherRepo, int outside) {}

  private static final String[] NAMES = {"a", "b", "c", "d1", "가", "x.md", "y", "Z"};
  private static final String[] REF_PARTS = {"main", "feat", "x", "1", "rc", "v2", "release"};

  private SpecLinkArbitraries() {}

  public static Arbitrary<SpecBody> bodies() {
    Arbitrary<String> separator = Arbitraries.of(" ", "\n", " ");
    return Combinators.combine(fragment().list().ofMinSize(0).ofMaxSize(30), separator)
        .as(SpecLinkArbitraries::assemble);
  }

  private static SpecBody assemble(List<Fragment> fragments, String separator) {
    List<String> texts = new ArrayList<>();
    List<String> distinct = new ArrayList<>();
    int other = 0;
    int outside = 0;
    for (Fragment fragment : fragments) {
      texts.add(fragment.text());
      for (String path : fragment.paths()) {
        if (!distinct.contains(path)) {
          distinct.add(path);
        }
      }
      other += fragment.otherRepo();
      outside += fragment.outside();
    }
    return new SpecBody(String.join(separator, texts), List.copyOf(distinct), other, outside);
  }

  private static Arbitrary<Fragment> fragment() {
    Arbitrary<Fragment> pair =
        Combinators.combine(linkable(), linkable())
            .as(
                (first, second) -> {
                  List<String> paths = new ArrayList<>(first.paths());
                  paths.addAll(second.paths());
                  return new Fragment(
                      "[a](" + first.text() + ")[b](" + second.text() + ")",
                      paths,
                      first.otherRepo() + second.otherRepo(),
                      first.outside() + second.outside());
                });
    Arbitrary<Fragment> word =
        Arbitraries.of("hello", "한글", "mydocs/specs/x.md", "docs/specsx/z.md", "--")
            .map(text -> new Fragment(text, List.of(), 0, 0));
    return Arbitraries.frequencyOf(Tuple.of(10, linkable()), Tuple.of(2, pair), Tuple.of(2, word));
  }

  private static Arbitrary<Fragment> linkable() {
    return Arbitraries.frequencyOf(
        Tuple.of(4, relative(false)),
        Tuple.of(4, url(true, false)),
        Tuple.of(2, url(false, false)),
        Tuple.of(1, relative(true)),
        Tuple.of(1, url(true, true)));
  }

  private static Arbitrary<String> segments(int min, int max) {
    return Arbitraries.of(NAMES).list().ofMinSize(min).ofMaxSize(max).map(l -> String.join("/", l));
  }

  private static Arbitrary<String> suffix() {
    return Arbitraries.of("", "", "#anchor", "?plain=1", "?a=1#L3");
  }

  /** {@code dotdot}이면 경로 중간에 {@code ..} 구간을 넣는다. */
  private static Arbitrary<String> specPath(boolean dotdot) {
    if (!dotdot) {
      return segments(1, 3).map(rest -> "docs/specs/" + rest);
    }
    return Combinators.combine(segments(1, 2), segments(1, 2))
        .as((head, tail) -> "docs/specs/" + head + "/../" + tail);
  }

  private static Arbitrary<Fragment> relative(boolean dotdot) {
    return Combinators.combine(Arbitraries.of("", "./", "/"), specPath(dotdot), suffix())
        .as(
            (prefix, path, suffix) ->
                new Fragment(
                    prefix + path + suffix, dotdot ? List.of() : List.of(path), 0, dotdot ? 1 : 0));
  }

  private static Arbitrary<String> ref() {
    return Arbitraries.of(REF_PARTS).list().ofMinSize(1).ofMaxSize(3).map(l -> String.join("/", l));
  }

  private static Arbitrary<String> caseVariant(String value) {
    return Arbitraries.of(true, false)
        .list()
        .ofSize(value.length())
        .map(
            flags -> {
              StringBuilder out = new StringBuilder();
              for (int i = 0; i < value.length(); i++) {
                char c = value.charAt(i);
                out.append(flags.get(i) ? Character.toUpperCase(c) : c);
              }
              return out.toString();
            });
  }

  private static Arbitrary<Fragment> url(boolean sameRepo, boolean dotdot) {
    Arbitrary<String> owner = sameRepo ? caseVariant("o") : Arbitraries.of("other", "Other");
    Arbitrary<String> name = sameRepo ? caseVariant("r") : Arbitraries.of("r", "x");
    return Combinators.combine(owner, name, ref(), specPath(dotdot), suffix())
        .as(
            (o, r, ref, path, suffix) -> {
              String text =
                  "https://github.com/" + o + "/" + r + "/blob/" + ref + "/" + path + suffix;
              if (!sameRepo) {
                return new Fragment(text, List.of(), 1, 0);
              }
              return new Fragment(text, dotdot ? List.of() : List.of(path), 0, dotdot ? 1 : 0);
            });
  }
}
