package com.prlens.output;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ContextFile;
import com.prlens.model.Finding;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewResult;
import com.prlens.model.Severity;
import com.prlens.testkit.ReviewContextArbitraries;
import com.prlens.testkit.ReviewResultArbitraries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class OutputPropertyTest {

  /** 같은 파일의 Finding이 겹치도록 작은 경로 풀(보충 문자 포함)과 작은 라인 범위를 쓴다. */
  private static final List<String> FILE_POOL =
      List.of("a/A.java", "a/B.java", "b.md", "😀/x.md", "～/x.md");

  private final MarkdownFormatter formatter = new MarkdownFormatter();

  @Provide
  Arbitrary<ReviewResult> results() {
    return ReviewResultArbitraries.reviewResults()
        .flatMap(
            result ->
                ReviewResultArbitraries.findings()
                    .map(OutputPropertyTest::intoPool)
                    .list()
                    .ofMinSize(0)
                    .ofMaxSize(8)
                    .map(findings -> withFindings(result, findings)));
  }

  @Provide
  Arbitrary<ReviewContext> contexts() {
    return ReviewContextArbitraries.contexts();
  }

  private static Finding intoPool(Finding finding) {
    String file = FILE_POOL.get(Math.floorMod(finding.file().hashCode(), FILE_POOL.size()));
    Integer line = finding.line() == null ? null : (finding.line() % 6) + 1;
    return new Finding(
        file,
        line,
        finding.severity(),
        finding.category(),
        finding.message(),
        finding.suggestion(),
        finding.basis(),
        finding.demotion(),
        finding.verdict(),
        finding.summaryOnlyReason());
  }

  private static ReviewResult withFindings(ReviewResult base, List<Finding> findings) {
    return new ReviewResult(
        base.status(),
        base.summary(),
        findings,
        base.excludedFiles(),
        base.excludedFileDetails(),
        base.usage(),
        base.incompleteReasons(),
        base.incompleteDetails(),
        base.stats());
  }

  private static int compareCodePoints(String a, String b) {
    return Arrays.compare(a.codePoints().toArray(), b.codePoints().toArray());
  }

  /** 줄이 {@code "## "}로 시작하는 구역 제목. */
  private static List<String> headings(String text) {
    List<String> out = new ArrayList<>();
    for (String line : text.split("\n", -1)) {
      if (line.startsWith("## ")) {
        out.add(line);
      }
    }
    return out;
  }

  private static List<String> section(String text, String titlePrefix) {
    List<String> lines = List.of(text.split("\n", -1));
    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      if (lines.get(i).startsWith(titlePrefix)) {
        start = i + 1;
        break;
      }
    }
    assertThat(start).as(titlePrefix).isPositive();
    int end = start;
    while (end < lines.size() && !lines.get(end).startsWith("## ")) {
      end++;
    }
    return lines.subList(start, end);
  }

  private static int countOf(String heading) {
    String number = heading.replaceAll(".*\\((\\d+)건?\\)$", "$1");
    return Integer.parseInt(number);
  }

  // Feature: pr-lens-p1-cli, Property 35: Markdown 구조와 개수 일관성
  @Property(tries = 300)
  void sectionsAppearInFixedOrderAndEndWithSingleLf(
      @ForAll("results") ReviewResult result, @ForAll("contexts") ReviewContext context) {
    String text = formatter.format(result, context);

    List<String> headings = headings(text);
    List<String> expected = new ArrayList<>();
    if (result.status() == ResultStatus.INCOMPLETE) {
      expected.add("## 불완전한 결과");
    }
    expected.addAll(
        List.of(
            "## 요약",
            "## 심각도별 지적 수",
            "## 파일별 지적",
            "## 라인 밖 지적",
            "## 제외 파일",
            "## 사용된 컨텍스트 파일",
            "## 토큰 사용량과 추정 비용"));
    assertThat(headings).hasSameSizeAs(expected);
    for (int i = 0; i < expected.size(); i++) {
      assertThat(headings.get(i)).startsWith(expected.get(i));
    }
    assertThat(text).startsWith(expected.get(0));
    assertThat(text).doesNotContain("\r");
    assertThat(text).endsWith("\n").doesNotEndWith("\n\n");
  }

  // Feature: pr-lens-p1-cli, Property 35: Markdown 구조와 개수 일관성
  @Property(tries = 300)
  void countsAreConsistent(
      @ForAll("results") ReviewResult result, @ForAll("contexts") ReviewContext context) {
    String text = formatter.format(result, context);
    int total = result.findings().size();

    int severitySum = 0;
    for (Severity severity : Severity.values()) {
      String key = "- " + severity.name().toLowerCase(Locale.ROOT) + ": ";
      List<String> matching =
          section(text, "## 심각도별 지적 수").stream().filter(line -> line.startsWith(key)).toList();
      assertThat(matching).hasSize(1);
      int count = Integer.parseInt(matching.get(0).substring(key.length()));
      assertThat(count)
          .isEqualTo(result.findings().stream().filter(f -> f.severity() == severity).count());
      severitySum += count;
    }
    assertThat(severitySum).isEqualTo(total);

    int inline =
        countOf(
            headings(text).stream()
                .filter(h -> h.startsWith("## 파일별 지적"))
                .findFirst()
                .orElseThrow());
    int outside =
        countOf(
            headings(text).stream()
                .filter(h -> h.startsWith("## 라인 밖 지적"))
                .findFirst()
                .orElseThrow());
    assertThat(inline + outside).isEqualTo(total);

    long demoted = result.findings().stream().filter(f -> f.demotion() != null).count();
    assertThat(section(text, "## 심각도별 지적 수")).contains("강등된 지적: " + demoted + "건");
  }

  // Feature: pr-lens-p1-cli, Property 35: Markdown 구조와 개수 일관성
  @Property(tries = 300)
  void inlineFindingsAreSortedByPathThenLine(
      @ForAll("results") ReviewResult result, @ForAll("contexts") ReviewContext context) {
    List<String> lines = section(formatter.format(result, context), "## 파일별 지적");

    List<String> paths = new ArrayList<>();
    int bullets = 0;
    int previousLine = 0;
    for (String line : lines) {
      if (line.startsWith("### ")) {
        paths.add(line.substring(4));
        previousLine = 0;
      } else if (line.startsWith("- `L")) {
        int number = Integer.parseInt(line.substring(4, line.indexOf('`', 4)));
        assertThat(number).isGreaterThanOrEqualTo(previousLine);
        previousLine = number;
        bullets++;
      }
    }
    assertThat(bullets)
        .isEqualTo(
            result.findings().stream()
                .filter(f -> f.verdict() == LineVerdict.INLINE_ELIGIBLE)
                .count());
    List<String> sorted = new ArrayList<>(paths);
    sorted.sort(OutputPropertyTest::compareCodePoints);
    assertThat(paths).isEqualTo(sorted).doesNotHaveDuplicates();
  }

  // Feature: pr-lens-p1-cli, Property 35: Markdown 구조와 개수 일관성
  @Property(tries = 300)
  void contextFilesAreAllListedBySourceThenPath(
      @ForAll("results") ReviewResult result, @ForAll("contexts") ReviewContext context) {
    List<String> listed =
        section(formatter.format(result, context), "## 사용된 컨텍스트 파일").stream()
            .filter(line -> line.startsWith("- `"))
            .map(line -> line.substring(3, line.length() - 1))
            .toList();

    List<String> expected =
        context.files().stream()
            .sorted(
                Comparator.comparing(ContextFile::source)
                    .thenComparing(ContextFile::path, OutputPropertyTest::compareCodePoints))
            .map(ContextFile::path)
            .toList();
    assertThat(listed).isEqualTo(expected);
  }

  // Feature: pr-lens-p1-cli, Property 36: 종료 코드 결정
  @Property(tries = 300)
  void exitCodeFollowsBlockerThenCompleteness(@ForAll("results") ReviewResult result) {
    boolean blocker = result.findings().stream().anyMatch(f -> f.severity() == Severity.BLOCKER);
    int expected = blocker ? 1 : result.status() == ResultStatus.INCOMPLETE ? 3 : 0;

    assertThat(ExitCodeResolver.resolve(result)).isEqualTo(expected);
  }
}
