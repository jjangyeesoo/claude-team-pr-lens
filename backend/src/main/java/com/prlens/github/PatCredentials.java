package com.prlens.github;

import com.prlens.model.RepoRef;
import java.util.Objects;

/** Personal Access Token 자격 증명. 기본 {@code toString}이 토큰을 드러내므로 record로 두지 않는다. */
public final class PatCredentials implements GitHubCredentials {

  private final String token;

  public PatCredentials(String token) {
    this.token = Objects.requireNonNull(token, "token");
  }

  @Override
  public String authorizationHeader(RepoRef repo) {
    return "Bearer " + token;
  }

  @Override
  public String toString() {
    return "PatCredentials[***]";
  }
}
