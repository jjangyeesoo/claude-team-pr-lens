package com.prlens.github;

import com.prlens.model.RepoRef;

/** Personal Access Token 자격 증명. */
public final class PatCredentials implements GitHubCredentials {

  private final String token;

  public PatCredentials(String token) {
    this.token = token;
  }

  @Override
  public String authorizationHeader(RepoRef repo) {
    return null;
  }
}
