package com.prlens.context;

import com.prlens.github.FileFetch;
import com.prlens.github.GitHubClient;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.util.Optional;

/**
 * base SHA의 파일 내용을 읽는다. 트리로 없거나 폴더인 것을 알면 요청하지 않고, 파일이거나 알 수 없을 때만 contents API를 부른다. 없는 파일과 폴더는 경고
 * 없이 건너뛰고(요구사항 4.9), 1MB 초과 파일은 경고 후 건너뛴다(설계 G-12).
 */
final class BaseFileReader {

  private final GitHubClient github;
  private final RepoRef repo;
  private final String baseSha;
  private final RepoTreeIndex index;
  private final WarningSink warnings;

  BaseFileReader(
      GitHubClient github,
      RepoRef repo,
      String baseSha,
      RepoTreeIndex index,
      WarningSink warnings) {
    this.github = github;
    this.repo = repo;
    this.baseSha = baseSha;
    this.index = index;
    this.warnings = warnings;
  }

  /** 조회 결과. */
  sealed interface Outcome {
    record Found(String content) implements Outcome {}

    record Missing() implements Outcome {}

    record Directory() implements Outcome {}

    record TooLarge() implements Outcome {}
  }

  Outcome fetch(String path) {
    return new Outcome.Missing();
  }

  /** 내용은 받은 그대로 돌려준다(줄바꿈을 바꾸지 않는다). */
  Optional<String> read(String path) {
    switch (index.lookup(path)) {
      case ABSENT, DIRECTORY -> {
        return Optional.empty();
      }
      case FILE, UNKNOWN -> {}
    }
    FileFetch fetch = github.getFile(repo, path, baseSha);
    if (fetch instanceof FileFetch.Found found) {
      return Optional.of(found.content());
    }
    if (fetch instanceof FileFetch.TooLarge) {
      warnings.warn(new Warning("context_file_too_large", "1MB를 넘는 컨텍스트 파일을 건너뜁니다: " + path));
    }
    return Optional.empty();
  }
}
