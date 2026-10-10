package com.prlens.model;

import java.util.Objects;

/**
 * 라인 판정까지 끝난 지적 한 건. {@code line}, {@code suggestion}, {@code demotion}, {@code summaryOnlyReason}은
 * null일 수 있다.
 *
 * <p>불변식: {@code verdict == SUMMARY_ONLY}와 {@code summaryOnlyReason != null}은 같은 뜻이다.
 */
public record Finding(
    String file,
    Integer line,
    Severity severity,
    Category category,
    String message,
    String suggestion,
    Basis basis,
    Demotion demotion,
    LineVerdict verdict,
    SummaryOnlyReason summaryOnlyReason) {
  public Finding {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(basis, "basis");
    Objects.requireNonNull(verdict, "verdict");
    if ((verdict == LineVerdict.SUMMARY_ONLY) != (summaryOnlyReason != null)) {
      throw new IllegalArgumentException(
          "verdict와 summaryOnlyReason이 맞지 않습니다: " + verdict + ", " + summaryOnlyReason);
    }
  }
}
