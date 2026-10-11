package com.prlens.github;

import com.prlens.model.RepoRef;

/** P1은 PAT, P2는 GitHub App installation token 구현을 끼운다. */
public interface GitHubCredentials {
  String authorizationHeader(RepoRef repo);
}
