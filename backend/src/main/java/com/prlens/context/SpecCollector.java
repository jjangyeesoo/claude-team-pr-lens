package com.prlens.context;

import com.prlens.github.GitHubClient;
import com.prlens.model.ContextFile;
import com.prlens.model.RepoRef;
import com.prlens.support.WarningSink;
import java.util.List;
import java.util.Set;

/** 스펙 경로의 내용을 base에서, 없으면 head에서 읽는다. */
final class SpecCollector {

  SpecCollector(
      GitHubClient github,
      RepoRef repo,
      String headSha,
      BaseFileReader reader,
      WarningSink warnings) {}

  List<ContextFile> collect(List<String> paths, Set<String> taken) {
    return List.of();
  }
}
