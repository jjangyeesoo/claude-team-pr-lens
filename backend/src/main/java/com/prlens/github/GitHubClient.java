package com.prlens.github;

import com.prlens.model.RepoRef;

public interface GitHubClient {
  PullRequestMeta getPullRequest(RepoRef repo, int number);

  /** 페이지 크기는 100으로 고정이다. {@code page}는 1부터 시작한다. */
  FilePage listFiles(RepoRef repo, int number, int page);

  /**
   * {@code sha}의 재귀 트리. GitHub가 잘라서 준 트리는 {@link RepoTree#truncated()}로 알리고, 트리를 찾지 못하면(404) {@link
   * #getPullRequest}, {@link #listFiles}와 같이 예외를 던진다. 404를 결과 값으로 돌려주는 것은 {@link #getFile}뿐이다.
   */
  RepoTree getTree(RepoRef repo, String sha);

  FileFetch getFile(RepoRef repo, String path, String sha);
}
