package com.prlens.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.diff.DiffParseException.Kind;
import com.prlens.model.DiffLine;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffParserTest {

  private static final String PATH = "src/A.java";

  @Test
  void parsesHunksWithKindsAndSectionHeading() {
    String patch = "@@ -1,3 +1,3 @@ class A {\n a\n-b\n+c\n d\n@@ -10,1 +10,2 @@\n x\n+y\n";

    List<Hunk> hunks = DiffParser.parse(PATH, patch);

    assertThat(hunks)
        .containsExactly(
            new Hunk(
                1,
                3,
                1,
                3,
                " class A {",
                List.of(
                    new DiffLine(LineKind.CONTEXT, "a", false),
                    new DiffLine(LineKind.REMOVED, "b", false),
                    new DiffLine(LineKind.ADDED, "c", false),
                    new DiffLine(LineKind.CONTEXT, "d", false))),
            new Hunk(
                10,
                1,
                10,
                2,
                "",
                List.of(
                    new DiffLine(LineKind.CONTEXT, "x", false),
                    new DiffLine(LineKind.ADDED, "y", false))));
  }

  @Test
  void omittedCountInHeaderMeansOne() {
    List<Hunk> hunks = DiffParser.parse(PATH, "@@ -3 +3 @@\n-a\n+b\n");

    assertThat(hunks).hasSize(1);
    assertThat(hunks.get(0).baseCount()).isEqualTo(1);
    assertThat(hunks.get(0).headCount()).isEqualTo(1);
  }

  @Test
  void noNewlineMarkerSetsFlagOnPreviousLineAndIsNotCounted() {
    String patch =
        "@@ -1,1 +1,1 @@\n-a\n\\ No newline at end of file\n+b\n\\ No newline at end of file\n";

    List<DiffLine> lines = DiffParser.parse(PATH, patch).get(0).lines();

    assertThat(lines)
        .containsExactly(
            new DiffLine(LineKind.REMOVED, "a", true), new DiffLine(LineKind.ADDED, "b", true));
  }

  @Test
  void sectionHeadingMayContainUnicodeLineSeparators() {
    // 포맷터가 유니코드 이스케이프를 실제 문자로 바꾸므로 문자 코드로 만든다
    String heading = " a" + (char) 0x0085 + "b" + (char) 0x2028 + "c" + (char) 0x2029 + "d";

    List<Hunk> hunks = DiffParser.parse(PATH, "@@ -1,1 +1,1 @@" + heading + "\n x\n");

    assertThat(hunks.get(0).sectionHeading()).isEqualTo(heading);
  }

  @Test
  void emptyPatchReturnsEmptyList() {
    assertThat(DiffParser.parse(PATH, "")).isEmpty();
  }

  @Test
  void crlfAndLoneCrParseSameAsLf() {
    String lf = "@@ -1,2 +1,2 @@ s\n a\n-b\n+c\n";

    List<Hunk> expected = DiffParser.parse(PATH, lf);

    assertThat(expected).hasSize(1);
    assertThat(DiffParser.parse(PATH, lf.replace("\n", "\r\n"))).isEqualTo(expected);
    assertThat(DiffParser.parse(PATH, lf.replace("\n", "\r"))).isEqualTo(expected);
  }

  @Test
  void rejectsMalformedHeader() {
    assertRejected("@@ -1,2 @@\n a\n", 1, 0, Kind.HEADER_SYNTAX);
    assertRejected("@@ -1,2 +1,2 @@\n a\n b\n@@ nonsense\n", 4, 1, Kind.HEADER_SYNTAX);
    assertRejected("@@ -1,99999999999 +1 @@\n a\n", 1, 0, Kind.HEADER_SYNTAX);
  }

  @Test
  void rejectsCountMismatch() {
    assertRejected("@@ -1,3 +1,3 @@\n a\n b\n", 3, 0, Kind.LINE_COUNT_MISMATCH);
    assertRejected("@@ -1,1 +1,1 @@\n a\n b\n", 3, 0, Kind.LINE_COUNT_MISMATCH);
    // 줄 수가 남았는데 다음 헤더가 나온 경우도 마지막 Hunk의 부족과 같은 종류다
    assertRejected(
        "@@ -1,3 +1,3 @@\n a\n b\n@@ -10,1 +10,1 @@\n c\n", 4, 0, Kind.LINE_COUNT_MISMATCH);
  }

  @Test
  void rejectsInvalidPrefix() {
    assertRejected("@@ -1,2 +1,2 @@\n a\nxb\n", 3, 0, Kind.INVALID_PREFIX);
    assertRejected("@@ -1,2 +1,2 @@\n a\n\n", 3, 0, Kind.INVALID_PREFIX);
  }

  @Test
  void rejectsNoNewlineMarkerWithoutPreviousLineOrRepeated() {
    assertRejected(
        "@@ -1,1 +1,1 @@\n\\ No newline at end of file\n a\n", 2, 0, Kind.INVALID_PREFIX);
    assertRejected(
        "@@ -1,1 +1,1 @@\n a\n\\ No newline at end of file\n\\ No newline at end of file\n",
        4,
        0,
        Kind.INVALID_PREFIX);
  }

  @Test
  void parsesNewFileHeaderAndZeroCountHunk() {
    List<Hunk> newFile = DiffParser.parse(PATH, "@@ -0,0 +1,2 @@\n+a\n+b\n");

    assertThat(newFile).hasSize(1);
    assertThat(newFile.get(0).baseStart()).isZero();
    assertThat(newFile.get(0).baseCount()).isZero();
    assertThat(newFile.get(0).headCount()).isEqualTo(2);

    // 줄이 없는 0/0 Hunk는 git이 만들지 않지만 요구사항이 막지 않으므로 받아들인다
    assertThat(DiffParser.parse(PATH, "@@ -1,0 +1,0 @@\n")).hasSize(1);
  }

  @Test
  void rejectsContentBeforeFirstHeader() {
    assertRejected("diff --git a/x b/x\n@@ -1 +1 @@\n-a\n+b\n", 1, 0, Kind.CONTENT_BEFORE_HEADER);
  }

  @Test
  void rejectsOverlappingOrNonIncreasingHunks() {
    assertRejected("@@ -1,2 +1,2 @@\n a\n b\n@@ -5,1 +2,1 @@\n c\n", 4, 1, Kind.HUNK_OVERLAP);
    assertRejected("@@ -5,1 +5,1 @@\n a\n@@ -1,1 +1,1 @@\n b\n", 3, 1, Kind.HUNK_OVERLAP);
    // head 줄 수 0인 Hunk 뒤에 같은 head 시작 줄이 오는 경우 (시작 줄이 증가하지 않음)
    assertRejected("@@ -5,2 +4,0 @@\n-a\n-b\n@@ -8,1 +4,1 @@\n c\n", 4, 1, Kind.HUNK_OVERLAP);
  }

  private static void assertRejected(String patch, int lineNo, int hunkIndex, Kind kind) {
    assertThatThrownBy(() -> DiffParser.parse(PATH, patch))
        .isInstanceOfSatisfying(
            DiffParseException.class,
            e -> {
              assertThat(e.path()).isEqualTo(PATH);
              assertThat(e.lineNo()).isEqualTo(lineNo);
              assertThat(e.hunkIndex()).isEqualTo(hunkIndex);
              assertThat(e.kind()).isEqualTo(kind);
            });
  }
}
