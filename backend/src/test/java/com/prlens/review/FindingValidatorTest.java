package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.diff.LineRanges;
import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.Demotion;
import com.prlens.model.Finding;
import com.prlens.model.Hunk;
import com.prlens.model.LineVerdict;
import com.prlens.model.ReviewContext;
import com.prlens.model.Revision;
import com.prlens.model.Severity;
import com.prlens.model.SummaryOnlyReason;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FindingValidatorTest {

  private static final String RULE = ".claude/rules/java.md";
  private static final ReviewContext CONTEXT =
      new ReviewContext(List.of(new ContextFile(RULE, "x", ContextSource.RULE, Revision.BASE)));
  // head 10~14 줄이 바뀐 파일 둘, 삭제만 있는 Hunk를 가진 파일 하나
  private static final Map<String, LineRanges> TARGETS =
      Map.of(
          "src/A.java", LineRanges.of(List.of(hunk(10, 5))),
          "src/B.java", LineRanges.of(List.of(hunk(1, 0))),
          "src/<prlens:x.java", LineRanges.of(List.of(hunk(1, 3))));

  private static Hunk hunk(int headStart, int headCount) {
    return new Hunk(headStart, 1, headStart, headCount, "", List.of());
  }

  private static RawFinding raw(String file, Integer line, BasisType type, String ref) {
    return new RawFinding(file, line, Severity.MAJOR, Category.CORRECTNESS, "m", "s", type, ref);
  }

  private static Finding validate(RawFinding raw) {
    return FindingValidator.apply(List.of(raw), CONTEXT, TARGETS).get(0);
  }

  // 요구사항 11.1, 11.2
  @Test
  void normalizesRefBeforeComparing() {
    for (String ref : List.of(RULE, "./" + RULE, "/" + RULE, ".claude\\rules\\java.md")) {
      Finding f = validate(raw("src/A.java", 10, BasisType.RULE, ref));

      assertThat(f.basis()).isEqualTo(new Basis(BasisType.RULE, ref));
      assertThat(f.demotion()).isNull();
    }
  }

  // 요구사항 11.1 (대소문자 구분, ..은 해석하지 않음)
  @Test
  void comparesCaseSensitivelyAndKeepsDotDot() {
    assertThat(
            validate(raw("src/A.java", 10, BasisType.RULE, ".claude/Rules/java.md")).basis().type())
        .isEqualTo(BasisType.GENERAL);
    assertThat(validate(raw("src/A.java", 10, BasisType.RULE, "x/../" + RULE)).basis().type())
        .isEqualTo(BasisType.GENERAL);
  }

  // 요구사항 11.3
  @Test
  void demotesRuleWhenRefNotInContext() {
    Finding f = validate(raw("src/A.java", 10, BasisType.SPEC, "docs/none.md"));

    assertThat(f.basis()).isEqualTo(new Basis(BasisType.GENERAL, null));
    assertThat(f.demotion()).isEqualTo(new Demotion(BasisType.SPEC, "docs/none.md"));
  }

  // 요구사항 11.4
  @Test
  void demotesWhenRefIsNullBlankOrWhitespace() {
    for (String ref : new String[] {null, "", "  ", "\t\n"}) {
      Finding f = validate(raw("src/A.java", 10, BasisType.RULE, ref));

      assertThat(f.basis().type()).isEqualTo(BasisType.GENERAL);
      assertThat(f.demotion()).isEqualTo(new Demotion(BasisType.RULE, ref));
    }
  }

  // 요구사항 11.1 ("정확히 일치"): 앞뒤 공백은 지우지 않는다
  @Test
  void refWithSurroundingWhitespaceIsDemoted() {
    Finding f = validate(raw("src/A.java", 10, BasisType.RULE, " " + RULE));

    assertThat(f.demotion()).isEqualTo(new Demotion(BasisType.RULE, " " + RULE));
  }

  // 요구사항 11.5
  @Test
  void keepsGeneralBasisUntouched() {
    Finding f = validate(raw("src/A.java", 10, BasisType.GENERAL, "anything"));

    assertThat(f.basis()).isEqualTo(new Basis(BasisType.GENERAL, "anything"));
    assertThat(f.demotion()).isNull();
  }

  // 요구사항 12.1
  @Test
  void inlineEligibleWhenLineInsideHunk() {
    for (int line : new int[] {10, 14}) {
      Finding f = validate(raw("src/A.java", line, BasisType.GENERAL, null));

      assertThat(f.verdict()).isEqualTo(LineVerdict.INLINE_ELIGIBLE);
      assertThat(f.summaryOnlyReason()).isNull();
    }
  }

  // 요구사항 12.2 (이름 변경은 새 경로가 키), 12.4
  @Test
  void fileNotInTargetsIsNotTargetFile() {
    Finding f = validate(raw("src/Old.java", 10, BasisType.GENERAL, null));

    assertThat(f.verdict()).isEqualTo(LineVerdict.SUMMARY_ONLY);
    assertThat(f.summaryOnlyReason()).isEqualTo(SummaryOnlyReason.NOT_TARGET_FILE);
  }

  // 요구사항 12.5
  @Test
  void nullLineIsLineMissing() {
    Finding f = validate(raw("src/A.java", null, BasisType.GENERAL, null));

    assertThat(f.summaryOnlyReason()).isEqualTo(SummaryOnlyReason.LINE_MISSING);
  }

  // 요구사항 12.6 (삭제만 있는 Hunk 포함)
  @Test
  void lineOutsideRangesIsOutOfRange() {
    assertThat(validate(raw("src/A.java", 9, BasisType.GENERAL, null)).summaryOnlyReason())
        .isEqualTo(SummaryOnlyReason.OUT_OF_RANGE);
    assertThat(validate(raw("src/A.java", 15, BasisType.GENERAL, null)).summaryOnlyReason())
        .isEqualTo(SummaryOnlyReason.OUT_OF_RANGE);
    assertThat(validate(raw("src/B.java", 1, BasisType.GENERAL, null)).summaryOnlyReason())
        .isEqualTo(SummaryOnlyReason.OUT_OF_RANGE);
  }

  // 요구사항 12.4~12.6의 판정 순서: not_target_file → line_missing → out_of_range
  @Test
  void notTargetFileWinsOverMissingLine() {
    Finding f = validate(raw("src/Old.java", null, BasisType.GENERAL, null));

    assertThat(f.summaryOnlyReason()).isEqualTo(SummaryOnlyReason.NOT_TARGET_FILE);
  }

  // 요구사항 11.10, 12.7
  @Test
  void onlyBasisDemotionAndVerdictFieldsChange() {
    RawFinding raw = raw("src/A.java", 10, BasisType.RULE, "docs/none.md");

    Finding f = validate(raw);

    assertThat(f.file()).isEqualTo(raw.file());
    assertThat(f.line()).isEqualTo(raw.line());
    assertThat(f.severity()).isEqualTo(raw.severity());
    assertThat(f.category()).isEqualTo(raw.category());
    assertThat(f.message()).isEqualTo(raw.message());
    assertThat(f.suggestion()).isEqualTo(raw.suggestion());
  }

  @Test
  void keepsCountAndOrder() {
    List<Finding> out =
        FindingValidator.apply(
            List.of(
                raw("src/B.java", 1, BasisType.GENERAL, null),
                raw("src/A.java", 10, BasisType.GENERAL, null),
                raw("src/Z.java", 1, BasisType.GENERAL, null)),
            CONTEXT,
            TARGETS);

    assertThat(out)
        .extracting(Finding::file)
        .containsExactly("src/B.java", "src/A.java", "src/Z.java");
  }

  // G-13: 이스케이프된 file은 비교할 때만 unescape하고 출력의 file은 그대로 둔다
  @Test
  void unescapesFileOnlyForComparison() {
    Finding f = validate(raw("src/<~prlens:x.java", 2, BasisType.GENERAL, null));

    assertThat(f.file()).isEqualTo("src/<~prlens:x.java");
    assertThat(f.verdict()).isEqualTo(LineVerdict.INLINE_ELIGIBLE);
  }
}
