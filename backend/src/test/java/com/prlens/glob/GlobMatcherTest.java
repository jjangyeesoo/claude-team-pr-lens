package com.prlens.glob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GlobMatcherTest {

  @Test
  void doubleStarSlashX_matchesRootX() {
    assertThat(GlobMatcher.matches("**/x", "x")).isTrue();
    assertThat(GlobMatcher.matches("**/x", "a/b/x")).isTrue();
    assertThat(GlobMatcher.matches("**/x", "a/b/y")).isFalse();
  }

  @Test
  void matchingIsCaseSensitive() {
    assertThat(GlobMatcher.matches("**/*.png", "a/B.png")).isTrue();
    assertThat(GlobMatcher.matches("**/*.png", "a/B.PNG")).isFalse();
    assertThat(GlobMatcher.matches("A/x", "a/x")).isFalse();
  }

  @Test
  void star_doesNotCrossSlash() {
    assertThat(GlobMatcher.matches("a/*.java", "a/Foo.java")).isTrue();
    assertThat(GlobMatcher.matches("a/*.java", "a/b/Foo.java")).isFalse();
    assertThat(GlobMatcher.matches("a/*", "a/")).isTrue();
  }

  @Test
  void question_isSingleNonSlashChar() {
    assertThat(GlobMatcher.matches("a?c", "abc")).isTrue();
    assertThat(GlobMatcher.matches("a?c", "ac")).isFalse();
    assertThat(GlobMatcher.matches("a?c", "a/c")).isFalse();
  }

  @Test
  void doubleStar_matchesZeroOrMoreSegments() {
    assertThat(GlobMatcher.matches("a/**/b", "a/b")).isTrue();
    assertThat(GlobMatcher.matches("a/**/b", "a/x/y/b")).isTrue();
    assertThat(GlobMatcher.matches("a/**", "a/x/y")).isTrue();
    assertThat(GlobMatcher.matches("**/generated/**", "src/generated/Foo.java")).isTrue();
    assertThat(GlobMatcher.matches("**/.env*", ".env.local")).isTrue();
  }

  @Test
  void leadingDotSlashAndSlash_areStrippedFromPatternAndPath() {
    assertThat(GlobMatcher.matches("./a/b", "/a/b")).isTrue();
    assertThat(GlobMatcher.matches("/./a/b", "./a/b")).isTrue();
    assertThat(GlobMatcher.matches("a/b", "a\\b")).isTrue();
  }

  @Test
  void braceExpansion_includingNestedAndSlash() {
    assertThat(GlobMatcher.matches("a/*.{java,kt}", "a/X.kt")).isTrue();
    assertThat(GlobMatcher.matches("a/*.{java,kt}", "a/X.md")).isFalse();
    assertThat(GlobMatcher.matches("x.{a,b{1,2}}", "x.b2")).isTrue();
    assertThat(GlobMatcher.matches("x.{a,b{1,2}}", "x.b")).isFalse();
    assertThat(GlobMatcher.matches("{a/b,c}/d", "a/b/d")).isTrue();
    assertThat(GlobMatcher.matches("{a/b,c}/d", "c/d")).isTrue();
    assertThat(GlobMatcher.matches("{a/b,c}/d", "a/d")).isFalse();
  }

  @Test
  void braceExpansion_isDoneBeforePrefixRemovalAndDoubleStarCheck() {
    assertThat(GlobMatcher.matches("{a,*}*", "x/y")).isTrue();
    assertThat(GlobMatcher.matches("{**,a}/x", "q/r/x")).isTrue();
    assertThat(GlobMatcher.matches("{./a,b}", "a")).isTrue();
    assertThat(GlobMatcher.matches("{/a,b}", "a")).isTrue();
    assertSyntaxError("{a**,b}");
  }

  @Test
  void regexMetaCharacters_areLiteral() {
    assertThat(GlobMatcher.matches("a.b(c)+[d]$", "a.b(c)+[d]$")).isTrue();
    assertThat(GlobMatcher.matches("a.b", "axb")).isFalse();
  }

  @Test
  void validate_rejectsUnbalancedBraces() {
    assertSyntaxError("a/{b,c");
    assertSyntaxError("a/b}");
    assertSyntaxError("{a,{b}");
  }

  @Test
  void validate_rejectsEmptyPatternAfterPrefixRemoval() {
    assertSyntaxError("");
    assertSyntaxError("/");
    assertSyntaxError("./");
  }

  @Test
  void validate_rejectsPatternWhenAnyExpansionIsEmpty() {
    assertSyntaxError("{a,}");
    assertSyntaxError("{/,x}");
    assertThatCode(() -> GlobMatcher.validate("{a,b}")).doesNotThrowAnyException();
  }

  @Test
  void validate_rejectsBraceNestingOver32() {
    String ok = "{".repeat(32) + "a" + "}".repeat(32);
    String tooDeep = "{".repeat(33) + "a" + "}".repeat(33);
    assertThatCode(() -> GlobMatcher.validate(ok)).doesNotThrowAnyException();
    assertSyntaxError(tooDeep);
    String thousands = "{".repeat(5000) + "a" + "}".repeat(5000);
    assertSyntaxError(thousands);
  }

  @Test
  void validate_rejectsBackslash() {
    assertSyntaxError("a\\b");
    assertSyntaxError("\\");
  }

  @Test
  void validate_rejectsDoubleStarAsPartOfSegment() {
    assertSyntaxError("a**b");
    assertSyntaxError("**.java");
    assertSyntaxError("a/***/b");
  }

  @Test
  void validate_rejectsMoreThan256Expansions() {
    String eight = IntStream.range(0, 8).mapToObj(i -> "{a,b}").collect(Collectors.joining("/"));
    String nine = eight + "/{a,b}";
    assertThatCode(() -> GlobMatcher.validate(eight)).doesNotThrowAnyException();
    assertSyntaxError(nine);
  }

  @Test
  void matches_throwsOnInvalidPattern() {
    assertThatThrownBy(() -> GlobMatcher.matches("a\\b", "a/b"))
        .isInstanceOf(GlobSyntaxException.class);
  }

  private static void assertSyntaxError(String pattern) {
    assertThatThrownBy(() -> GlobMatcher.validate(pattern))
        .isInstanceOfSatisfying(
            GlobSyntaxException.class,
            e -> {
              assertThat(e.pattern()).isEqualTo(pattern);
              assertThat(e.reason()).isNotBlank();
            });
  }
}
