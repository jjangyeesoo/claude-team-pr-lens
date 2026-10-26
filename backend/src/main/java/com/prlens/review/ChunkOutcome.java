package com.prlens.review;

import com.prlens.model.Finding;
import com.prlens.model.IncompleteReason;
import com.prlens.model.Usage;
import java.util.List;
import java.util.Objects;

/**
 * Chunk 하나의 리뷰 결과. 엔진이 응답 해석, 검증, 비용 계산을 마친 뒤 {@link ResultMerger}에 넘긴다.
 *
 * @param index Chunk 번호. 1부터 시작한다. 단일·요약 전용 모드는 1이다
 * @param incompleteReason 이 Chunk가 불완전한 사유. complete이면 null
 * @param rawResponseExcerpt {@code SCHEMA_VIOLATION}일 때만 쓴다
 * @param usage 재시도를 모두 쓰고 실패한 Chunk는 null
 * @param failedStatus {@code CHUNK_FAILED}일 때 마지막 HTTP 상태 코드. 없으면 null
 */
record ChunkOutcome(
    int index,
    List<String> files,
    String summary,
    List<Finding> findings,
    IncompleteReason incompleteReason,
    String rawResponseExcerpt,
    Usage usage,
    Integer failedStatus) {

  ChunkOutcome {
    Objects.requireNonNull(files, "files");
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(findings, "findings");
    files = List.copyOf(files);
    findings = List.copyOf(findings);
  }

  static ChunkOutcome failed(int index, List<String> files, Integer lastStatus) {
    return new ChunkOutcome(
        index, files, "", List.of(), IncompleteReason.CHUNK_FAILED, null, null, lastStatus);
  }

  boolean isFailed() {
    return usage == null;
  }
}
