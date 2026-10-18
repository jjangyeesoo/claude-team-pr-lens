package com.prlens.github;

import com.prlens.support.PrLensException;

/** GitHub API가 401, 403, 404 등으로 거절했다. */
public class GitHubApiException extends PrLensException {

  private final int status;

  public GitHubApiException(int status, String message) {
    super(message);
    this.status = status;
  }

  public int status() {
    return status;
  }
}
