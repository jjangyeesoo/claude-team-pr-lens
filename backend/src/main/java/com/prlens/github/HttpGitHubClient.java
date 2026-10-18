package com.prlens.github;

import com.prlens.model.RepoRef;
import com.prlens.support.RetryExecutor;
import com.prlens.support.SecretMasker;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.List;
import java.util.function.Consumer;

/** GitHub REST API 어댑터. */
public final class HttpGitHubClient implements GitHubClient {

  /**
   * @param debugLog null이면 디버그 로그를 쓰지 않는다
   */
  public record Options(URI apiBase, Clock clock, SecretMasker masker, Consumer<String> debugLog) {
    public static Options defaults() {
      return new Options(
          URI.create("https://api.github.com"),
          Clock.systemUTC(),
          new SecretMasker(List.of()),
          null);
    }
  }

  private final HttpClient http;
  private final GitHubCredentials credentials;
  private final RetryExecutor retry;
  private final Options options;

  public HttpGitHubClient(HttpClient http, GitHubCredentials credentials, RetryExecutor retry) {
    this(http, credentials, retry, Options.defaults());
  }

  public HttpGitHubClient(
      HttpClient http, GitHubCredentials credentials, RetryExecutor retry, Options options) {
    this.http = http;
    this.credentials = credentials;
    this.retry = retry;
    this.options = options;
  }

  @Override
  public PullRequestMeta getPullRequest(RepoRef repo, int number) {
    return null;
  }

  @Override
  public FilePage listFiles(RepoRef repo, int number, int page) {
    return new FilePage(List.of(), false);
  }

  @Override
  public RepoTree getTree(RepoRef repo, String sha) {
    return new RepoTree(List.of(), false);
  }

  @Override
  public FileFetch getFile(RepoRef repo, String path, String sha) {
    return new FileFetch.NotFound();
  }
}
