package com.prlens.github;

import java.util.Objects;

/**
 * @param body GitHub가 준 그대로. null일 수 있다
 * @param changedFiles 메타데이터상 변경 파일 수
 */
public record PullRequestMeta(
    String title, String body, String baseSha, String headSha, int changedFiles) {
  public PullRequestMeta {
    Objects.requireNonNull(title, "title");
    Objects.requireNonNull(baseSha, "baseSha");
    Objects.requireNonNull(headSha, "headSha");
  }
}
