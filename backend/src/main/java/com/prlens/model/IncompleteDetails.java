package com.prlens.model;

import java.util.List;
import java.util.Objects;

/**
 * @param fileCountGap 파일 목록이 잘렸을 때만 있다. 그 밖에는 null
 */
public record IncompleteDetails(List<ChunkIssue> chunks, FileCountGap fileCountGap) {
  public IncompleteDetails {
    Objects.requireNonNull(chunks, "chunks");
    chunks = List.copyOf(chunks);
  }
}
