package com.prlens.model;

import java.util.Objects;

public record RepoRef(String owner, String name) {
  public RepoRef {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(name, "name");
  }
}
