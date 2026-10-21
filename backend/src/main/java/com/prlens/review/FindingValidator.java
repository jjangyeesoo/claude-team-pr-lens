package com.prlens.review;

import com.prlens.diff.LineRanges;
import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.ContextFile;
import com.prlens.model.Demotion;
import com.prlens.model.Finding;
import com.prlens.model.LineVerdict;
import com.prlens.model.RepoPaths;
import com.prlens.model.ReviewContext;
import com.prlens.model.SummaryOnlyReason;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 원시 지적의 basis를 컨텍스트 경로로 검증하고(요구사항 11) 라인을 판정한다(요구사항 12).
 *
 * <p>{@code basis}, {@code demotion}, 판정 필드 외에는 바꾸지 않는다. 응답의 {@code file}은 이스케이프된 경로일 수 있어 비교할 때만
 * {@link DelimiterCodec#unescape}하고 결과의 {@code file}은 그대로 둔다(설계 G-13).
 */
final class FindingValidator {

  private FindingValidator() {}

  /**
   * @param targets 전체 Review_Target_File의 head 경로(이름 변경이면 새 경로) → 변경 줄 구간. Chunk가 아니라 파일 전체다
   */
  static List<Finding> apply(
      List<RawFinding> findings, ReviewContext context, Map<String, LineRanges> targets) {
    Set<String> contextPaths =
        context.files().stream().map(ContextFile::path).collect(Collectors.toSet());
    return findings.stream().map(raw -> validate(raw, contextPaths, targets)).toList();
  }

  private static Finding validate(
      RawFinding raw, Set<String> contextPaths, Map<String, LineRanges> targets) {
    Basis basis = new Basis(raw.basisType(), raw.basisRef());
    Demotion demotion = null;
    if (raw.basisType() != BasisType.GENERAL && !isInContext(raw.basisRef(), contextPaths)) {
      demotion = new Demotion(raw.basisType(), raw.basisRef());
      basis = new Basis(BasisType.GENERAL, null);
    }

    SummaryOnlyReason reason = summaryOnlyReason(raw, targets);
    return new Finding(
        raw.file(),
        raw.line(),
        raw.severity(),
        raw.category(),
        raw.message(),
        raw.suggestion(),
        basis,
        demotion,
        reason == null ? LineVerdict.INLINE_ELIGIBLE : LineVerdict.SUMMARY_ONLY,
        reason);
  }

  private static boolean isInContext(String ref, Set<String> contextPaths) {
    return ref != null && !ref.isBlank() && contextPaths.contains(RepoPaths.normalize(ref));
  }

  private static SummaryOnlyReason summaryOnlyReason(
      RawFinding raw, Map<String, LineRanges> targets) {
    LineRanges ranges = targets.get(RepoPaths.normalize(DelimiterCodec.unescape(raw.file())));
    if (ranges == null) {
      return SummaryOnlyReason.NOT_TARGET_FILE;
    }
    if (raw.line() == null) {
      return SummaryOnlyReason.LINE_MISSING;
    }
    return raw.line() >= 1 && ranges.contains(raw.line()) ? null : SummaryOnlyReason.OUT_OF_RANGE;
  }
}
