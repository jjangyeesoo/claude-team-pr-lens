package com.prlens.output;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ChunkIssue;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Severity;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.List;

/** output 패키지 테스트가 함께 쓰는 작은 결과 생성 도우미. */
final class ResultFixtures {

  static final Usage USAGE =
      new Usage(21_000, 3_100, 500, 9_000, "claude-opus-5-5", new BigDecimal("0.1478"));
  static final ReviewStats SINGLE = new ReviewStats(ReviewMode.SINGLE, 120, 400, 1);

  private ResultFixtures() {}

  static Finding inline(String file, int line, Severity severity) {
    return new Finding(
        file,
        line,
        severity,
        Category.CONVENTION,
        "메시지 " + file + ":" + line,
        null,
        new Basis(BasisType.GENERAL, null),
        null,
        LineVerdict.INLINE_ELIGIBLE,
        null);
  }

  static Finding summaryOnly(String file, Integer line, SummaryOnlyReason reason) {
    return new Finding(
        file,
        line,
        Severity.MINOR,
        Category.DESIGN,
        "밖 " + file,
        null,
        new Basis(BasisType.GENERAL, null),
        null,
        LineVerdict.SUMMARY_ONLY,
        reason);
  }

  static ReviewResult complete(String summary, List<Finding> findings) {
    return new ReviewResult(
        ResultStatus.COMPLETE,
        summary,
        findings,
        List.of(),
        List.of(),
        USAGE,
        List.of(),
        new IncompleteDetails(List.of(), null),
        SINGLE);
  }

  static ReviewResult withExcluded(ReviewResult base, List<ExcludedFile> excluded) {
    return new ReviewResult(
        base.status(),
        base.summary(),
        base.findings(),
        excluded.stream().map(ExcludedFile::path).toList(),
        excluded,
        base.usage(),
        base.incompleteReasons(),
        base.incompleteDetails(),
        base.stats());
  }

  static ReviewResult withUsage(ReviewResult base, Usage usage) {
    return new ReviewResult(
        base.status(),
        base.summary(),
        base.findings(),
        base.excludedFiles(),
        base.excludedFileDetails(),
        usage,
        base.incompleteReasons(),
        base.incompleteDetails(),
        base.stats());
  }

  static ReviewResult withStats(ReviewResult base, ReviewStats stats) {
    return new ReviewResult(
        base.status(),
        base.summary(),
        base.findings(),
        base.excludedFiles(),
        base.excludedFileDetails(),
        base.usage(),
        base.incompleteReasons(),
        base.incompleteDetails(),
        stats);
  }

  static ReviewResult incomplete(List<Finding> findings) {
    return new ReviewResult(
        ResultStatus.INCOMPLETE,
        "부분 요약",
        findings,
        List.of(),
        List.of(),
        USAGE,
        List.of(IncompleteReason.CHUNK_FAILED, IncompleteReason.FILES_TRUNCATED),
        new IncompleteDetails(
            List.of(
                new ChunkIssue(
                    2,
                    List.of("web/a.tsx", "web/b.tsx"),
                    IncompleteReason.CHUNK_FAILED,
                    529,
                    null)),
            new FileCountGap(3100, 3000)),
        new ReviewStats(ReviewMode.SPLIT, 812, 400, 3));
  }
}
