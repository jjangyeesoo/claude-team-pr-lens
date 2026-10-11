package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 리뷰 결과 타입 (요구사항 17.4~17.8, design.md "Data Models"의 불변식). */
class ReviewResultTest {

  private static final Usage USAGE = new Usage(10, 20, 0, 5, "claude-opus-5-5", null);
  private static final ReviewStats STATS = new ReviewStats(ReviewMode.SINGLE, 12, 400, 1);
  private static final IncompleteDetails NO_DETAILS = new IncompleteDetails(List.of(), null);
  private static final Basis GENERAL = new Basis(BasisType.GENERAL, null);

  private static Finding finding(String file) {
    return new Finding(
        file,
        3,
        Severity.MAJOR,
        Category.CORRECTNESS,
        "메시지",
        null,
        GENERAL,
        null,
        LineVerdict.INLINE_ELIGIBLE,
        null);
  }

  private static ReviewResult result(
      ResultStatus status,
      List<Finding> findings,
      List<String> excludedFiles,
      List<ExcludedFile> excludedFileDetails,
      List<IncompleteReason> incompleteReasons) {
    return new ReviewResult(
        status,
        "요약",
        findings,
        excludedFiles,
        excludedFileDetails,
        USAGE,
        incompleteReasons,
        NO_DETAILS,
        STATS);
  }

  private static ReviewResult complete(List<Finding> findings) {
    return result(ResultStatus.COMPLETE, findings, List.of(), List.of(), List.of());
  }

