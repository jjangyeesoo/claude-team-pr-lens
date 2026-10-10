package com.prlens.github;

import com.prlens.model.RepoRef;
import com.prlens.support.RetryExecutor;
import java.net.http.HttpClient;

/** GitHub REST API 어댑터. 본문은 작업 7.1에서 구현한다. */
public final class HttpGitHubClient implements GitHubClient {

  private final HttpClient http;
  private final GitHubCredentials credentials;
  private final RetryExecutor retry;

  public HttpGitHubClient(HttpClient http, GitHubCredentials credentials, RetryExecutor retry) {
    this.http = http;
    this.credentials = credentials;
    this.retry = retry;
  }

  @Override
  public PullRequestMeta getPullRequest(RepoRef repo, int number) {
    throw new UnsupportedOperationException("작업 7.1에서 구현한다");
  }

  @Override
  public FilePage listFiles(RepoRef repo, int number, int page) {
    throw new UnsupportedOperationException("작업 7.1에서 구현한다");
  }

  @Override
  public RepoTree getTree(RepoRef repo, String sha) {
    throw new UnsupportedOperationException("작업 7.1에서 구현한다");
  }

  @Override
  public FileFetch getFile(RepoRef repo, String path, String sha) {
    throw new UnsupportedOperationException("작업 7.1에서 구현한다");
  }
}
