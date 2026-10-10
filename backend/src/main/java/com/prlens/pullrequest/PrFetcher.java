package com.prlens.pullrequest;

import com.prlens.github.GitHubClient;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.support.WarningSink;

/** PR 메타데이터와 변경 파일을 가져와 스냅샷을 만든다. 본문은 작업 7.4에서 구현한다. */
public final class PrFetcher {

  private final GitHubClient github;
  private final WarningSink warnings;

  public PrFetcher(GitHubClient github, WarningSink warnings) {
    this.github = github;
    this.warnings = warnings;
  }

  public PullRequestSnapshot fetch(RepoRef repo, int number) {
    throw new UnsupportedOperationException("작업 7.4에서 구현한다");
  }
}
