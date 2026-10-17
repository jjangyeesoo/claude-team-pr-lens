package com.prlens.output;

import static com.prlens.output.ResultFixtures.complete;
import static com.prlens.output.ResultFixtures.incomplete;
import static com.prlens.output.ResultFixtures.inline;
import static com.prlens.output.ResultFixtures.summaryOnly;
import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.Demotion;
import com.prlens.model.ExcludedFile;
import com.prlens.model.Finding;
import com.prlens.model.LineVerdict;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Revision;
import com.prlens.model.Severity;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownFormatterTest {

  private static final ReviewContext NO_CONTEXT = new ReviewContext(List.of());

  private final MarkdownFormatter formatter = new MarkdownFormatter();

  private static ContextFile file(String path, ContextSource source) {
    return new ContextFile(path, "내용", source, Revision.BASE);
  }

  private String format(ReviewResult result) {
    return formatter.format(result, NO_CONTEXT);
  }

  private static void assertInOrder(String text, String... parts) {
    int from = 0;
    for (String part : parts) {
      int at = text.indexOf(part, from);
      assertThat(at).as("'%s'가 순서대로 나와야 합니다", part).isGreaterThanOrEqualTo(0);
      from = at + part.length();
    }
  }

  // 15.1, 15.2
  @Test
  void printsSevenSectionsInOrderEvenWhenEmpty() {
    String text = format(complete("요약 본문", List.of()));

    assertThat(text).doesNotContain("불완전한 결과");
    assertInOrder(
        text,
        "## 요약",
        "요약 본문",
        "## 심각도별 지적 수",
        "## 파일별 지적 (0건)",
        "## 라인 밖 지적 (0건)",
        "## 제외 파일 (0건)",
        "## 사용된 컨텍스트 파일 (0)",
        "## 토큰 사용량과 추정 비용");
    assertThat(text).startsWith("## 요약");
    assertThat(text).endsWith("\n").doesNotEndWith("\n\n");
  }

  // 15.3, 16.3
  @Test
  void incompleteStartsWithWarningListingReasonsChunkFilesAndPartialNotice() {
    String text = format(incomplete(List.of(inline("A.java", 1, Severity.MINOR))));

    assertThat(text).startsWith("## 불완전한 결과");
    assertInOrder(text, "## 불완전한 결과", "## 요약");
    String warning = text.substring(0, text.indexOf("## 요약"));
    assertThat(warning).contains("부분 결과");
    assertThat(warning).contains("chunk_failed").contains("files_truncated");
    assertThat(warning).contains("web/a.tsx").contains("web/b.tsx");
  }

  // 16.3
  @Test
  void printsFileCountGapInWarning() {
    String warning = format(incomplete(List.of()));
    warning = warning.substring(0, warning.indexOf("## 요약"));

    assertThat(warning).contains("3100").contains("3000");
  }

  // 15.4
  @Test
  void printsAllFourSeveritiesWithZero() {
    String text =
        format(
            complete(
                "",
                List.of(
                    inline("A.java", 1, Severity.MAJOR),
                    inline("A.java", 2, Severity.MAJOR),
                    inline("B.java", 3, Severity.NIT))));

    assertInOrder(text, "- blocker: 0", "- major: 2", "- minor: 0", "- nit: 1");
  }

  // 15.5
  @Test
  void sortsFindingsByPathThenLine() {
    String text =
        format(
            complete(
                "",
                List.of(
                    inline("b/Z.java", 3, Severity.MINOR),
                    inline("a/Y.java", 20, Severity.MINOR),
                    inline("a/Y.java", 5, Severity.MAJOR),
                    inline("b/Z.java", 1, Severity.MINOR))));

    assertInOrder(text, "### a/Y.java", "`L5`", "`L20`", "### b/Z.java", "`L1`", "`L3`");
  }

  // 15.5
  @Test
  void sortsPathsByCodePointNotUtf16() {
    String emoji = "😀/A.java"; // U+1F600
    String bmp = "～/A.java"; // U+FF5E, UTF-16 순서로는 이모지(0xD83D)보다 뒤
    String text =
        format(complete("", List.of(inline(emoji, 1, Severity.NIT), inline(bmp, 1, Severity.NIT))));

    assertInOrder(text, "### " + bmp, "### " + emoji);
  }

  // 15.5
  @Test
  void omitsMissingSuggestionAndBasisRef() {
    Finding bare = inline("A.java", 1, Severity.MINOR);
    Finding full =
        new Finding(
            "B.java",
            2,
            Severity.MAJOR,
            Category.SECURITY,
            "전체",
            "이렇게 고치세요",
            new Basis(BasisType.RULE, ".claude/rules/backend/x.md"),
            null,
            LineVerdict.INLINE_ELIGIBLE,
            null);
    String text = format(complete("", List.of(bare, full)));
    String first = text.substring(text.indexOf("### A.java"), text.indexOf("### B.java"));
    String second = text.substring(text.indexOf("### B.java"), text.indexOf("## 라인 밖"));

    assertThat(first).contains("minor").contains("convention").contains("general");
    assertThat(first).doesNotContain("제안").doesNotContain("rules/");
    assertThat(second)
        .contains("major")
        .contains("security")
        .contains("제안: 이렇게 고치세요")
        .contains("rule")
        .contains(".claude/rules/backend/x.md");
  }

  // 15.6, 20.5
  @Test
  void printsTokensModelAndCostWithFourDecimals() {
    String text =
        format(
            ResultFixtures.withUsage(
                complete("", List.of()),
                new Usage(21_000, 3_100, 500, 9_000, "claude-opus-5-5", new BigDecimal("0.5"))));
    String tail = text.substring(text.indexOf("## 토큰 사용량과 추정 비용"));

    assertThat(tail)
        .contains("claude-opus-5-5")
        .contains("21000")
        .contains("3100")
        .contains("500")
        .contains("9000")
        .contains("$0.5000");
  }

  // 20.5
  @Test
  void printsUnknownCostWhenPricingMissing() {
    String text =
        format(
            ResultFixtures.withUsage(
                complete("", List.of()), new Usage(1, 2, 3, 4, "unpriced-model", null)));

    assertThat(text.substring(text.indexOf("## 토큰 사용량과 추정 비용"))).contains("알 수 없음");
  }

  // 15.14
  @Test
  void printsExcludedFilesWithReasons() {
    String text =
        format(
            ResultFixtures.withExcluded(
                complete("", List.of()),
                List.of(
                    new ExcludedFile("package-lock.json", "**/package-lock.json"),
                    new ExcludedFile("logo.png", "binary_or_too_large"))));
    String section = text.substring(text.indexOf("## 제외 파일"), text.indexOf("## 사용된 컨텍스트 파일"));

    assertThat(section).contains("(2건)");
    assertThat(section).contains("package-lock.json").contains("**/package-lock.json");
    assertThat(section).contains("logo.png").contains("binary_or_too_large");
  }

  // 8.1, 8.3
  @Test
  void listsContextFilesGroupedBySourceThenPathWithCount() {
    ReviewContext context =
        new ReviewContext(
            List.of(
                file("docs/specs/x/design.md", ContextSource.SPEC),
                file(".claude/rules/b.md", ContextSource.RULE),
                file("CLAUDE.md", ContextSource.CLAUDE_MD),
                file(".claude/rules/a.md", ContextSource.RULE),
                file("docs/adr/0001.md", ContextSource.IMPORT)));
    String text = formatter.format(complete("", List.of()), context);

    assertThat(text).contains("## 사용된 컨텍스트 파일 (5)");
    assertInOrder(
        text.substring(text.indexOf("## 사용된 컨텍스트 파일")),
        "claude_md",
        "`CLAUDE.md`",
        "rule",
        "`.claude/rules/a.md`",
        "`.claude/rules/b.md`",
        "import",
        "`docs/adr/0001.md`",
        "spec",
        "`docs/specs/x/design.md`");
  }

  // 8.4
  @Test
  void printsZeroContextFilesNotice() {
    String text = format(complete("", List.of()));
    String section =
        text.substring(text.indexOf("## 사용된 컨텍스트 파일"), text.indexOf("## 토큰 사용량과 추정 비용"));

    assertThat(section).contains("(0)").contains("컨텍스트 파일이 없습니다");
  }

  // 11.6
  @Test
  void printsDemotionCountIncludingZero() {
    assertThat(format(complete("", List.of(inline("A.java", 1, Severity.NIT)))))
        .contains("강등된 지적: 0건");

    Finding demoted =
        new Finding(
            "A.java",
            1,
            Severity.NIT,
            Category.TEST,
            "m",
            null,
            new Basis(BasisType.GENERAL, null),
            new Demotion(BasisType.RULE, ".claude/rules/nope.md"),
            LineVerdict.INLINE_ELIGIBLE,
            null);
    assertThat(format(complete("", List.of(demoted, demoted, inline("B.java", 2, Severity.NIT)))))
        .contains("강등된 지적: 2건");
  }

  // 12.8
  @Test
  void printsSummaryOnlyFindingsWithReasonOrZero() {
    String text =
        format(
            complete(
                "",
                List.of(
                    inline("A.java", 1, Severity.NIT),
                    summaryOnly("B.java", null, SummaryOnlyReason.LINE_MISSING),
                    summaryOnly("C.java", 9, SummaryOnlyReason.OUT_OF_RANGE))));
    String section = text.substring(text.indexOf("## 라인 밖 지적"), text.indexOf("## 제외 파일"));

    assertThat(section).contains("(2건)");
    assertThat(section).contains("B.java").contains("line_missing");
    assertThat(section).contains("C.java").contains("out_of_range");
    assertThat(text.substring(text.indexOf("## 파일별 지적"), text.indexOf("## 라인 밖 지적")))
        .contains("A.java")
        .doesNotContain("B.java")
        .doesNotContain("C.java");
  }

  // 13.9
  @Test
  void summaryOnlyModeTellsToSplitWithLineCountAndThreshold() {
    String text =
        format(
            ResultFixtures.withStats(
                complete("요약만", List.of()),
                new ReviewStats(ReviewMode.SUMMARY_ONLY, 1_201, 400, 0)));
    String summary = text.substring(text.indexOf("## 요약"), text.indexOf("## 심각도별 지적 수"));

    assertThat(summary).contains("PR을 나누세요").contains("1201").contains("1200");
    assertThat(format(complete("요약만", List.of()))).doesNotContain("PR을 나누세요");
  }

  // 22.5
  @Test
  void usesLfOnlyAndNormalizesCrInText() {
    Finding finding =
        new Finding(
            "A.java",
            1,
            Severity.NIT,
            Category.TEST,
            "첫 줄\r\n둘째 줄\r셋째 줄",
            null,
            new Basis(BasisType.GENERAL, null),
            null,
            LineVerdict.INLINE_ELIGIBLE,
            null);
    String text = format(complete("요약\r\n본문", List.of(finding)));

    assertThat(text).doesNotContain("\r");
    assertThat(text).contains("첫 줄\n").contains("둘째 줄\n").contains("셋째 줄");
  }
}
