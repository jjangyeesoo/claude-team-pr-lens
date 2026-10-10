package com.prlens.model;

import java.util.Objects;

public record ContextFile(String path, String content, ContextSource source, Revision revision) {
  public ContextFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(content, "content");
    Objects.requireNonNull(source, "source");
    Objects.requireNonNull(revision, "revision");
  }
}
