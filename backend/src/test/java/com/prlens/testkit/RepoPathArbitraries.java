package com.prlens.testkit;

import java.util.List;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/** 저장소 경로 생성기 (design.md "속성 기반 테스트 규칙"의 "저장소 경로"). */
public final class RepoPathArbitraries {

  /** 대소문자가 있는 글자와 없는 글자(숫자, 기호, 한글)를 섞는다. {@code /}, {@code \}, {@code .}은 넣지 않는다. */
  private static final char[] NAME_CHARS = "abcXYZ019_-가나".toCharArray();

  private RepoPathArbitraries() {}

  /**
   * @param base 이미 정규화된 기준 경로
   * @param variant 기준 경로를 바꿔 쓴 값
   */
  public record PathPair(String base, String variant) {}

  /**
   * {@code RepoPaths.normalize}를 거쳐도 그대로인 경로. 구간 1~4개이고 {@code .claude} 같은 점 시작 구간, 확장자, 구간 안의 공백을
   * 포함한다. 드물게 빈 문자열도 낸다.
   */
  public static Arbitrary<String> normalized() {
    Arbitrary<String> name =
        Arbitraries.strings().withChars(NAME_CHARS).ofMinLength(1).ofMaxLength(8);
    Arbitrary<String> segment =
        Combinators.combine(
                Arbitraries.of("", "", "", "."),
                name,
                Arbitraries.of("", "", ".java", ".md", ".d.ts", " copy"))
            .as((dot, stem, suffix) -> dot + stem + suffix);
    Arbitrary<String> path =
        segment.list().ofMinSize(1).ofMaxSize(4).map(segments -> String.join("/", segments));
    return Arbitraries.frequencyOf(Tuple.of(19, path), Tuple.of(1, Arbitraries.just("")));
  }

  /** 정규화하면 기준 경로와 같아지는 짝. {@code ./}·{@code /} 접두어를 0~3번 붙이고, 절반은 {@code /}를 {@code \}로 바꾼다. */
  public static Arbitrary<PathPair> equivalentPairs() {
    Arbitrary<String> prefix =
        Arbitraries.of("./", "/")
            .list()
            .ofMinSize(0)
            .ofMaxSize(3)
            .map(parts -> String.join("", parts));
    return Combinators.combine(normalized(), prefix, Arbitraries.of(true, false))
        .as(
            (base, chosenPrefix, backslash) -> {
              String variant = chosenPrefix + base;
              return new PathPair(base, backslash ? variant.replace('/', '\\') : variant);
            });
  }

  /**
   * 정규화해도 기준 경로와 다른 짝. {@code ../}를 붙이거나 ASCII 글자 하나의 대소문자를 바꾼다. 기준 경로에 대소문자 있는 글자가 없으면 {@code
   * ../}만 쓴다.
   */
  public static Arbitrary<PathPair> differentPairs() {
    return normalized()
        .flatMap(
            base -> {
              Arbitrary<String> parent =
                  Arbitraries.integers().between(1, 2).map(count -> "../".repeat(count) + base);
              List<Integer> letters =
                  IntStream.range(0, base.length())
                      .filter(index -> isAsciiLetter(base.charAt(index)))
                      .boxed()
                      .toList();
              Arbitrary<String> variant =
                  letters.isEmpty()
                      ? parent
                      : Arbitraries.oneOf(
                          parent, Arbitraries.of(letters).map(index -> flipCase(base, index)));
              return variant.map(value -> new PathPair(base, value));
            });
  }

  private static boolean isAsciiLetter(char character) {
    return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z');
  }

  private static String flipCase(String text, int index) {
    char character = text.charAt(index);
    char flipped =
        Character.isUpperCase(character)
            ? Character.toLowerCase(character)
            : Character.toUpperCase(character);
    return text.substring(0, index) + flipped + text.substring(index + 1);
  }
}
