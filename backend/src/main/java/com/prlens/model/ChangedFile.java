package com.prlens.model;

import java.util.Objects;

/**
 * @param previousPath 이름 변경일 때의 이전 경로. 그 밖에는 null
 */
public record ChangedFile(
    String path,
    String previousPath,
    FileStatus status,
    int additions,
    int deletions,
    PatchContent patch) {
  public ChangedFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(patch, "patch");
  }
}
