package com.prlens.model;

import java.util.List;
import java.util.Objects;

/**
 * @param lastStatusCode 마지막 HTTP 상태 코드. 없으면 null
 * @param rawResponseExcerpt {@code SCHEMA_VIOLATION}일 때 원본 응답의 앞 2,000자. 그 밖에는 null
 */
public record ChunkIssue(
    int chunkIndex,
    List<String> files,
    IncompleteReason reason,
    Integer lastStatusCode,
    String rawResponseExcerpt) {
  public ChunkIssue {
    Objects.requireNonNull(files, "files");
    Objects.requireNonNull(reason, "reason");
    files = List.copyOf(files);
  }
}
