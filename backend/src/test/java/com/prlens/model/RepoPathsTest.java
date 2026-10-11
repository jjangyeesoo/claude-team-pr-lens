package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

/** Normalized_Repo_Path (요구사항 4.8, 11.1, 22.2, 22.6). */
class RepoPathsTest {

  @Test
  void normalizedPathIsReturnedUnchanged() {
    assertThat(RepoPaths.normalize(".claude/rules/java.md")).isEqualTo(".claude/rules/java.md");
    assertThat(RepoPaths.normalize("backend/src/Foo.java")).isEqualTo("backend/src/Foo.java");
  }

  // 요구사항 11.1의 예시 그대로
  @Test
  void requirementExamplesNormalizeToSamePath() {
    assertThat(RepoPaths.normalize("./.claude/rules/java.md")).isEqualTo(".claude/rules/java.md");
    assertThat(RepoPaths.normalize("/.claude/rules/java.md")).isEqualTo(".claude/rules/java.md");
    assertThat(RepoPaths.normalize(".claude\\rules\\java.md")).isEqualTo(".claude/rules/java.md");
    assertThat(RepoPaths.normalize(".claude/rules/Java.md")).isNotEqualTo(".claude/rules/java.md");
    assertThat(RepoPaths.normalize("../.claude/rules/java.md"))
        .isNotEqualTo(".claude/rules/java.md");
  }

  @Test
  void backslashesBecomeSlashesOnEveryOs() {
    assertThat(RepoPaths.normalize("backend\\src\\Foo.java")).isEqualTo("backend/src/Foo.java");
    assertThat(RepoPaths.normalize(".\\backend\\Foo.java")).isEqualTo("backend/Foo.java");
    assertThat(RepoPaths.normalize("\\backend/Foo.java")).isEqualTo("backend/Foo.java");
  }

  @Test
  void leadingDotSlashAndSlashAreRemovedRepeatedly() {
    assertThat(RepoPaths.normalize("././a.md")).isEqualTo("a.md");
    assertThat(RepoPaths.normalize("//a.md")).isEqualTo("a.md");
    assertThat(RepoPaths.normalize("/./a.md")).isEqualTo("a.md");
    assertThat(RepoPaths.normalize(".//./a.md")).isEqualTo("a.md");
    assertThat(RepoPaths.normalize("./")).isEmpty();
  }

  @Test
  void pathMadeOnlyOfPrefixesBecomesEmpty() {
    assertThat(RepoPaths.normalize("")).isEmpty();
    assertThat(RepoPaths.normalize("/")).isEmpty();
    assertThat(RepoPaths.normalize("\\")).isEmpty();
  }

  @Test
  void dotWithoutFollowingSlashIsKept() {
    assertThat(RepoPaths.normalize(".")).isEqualTo(".");
    assertThat(RepoPaths.normalize("..")).isEqualTo("..");
    assertThat(RepoPaths.normalize("./.")).isEqualTo(".");
  }

  @Test
  void whitespaceIsNotTrimmed() {
    assertThat(RepoPaths.normalize(" ./a.md")).isEqualTo(" ./a.md");
    assertThat(RepoPaths.normalize("./ a.md")).isEqualTo(" a.md");
    assertThat(RepoPaths.normalize("./a.md ")).isEqualTo("a.md ");
  }

  @Test
  void onlyLeadingPrefixIsRemoved() {
    assertThat(RepoPaths.normalize("a/./b.md")).isEqualTo("a/./b.md");
    assertThat(RepoPaths.normalize("a//b.md")).isEqualTo("a//b.md");
    assertThat(RepoPaths.normalize("a/b/")).isEqualTo("a/b/");
    assertThat(RepoPaths.normalize(".env")).isEqualTo(".env");
    assertThat(RepoPaths.normalize("./.env")).isEqualTo(".env");
  }

  @Test
  void parentSegmentsAreKeptLiterally() {
    assertThat(RepoPaths.normalize("../.claude/rules/java.md"))
        .isEqualTo("../.claude/rules/java.md");
    assertThat(RepoPaths.normalize("./../a.md")).isEqualTo("../a.md");
    assertThat(RepoPaths.normalize("..\\a.md")).isEqualTo("../a.md");
    assertThat(RepoPaths.normalize("a/../b.md")).isEqualTo("a/../b.md");
  }

  @Test
  void caseIsPreserved() {
    assertThat(RepoPaths.normalize("./.claude/rules/Java.md"))
        .isEqualTo(".claude/rules/Java.md")
        .isNotEqualTo(RepoPaths.normalize(".claude/rules/java.md"));
  }

  @Test
  void normalizeIsIdempotent() {
    String once = RepoPaths.normalize(".\\/./docs\\specs/README.md");

    assertThat(once).isEqualTo("docs/specs/README.md");
    assertThat(RepoPaths.normalize(once)).isEqualTo(once);
  }

  @Test
  void nullPathIsRejectedWithFieldName() {
    assertThatNullPointerException()
        .isThrownBy(() -> RepoPaths.normalize(null))
        .withMessage("path");
  }
}
