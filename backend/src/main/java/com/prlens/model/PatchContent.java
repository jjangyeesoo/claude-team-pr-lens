package com.prlens.model;

import java.util.List;
import java.util.Objects;

public sealed interface PatchContent {
  record Parsed(List<Hunk> hunks) implements PatchContent {
    public Parsed {
      Objects.requireNonNull(hunks, "hunks");
      hunks = List.copyOf(hunks);
    }
  }

  record Absent() implements PatchContent {}

  record Unparseable(String errorKind, int lineNumber, int hunkIndex) implements PatchContent {
    public Unparseable {
      Objects.requireNonNull(errorKind, "errorKind");
    }
  }
}
