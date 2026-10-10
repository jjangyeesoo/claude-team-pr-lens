package com.prlens.model;

import java.util.Objects;

public record ReviewStats(ReviewMode mode, int changedLineCount, int sizeLimit, int chunkCount) {
  public ReviewStats {
    Objects.requireNonNull(mode, "mode");
  }
}
