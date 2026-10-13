package com.prlens.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.ChunkIssue;
import com.prlens.model.Demotion;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.IntPredicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/** `ReviewResult` 생성기가 2.1의 불변식을 지키고 round-trip 테스트에 필요한 값을 내는지 확인한다. */
class ReviewResultArbitrariesTest {

  @Provide
  Arbitrary<ReviewResult> reviewResults() {
    return ReviewResultArbitraries.reviewResults();
  }

  @Property(tries = 200)
  void generatedResultsSatisfyModelInvariants(@ForAll("reviewResults") ReviewResult result) {
    assertThat(result.status() == ResultStatus.COMPLETE)
        .isEqualTo(result.incompleteReasons().isEmpty());
    assertThat(result.excludedFiles())
        .isEqualTo(result.excludedFileDetails().stream().map(ExcludedFile::path).toList());
    assertThat(result.findings())
        .allSatisfy(
            finding ->
                assertThat(finding.verdict() == LineVerdict.SUMMARY_ONLY)
                    .isEqualTo(finding.summaryOnlyReason() != null));
    Usage usage = result.usage();
    assertThat(
            List.of(
                usage.inputTokens(),
                usage.outputTokens(),
                usage.cacheWriteTokens(),
                usage.cacheReadTokens()))
        .allSatisfy(tokens -> assertThat(tokens).isNotNegative());
  }

  /** 생성자가 검사하지 않아 생성기가 따로 지키는 규칙 ({@code ReviewResultArbitraries}의 Javadoc). */
  @Property(tries = 200)
  void generatedResultsFollowDocumentedRules(@ForAll("reviewResults") ReviewResult result) {
    assertThat(result.findings())
        .allSatisfy(
            finding -> {
              assertThat(finding.basis().ref() == null)
                  .isEqualTo(finding.basis().type() == BasisType.GENERAL);
              if (finding.verdict() == LineVerdict.INLINE_ELIGIBLE
                  || finding.summaryOnlyReason() == SummaryOnlyReason.OUT_OF_RANGE) {
                assertThat(finding.line()).isNotNull();
              }
              if (finding.summaryOnlyReason() == SummaryOnlyReason.LINE_MISSING) {
                assertThat(finding.line()).isNull();
              }
            });
    assertThat(result.incompleteDetails().chunks())
        .allSatisfy(
            chunk -> {
              assertThat(chunk.chunkIndex()).isPositive();
              assertThat(chunk.reason()).isNotEqualTo(IncompleteReason.FILES_TRUNCATED);
              assertThat(chunk.rawResponseExcerpt() != null)
                  .isEqualTo(chunk.reason() == IncompleteReason.SCHEMA_VIOLATION);
            });
    assertThat(result.incompleteDetails().fileCountGap() != null)
        .isEqualTo(result.incompleteReasons().contains(IncompleteReason.FILES_TRUNCATED));
  }

  @Property(tries = 200)
  void sameFieldValuesProduceEqualResults(@ForAll("reviewResults") ReviewResult result) {
    ReviewResult rebuilt = rebuild(result);

    assertThat(rebuilt).isNotSameAs(result).isEqualTo(result);
    assertThat(rebuilt.hashCode()).isEqualTo(result.hashCode());
  }

  @Property(tries = 200)
  void generatedStringsCoverSpecialCharacters(@ForAll("reviewResults") ReviewResult result) {
    String text =
        result.summary()
            + result.findings().stream()
                .flatMap(finding -> Stream.of(finding.message(), finding.suggestion()))
                .filter(Objects::nonNull)
                .collect(Collectors.joining());

    covers("한글", hasCodePoint(text, codePoint -> codePoint >= 0xAC00 && codePoint <= 0xD7A3));
    covers("큰따옴표", text.contains("\""));
    covers("역슬래시", text.contains("\\"));
    covers("줄바꿈", text.contains("\n"));
    covers("탭", text.contains("\t"));
    covers("이모지", hasCodePoint(text, Character::isSupplementaryCodePoint));
  }

