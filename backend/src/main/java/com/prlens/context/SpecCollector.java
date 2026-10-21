package com.prlens.context;

import com.prlens.context.BaseFileReader.Outcome;
import com.prlens.github.FileFetch;
import com.prlens.github.GitHubClient;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.RepoRef;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 스펙 경로의 내용을 읽는다. base를 먼저 보고, base에 없을 때({@link Outcome.Missing})만 head SHA로 다시 조회한다(결정 대기 D-7의
 * 제안값). head는 base 트리 색인을 거치지 않는다.
 */
final class SpecCollector {

  private final GitHubClient github;
  private final RepoRef repo;
  private final String headSha;
  private final BaseFileReader reader;
  private final WarningSink warnings;

  SpecCollector(
      GitHubClient github,
      RepoRef repo,
      String headSha,
      BaseFileReader reader,
      WarningSink warnings) {
    this.github = github;
    this.repo = repo;
    this.headSha = headSha;
    this.reader = reader;
    this.warnings = warnings;
  }

  /** {@code taken}에 있는 경로는 요청도 경고도 없이 건너뛴다. */
  List<ContextFile> collect(List<String> paths, Set<String> taken) {
    List<ContextFile> files = new ArrayList<>();
    for (String path : paths) {
      if (taken.contains(path)) {
        continue;
      }
      Outcome outcome = reader.fetch(path);
      if (outcome instanceof Outcome.Found found) {
        files.add(new ContextFile(path, found.content(), ContextSource.SPEC, Revision.BASE));
      } else if (outcome instanceof Outcome.Directory) {
        warnDirectory(path);
      } else if (outcome instanceof Outcome.Missing) {
        readHead(path, files);
      }
    }
    return files;
  }

  private void readHead(String path, List<ContextFile> files) {
    FileFetch fetch = github.getFile(repo, path, headSha);
    if (fetch instanceof FileFetch.Found found) {
      files.add(new ContextFile(path, found.content(), ContextSource.SPEC, Revision.HEAD));
    } else if (fetch instanceof FileFetch.IsDirectory) {
      warnDirectory(path);
    } else if (fetch instanceof FileFetch.TooLarge) {
      warnings.warn(new Warning("context_file_too_large", "1MB를 넘는 컨텍스트 파일을 건너뜁니다: " + path));
    } else {
      warnings.warn(new Warning("spec_not_found", "스펙 파일을 찾을 수 없어 건너뜁니다: " + path));
    }
  }

  private void warnDirectory(String path) {
    warnings.warn(new Warning("spec_is_directory", "스펙 경로가 폴더라서 건너뜁니다: " + path));
  }
}