  @Test
  void completeWithIncompleteReasonsIsRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                result(
                    ResultStatus.COMPLETE,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(IncompleteReason.MAX_TOKENS)));
  }

  @Test
  void incompleteWithoutReasonsIsRejected() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () -> result(ResultStatus.INCOMPLETE, List.of(), List.of(), List.of(), List.of()));
  }

  @Test
  void incompleteWithReasonsIsAccepted() {
    ReviewResult result =
        result(
            ResultStatus.INCOMPLETE,
            List.of(),
            List.of(),
            List.of(),
            List.of(IncompleteReason.CHUNK_FAILED));

    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.CHUNK_FAILED);
  }

  @Test
  void excludedFilesMustEqualDetailPathsInOrder() {
    List<ExcludedFile> details =
        List.of(
            new ExcludedFile("package-lock.json", "**/package-lock.json"),
            new ExcludedFile("logo.png", "**/*.png"));

    assertThat(
            result(
                    ResultStatus.COMPLETE,
                    List.of(),
                    List.of("package-lock.json", "logo.png"),
                    details,
                    List.of())
                .excludedFiles())
        .containsExactly("package-lock.json", "logo.png");
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                result(
                    ResultStatus.COMPLETE,
                    List.of(),
                    List.of("logo.png", "package-lock.json"),
                    details,
                    List.of()));
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                result(
                    ResultStatus.COMPLETE,
                    List.of(),
                    List.of("package-lock.json"),
                    details,
                    List.of()));
  }

  @Test
  void listsAreCopiedDefensively() {
    List<Finding> findings = new ArrayList<>(List.of(finding("A.java")));
    List<String> excludedFiles = new ArrayList<>(List.of("logo.png"));
    List<ExcludedFile> details = new ArrayList<>(List.of(new ExcludedFile("logo.png", "**/*.png")));
    List<IncompleteReason> reasons = new ArrayList<>(List.of(IncompleteReason.REFUSAL));
    ReviewResult result =
        result(ResultStatus.INCOMPLETE, findings, excludedFiles, details, reasons);

    findings.clear();
    excludedFiles.clear();
    details.clear();
    reasons.clear();

    assertThat(result.findings()).containsExactly(finding("A.java"));
    assertThat(result.excludedFiles()).containsExactly("logo.png");
    assertThat(result.excludedFileDetails())
        .containsExactly(new ExcludedFile("logo.png", "**/*.png"));
    assertThat(result.incompleteReasons()).containsExactly(IncompleteReason.REFUSAL);
  }

  @Test
  void exposedListsRejectModification() {
    ReviewResult result =
        result(
            ResultStatus.INCOMPLETE,
            new ArrayList<>(List.of(finding("A.java"))),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(List.of(IncompleteReason.REFUSAL)));

    assertThatThrownBy(() -> result.findings().add(finding("B.java")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.excludedFiles().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.excludedFileDetails().add(new ExcludedFile("x", "y")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> result.incompleteReasons().add(IncompleteReason.MAX_TOKENS))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void incompleteDetailsListsAreCopiedAndUnmodifiable() {
    List<String> files = new ArrayList<>(List.of("A.java"));
    ChunkIssue issue = new ChunkIssue(2, files, IncompleteReason.CHUNK_FAILED, 529, null);
    List<ChunkIssue> chunks = new ArrayList<>(List.of(issue));
    IncompleteDetails details = new IncompleteDetails(chunks, new FileCountGap(120, 100));

    files.clear();
    chunks.clear();

    assertThat(issue.files()).containsExactly("A.java");
    assertThat(details.chunks()).containsExactly(issue);
    assertThatThrownBy(() -> issue.files().add("B.java"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> details.chunks().add(issue))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void optionalFieldsMayBeNull() {
    ChunkIssue issue = new ChunkIssue(1, List.of(), IncompleteReason.SCHEMA_VIOLATION, null, null);

    assertThat(issue.lastStatusCode()).isNull();
    assertThat(issue.rawResponseExcerpt()).isNull();
    assertThat(NO_DETAILS.fileCountGap()).isNull();
    assertThat(finding("A.java").suggestion()).isNull();
    assertThat(
            new Finding(
                    "A.java",
                    null,
                    Severity.NIT,
                    Category.DESIGN,
                    "m",
                    null,
                    GENERAL,
                    new Demotion(BasisType.RULE, null),
                    LineVerdict.SUMMARY_ONLY,
                    SummaryOnlyReason.LINE_MISSING)
                .line())
        .isNull();
  }

  @Test
  void nullRequiredFieldIsRejectedWithFieldName() {
    List<Finding> none = List.of();
    List<String> noPaths = List.of();
    List<ExcludedFile> noDetails = List.of();
    List<IncompleteReason> noReasons = List.of();

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    null, "s", none, noPaths, noDetails, USAGE, noReasons, NO_DETAILS, STATS))
        .withMessage("status");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    null,
                    none,
                    noPaths,
                    noDetails,
                    USAGE,
                    noReasons,
                    NO_DETAILS,
                    STATS))
        .withMessage("summary");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    null,
                    noPaths,
                    noDetails,
                    USAGE,
                    noReasons,
                    NO_DETAILS,
                    STATS))
        .withMessage("findings");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    none,
                    null,
                    noDetails,
                    USAGE,
                    noReasons,
                    NO_DETAILS,
                    STATS))
        .withMessage("excludedFiles");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    none,
                    noPaths,
                    null,
                    USAGE,
                    noReasons,
                    NO_DETAILS,
                    STATS))
        .withMessage("excludedFileDetails");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    none,
                    noPaths,
                    noDetails,
                    null,
                    noReasons,
                    NO_DETAILS,
                    STATS))
        .withMessage("usage");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    none,
                    noPaths,
                    noDetails,
                    USAGE,
                    null,
                    NO_DETAILS,
                    STATS))
        .withMessage("incompleteReasons");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    none,
                    noPaths,
                    noDetails,
                    USAGE,
                    noReasons,
                    null,
                    STATS))
        .withMessage("incompleteDetails");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new ReviewResult(
                    ResultStatus.COMPLETE,
                    "s",
                    none,
                    noPaths,
                    noDetails,
                    USAGE,
                    noReasons,
                    NO_DETAILS,
                    null))
        .withMessage("stats");
    assertThatNullPointerException()
        .isThrownBy(() -> new ExcludedFile(null, "r"))
        .withMessage("path");
    assertThatNullPointerException()
        .isThrownBy(() -> new ExcludedFile("p", null))
        .withMessage("reason");
    assertThatNullPointerException()
        .isThrownBy(() -> new IncompleteDetails(null, null))
        .withMessage("chunks");
    assertThatNullPointerException()
        .isThrownBy(() -> new ChunkIssue(1, null, IncompleteReason.REFUSAL, null, null))
        .withMessage("files");
    assertThatNullPointerException()
        .isThrownBy(() -> new ChunkIssue(1, List.of(), null, null, null))
        .withMessage("reason");
    assertThatNullPointerException()
        .isThrownBy(() -> new ReviewStats(null, 0, 400, 0))
        .withMessage("mode");
    assertThatNullPointerException().isThrownBy(() -> new Basis(null, null)).withMessage("type");
    assertThatNullPointerException()
        .isThrownBy(() -> new Demotion(null, null))
        .withMessage("originalType");
  }

  @Test
  void findingRejectsNullRequiredFieldWithFieldName() {
    assertThatNullPointerException()
        .isThrownBy(() -> findingWith(null, Severity.MAJOR, Category.TEST, "m", GENERAL))
        .withMessage("file");
    assertThatNullPointerException()
        .isThrownBy(() -> findingWith("A.java", null, Category.TEST, "m", GENERAL))
        .withMessage("severity");
    assertThatNullPointerException()
        .isThrownBy(() -> findingWith("A.java", Severity.MAJOR, null, "m", GENERAL))
        .withMessage("category");
    assertThatNullPointerException()
        .isThrownBy(() -> findingWith("A.java", Severity.MAJOR, Category.TEST, null, GENERAL))
        .withMessage("message");
    assertThatNullPointerException()
        .isThrownBy(() -> findingWith("A.java", Severity.MAJOR, Category.TEST, "m", null))
        .withMessage("basis");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Finding(
                    "A.java",
                    1,
                    Severity.MAJOR,
                    Category.TEST,
                    "m",
                    null,
                    GENERAL,
                    null,
                    null,
                    null))
        .withMessage("verdict");
  }

  private static Finding findingWith(
      String file, Severity severity, Category category, String message, Basis basis) {
    return new Finding(
        file, 1, severity, category, message, null, basis, null, LineVerdict.INLINE_ELIGIBLE, null);
  }

  @Test
  void summaryOnlyVerdictRequiresReason() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Finding(
                    "A.java",
                    null,
                    Severity.MINOR,
                    Category.CONVENTION,
                    "m",
                    null,
                    GENERAL,
                    null,
                    LineVerdict.SUMMARY_ONLY,
                    null));
  }

  @Test
  void inlineEligibleVerdictRejectsReason() {
    assertThatIllegalArgumentException()
        .isThrownBy(
            () ->
                new Finding(
                    "A.java",
                    3,
                    Severity.MINOR,
                    Category.CONVENTION,
                    "m",
                    null,
                    GENERAL,
                    null,
                    LineVerdict.INLINE_ELIGIBLE,
                    SummaryOnlyReason.OUT_OF_RANGE));
  }

  @Test
  void usageRejectsNegativeTokenCounts() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Usage(-1, 0, 0, 0, "m", null))
        .withMessageContaining("inputTokens");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Usage(0, -1, 0, 0, "m", null))
        .withMessageContaining("outputTokens");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Usage(0, 0, -1, 0, "m", null))
        .withMessageContaining("cacheWriteTokens");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new Usage(0, 0, 0, -1, "m", null))
        .withMessageContaining("cacheReadTokens");
  }

  @Test
  void usageRequiresModelAndAllowsMissingCost() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Usage(0, 0, 0, 0, null, null))
        .withMessage("model");
    assertThat(USAGE.estimatedCostUsd()).isNull();
  }

  @Test
  void usageNormalizesCostToFourDecimalPlaces() {
    Usage one = new Usage(1, 2, 3, 4, "m", new BigDecimal("0.1"));
    Usage other = new Usage(1, 2, 3, 4, "m", new BigDecimal("0.1000"));

    assertThat(one.estimatedCostUsd().scale()).isEqualTo(4);
    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
  }

  @Test
  void usageRoundsCostHalfUpAtFifthDecimalPlace() {
    assertThat(new Usage(0, 0, 0, 0, "m", new BigDecimal("0.12345")).estimatedCostUsd())
        .isEqualTo(new BigDecimal("0.1235"));
    assertThat(new Usage(0, 0, 0, 0, "m", new BigDecimal("0.12344")).estimatedCostUsd())
        .isEqualTo(new BigDecimal("0.1234"));
    assertThat(new Usage(0, 0, 0, 0, "m", new BigDecimal("0.00005")).estimatedCostUsd())
        .isEqualTo(new BigDecimal("0.0001"));
  }

  @Test
  void instancesWithSameFieldValuesAreEqual() {
    ReviewResult one = complete(new ArrayList<>(List.of(finding("A.java"))));
    ReviewResult other = complete(List.of(finding("A.java")));

    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
  }
}
