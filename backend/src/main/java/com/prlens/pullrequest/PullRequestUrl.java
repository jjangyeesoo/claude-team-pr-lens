package com.prlens.pullrequest;

import com.prlens.model.RepoRef;
import java.util.Optional;

/** GitHub PR URL을 검증해 저장소와 PR 번호를 꺼낸다. 본문은 작업 7.2에서 구현한다. */
public final class PullRequestUrl {

  private PullRequestUrl() {}

  public static Optional<PullRequestUrl> parse(String url) {
    return Optional.empty();
  }

  public RepoRef repo() {
    return null;
  }

  public int number() {
    return 0;
  }
}
