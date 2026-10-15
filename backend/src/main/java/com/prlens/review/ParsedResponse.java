package com.prlens.review;

import com.prlens.llm.LlmUsage;
import com.prlens.model.IncompleteReason;
import java.util.List;
import java.util.Objects;

/**
 * 응답 해석 결과. {@code incompleteReason}이 null이면 complete이다. {@code rawResponseExcerpt}는
 * schema_violation일 때만 있다(원본 앞 2,000자).
 */
record ParsedResponse(
    String summary,
    List<RawFinding> findings,
    IncompleteReason incompleteReason,
    String rawResponseExcerpt,
    LlmUsage usage) {

  ParsedResponse {
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(usage, "usage");
    findings = List.copyOf(findings);
  }
}
