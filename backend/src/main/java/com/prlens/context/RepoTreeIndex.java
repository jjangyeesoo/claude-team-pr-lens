package com.prlens.context;

import com.prlens.github.GitHubClient;
import com.prlens.github.RepoTree;
import com.prlens.model.RepoRef;
import java.util.List;
import java.util.Optional;

/** base SHA 재귀 트리로 경로마다 있는지 답한다. 작업 8.1에서 구현한다. */
final class RepoTreeIndex {

  enum Presence {
    FILE,
    DIRECTORY,
    ABSENT,
    UNKNOWN
  }

  static RepoTreeIndex load(GitHubClient github, RepoRef repo, String sha) {
    return new RepoTreeIndex();
  }

  static RepoTreeIndex of(RepoTree tree) {
    return new RepoTreeIndex();
  }

  Presence lookup(String path) {
    return null;
  }

  Optional<List<String>> filesUnder(String folder) {
    return null;
  }
}
