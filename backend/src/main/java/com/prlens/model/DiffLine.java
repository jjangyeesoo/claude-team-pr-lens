package com.prlens.model;

import java.util.Objects;

public record DiffLine(LineKind kind, String content, boolean noNewlineAtEnd) {
  public DiffLine {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(content, "content");
  }
}
