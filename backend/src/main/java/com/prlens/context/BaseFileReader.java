package com.prlens.context;

import com.prlens.github.GitHubClient;
import com.prlens.model.RepoRef;
import com.prlens.support.WarningSink;
import java.util.Optional;

/** base SHA의 파일 내용을 읽는다. 작업 8.1에서 구현한다. */
final class BaseFileReader {

  BaseFileReader(
      GitHubClient github,
      RepoRef repo,
      String baseSha,
      RepoTreeIndex index,
      WarningSink warnings) {}

  Optional<String> read(String path) {
    return null;
  }
}