  @Property(tries = 200)
  void generatedResultsCoverNullOptionalFieldsAndEmptyLists(
      @ForAll("reviewResults") ReviewResult result) {
    List<Finding> findings = result.findings();
    List<ChunkIssue> chunks = result.incompleteDetails().chunks();

    covers("findings 빈 목록", findings.isEmpty());
    covers("findings 있음", !findings.isEmpty());
    covers("excludedFiles 빈 목록", result.excludedFiles().isEmpty());
    covers("excludedFiles 있음", !result.excludedFiles().isEmpty());
    covers("line null", findings.stream().anyMatch(finding -> finding.line() == null));
    covers("line 있음", findings.stream().anyMatch(finding -> finding.line() != null));
    covers("suggestion null", findings.stream().anyMatch(finding -> finding.suggestion() == null));
    covers("suggestion 있음", findings.stream().anyMatch(finding -> finding.suggestion() != null));
    covers("basis.ref null", findings.stream().anyMatch(finding -> finding.basis().ref() == null));
    covers("basis.ref 있음", findings.stream().anyMatch(finding -> finding.basis().ref() != null));
    covers("demotion null", findings.stream().anyMatch(finding -> finding.demotion() == null));
    covers("demotion 있음", findings.stream().anyMatch(finding -> finding.demotion() != null));
    covers(
        "summaryOnlyReason 있음",
        findings.stream().anyMatch(finding -> finding.summaryOnlyReason() != null));
    covers("estimatedCostUsd null", result.usage().estimatedCostUsd() == null);
    covers("estimatedCostUsd 있음", result.usage().estimatedCostUsd() != null);
    covers("int 범위를 넘는 토큰 수", result.usage().inputTokens() > Integer.MAX_VALUE);
    covers(
        "유효 숫자 17자리 이상의 비용",
        result.usage().estimatedCostUsd() != null
            && result.usage().estimatedCostUsd().precision() >= 17);
    covers("complete", result.status() == ResultStatus.COMPLETE);
    covers("incomplete", result.status() == ResultStatus.INCOMPLETE);
    covers("chunks 있음", !chunks.isEmpty());
    covers("fileCountGap 있음", result.incompleteDetails().fileCountGap() != null);
    covers(
        "rawResponseExcerpt 있음",
        chunks.stream().anyMatch(chunk -> chunk.rawResponseExcerpt() != null));
    covers("lastStatusCode 있음", chunks.stream().anyMatch(chunk -> chunk.lastStatusCode() != null));
  }

  /** 그 경우가 한 번 이상 생성됐는지 확인한다. */
  private static void covers(String label, boolean observed) {
    Statistics.label(label)
        .collect(observed)
        .coverage(coverage -> coverage.check(true).count(count -> count > 0));
  }

  private static boolean hasCodePoint(String text, IntPredicate predicate) {
    return text.codePoints().anyMatch(predicate);
  }

  /** 같은 필드 값으로 하위 타입과 목록을 모두 새로 만든다. */
  private static ReviewResult rebuild(ReviewResult result) {
    Usage usage = result.usage();
    IncompleteDetails details = result.incompleteDetails();
    FileCountGap gap = details.fileCountGap();
    ReviewStats stats = result.stats();
    BigDecimal cost = usage.estimatedCostUsd();
    return new ReviewResult(
        result.status(),
        new String(result.summary().toCharArray()),
        new ArrayList<>(
            result.findings().stream().map(ReviewResultArbitrariesTest::rebuild).toList()),
        new ArrayList<>(result.excludedFiles()),
        new ArrayList<>(
            result.excludedFileDetails().stream()
                .map(excluded -> new ExcludedFile(excluded.path(), excluded.reason()))
                .toList()),
        new Usage(
            usage.inputTokens(),
            usage.outputTokens(),
            usage.cacheWriteTokens(),
            usage.cacheReadTokens(),
            usage.model(),
            cost == null ? null : new BigDecimal(cost.toPlainString())),
        new ArrayList<>(result.incompleteReasons()),
        new IncompleteDetails(
            new ArrayList<>(
                details.chunks().stream()
                    .map(
                        chunk ->
                            new ChunkIssue(
                                chunk.chunkIndex(),
                                new ArrayList<>(chunk.files()),
                                chunk.reason(),
                                chunk.lastStatusCode(),
                                chunk.rawResponseExcerpt()))
                    .toList()),
            gap == null ? null : new FileCountGap(gap.reported(), gap.received())),
        new ReviewStats(
            stats.mode(), stats.changedLineCount(), stats.sizeLimit(), stats.chunkCount()));
  }

  private static Finding rebuild(Finding finding) {
    Demotion demotion = finding.demotion();
    return new Finding(
        finding.file(),
        finding.line(),
        finding.severity(),
        finding.category(),
        finding.message(),
        finding.suggestion(),
        new Basis(finding.basis().type(), finding.basis().ref()),
        demotion == null ? null : new Demotion(demotion.originalType(), demotion.originalRef()),
        finding.verdict(),
        finding.summaryOnlyReason());
  }
}
