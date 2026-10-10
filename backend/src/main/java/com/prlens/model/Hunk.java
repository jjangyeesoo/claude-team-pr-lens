package com.prlens.model;

import java.util.List;
import java.util.Objects;

/**
 * @param sectionHeading Hunk 헤더의 닫는 {@code @@} 뒤 문맥 텍스트. 없으면 {@code ""}
 */
public record Hunk(
    int baseStart,
    int baseCount,
    int headStart,
    int headCount,
    String sectionHeading,
    List<DiffLine> lines) {
  public Hunk {
    Objects.requireNonNull(sectionHeading, "sectionHeading");
    Objects.requireNonNull(lines, "lines");
    lines = List.copyOf(lines);
  }
}
