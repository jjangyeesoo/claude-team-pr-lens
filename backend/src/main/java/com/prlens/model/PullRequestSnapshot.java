package com.prlens.model;

import java.util.List;
import java.util.Objects;

/**
 * PR 한 건의 스냅샷 (T2 출력).
 *
 * @param body GitHub가 null로 준 본문은 {@code ""}로 기록한다
 * @param reportedChangedFiles 메타데이터상 변경 파일 수. {@code files}보다 크면 목록이 잘린 것이다
 */
public record PullRequestSnapshot(
    RepoRef repo,
    int number,
    String title,
    String body,
    String baseSha,
    String headSha,
    int reportedChangedFiles,
    List<ChangedFile> files) {
  public PullRequestSnapshot {
    Objects.requireNonNull(repo, "repo");
    Objects.requireNonNull(title, "title");
    body = body == null ? "" : body;
    Objects.requireNonNull(baseSha, "baseSha");
    Objects.requireNonNull(headSha, "headSha");
    Objects.requireNonNull(files, "files");
    files = List.copyOf(files);
  }
}
