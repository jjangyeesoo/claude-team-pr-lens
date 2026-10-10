package com.prlens.github;

import java.util.List;
import java.util.Objects;

/**
 * 한 커밋의 재귀 트리.
 *
 * @param truncated GitHub가 트리를 잘라서 준 경우. 이때 {@code entries}는 전체가 아니다
 */
public record RepoTree(List<Entry> entries, boolean truncated) {
  public RepoTree {
    Objects.requireNonNull(entries, "entries");
    entries = List.copyOf(entries);
  }

  public record Entry(String path, Kind kind) {
    public Entry {
      Objects.requireNonNull(path, "path");
      Objects.requireNonNull(kind, "kind");
    }
  }

  /** {@code COMMIT}은 서브모듈이다. */
  public enum Kind {
    BLOB,
    TREE,
    COMMIT
  }
}
