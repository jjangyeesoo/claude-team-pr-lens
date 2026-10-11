package com.prlens.context;

import com.prlens.github.GitHubClient;
import com.prlens.model.Configuration;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.ReviewContext;
import com.prlens.support.WarningSink;

/** base SHA 기준으로 팀 컨텍스트를 모은다. 본문은 작업 8.11에서 조립한다. */
public final class ContextCollector {

  private final GitHubClient github;
  private final WarningSink warnings;

  public ContextCollector(GitHubClient github, WarningSink warnings) {
    this.github = github;
    this.warnings = warnings;
  }

  public ReviewContext collect(PullRequestSnapshot snapshot, Configuration config) {
    throw new UnsupportedOperationException("작업 8.11에서 구현한다");
  }
}
