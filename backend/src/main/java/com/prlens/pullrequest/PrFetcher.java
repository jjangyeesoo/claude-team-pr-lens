package com.prlens.pullrequest;

import com.prlens.diff.DiffParseException;
import com.prlens.diff.DiffParser;
import com.prlens.github.FilePage;
import com.prlens.github.GitHubClient;
import com.prlens.github.PullRequestMeta;
import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoPaths;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * PR 메타데이터와 변경 파일을 가져와 스냅샷을 만든다.
 *
 * <p>파일 목록은 1페이지부터 차례로 요청하고, 중복을 뺀 누적 수가 메타데이터의 변경 파일 수에 이르거나, 빈 페이지가 오거나, 다음 페이지가 없으면 멈춘다. 같은
 * 경로(정규화 뒤)가 다시 오면 처음 온 항목을 남긴다. 파일 수가 메타데이터보다 적어도 경고하지 않는다(판정은 엔진, 경고는 CLI).
 */
public final class PrFetcher {

  private final GitHubClient github;
  private final WarningSink warnings;

  public PrFetcher(GitHubClient github, WarningSink warnings) {
    this.github = github;
    this.warnings = warnings;
  }

  public PullRequestSnapshot fetch(RepoRef repo, int number) {
    PullRequestMeta meta = github.getPullRequest(repo, number);
    Map<String, ChangedFile> files = new LinkedHashMap<>();
    int page = 1;
    FilePage current;
    do {
      current = github.listFiles(repo, number, page);
      for (FilePage.Entry entry : current.files()) {
        String path = RepoPaths.normalize(entry.filename());
        if (!files.containsKey(path)) {
          files.put(path, toChangedFile(path, entry));
        }
      }
      page++;
    } while (files.size() < meta.changedFiles() && !current.files().isEmpty() && current.hasNext());
    return new PullRequestSnapshot(
        repo,
        number,
        meta.title(),
        meta.body(),
        meta.baseSha(),
        meta.headSha(),
        meta.changedFiles(),
        List.copyOf(files.values()));
  }

  private ChangedFile toChangedFile(String path, FilePage.Entry entry) {
    String previousPath =
        entry.previousFilename() == null ? null : RepoPaths.normalize(entry.previousFilename());
    return new ChangedFile(
        path,
        previousPath,
        mapStatus(path, entry.status()),
        entry.additions(),
        entry.deletions(),
        toPatch(path, entry.patch()));
  }

  /** GitHub 상태 문자열을 바꾼다(design.md G-6). 표에 없는 문자열은 {@code MODIFIED}로 담고 경고한다. */
  private FileStatus mapStatus(String path, String status) {
    return switch (status) {
      case "added", "copied" -> FileStatus.ADDED;
      case "modified", "changed", "unchanged" -> FileStatus.MODIFIED;
      case "removed" -> FileStatus.REMOVED;
      case "renamed" -> FileStatus.RENAMED;
      default -> {
        warnings.warn(
            new Warning(
                "unknown_file_status",
                "알 수 없는 파일 상태라 modified로 처리합니다: " + path + " (" + status + ")"));
        yield FileStatus.MODIFIED;
      }
    };
  }

  /** {@code null}만 {@code Absent}다. 빈 문자열은 Hunk 없는 {@code Parsed}가 된다. */
  private PatchContent toPatch(String path, String patch) {
    if (patch == null) {
      return new PatchContent.Absent();
    }
    try {
      return new PatchContent.Parsed(DiffParser.parse(path, patch));
    } catch (DiffParseException e) {
      warnings.warn(new Warning("unparseable_patch", e.getMessage()));
      return new PatchContent.Unparseable(
          e.kind().name().toLowerCase(Locale.ROOT), e.lineNo(), e.hunkIndex());
    }
  }
}
