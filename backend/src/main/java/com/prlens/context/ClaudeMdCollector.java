package com.prlens.context;

import com.prlens.model.ChangedFile;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.FileStatus;
import com.prlens.model.RepoPaths;
import com.prlens.model.Revision;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * base SHA의 {@code CLAUDE.md}를 모은다(요구사항 4.1~4.7). 순서는 루트 {@code CLAUDE.md}, {@code
 * .claude/CLAUDE.md}, 그 뒤 대상 파일마다 새 경로의 조상 폴더(가까운 순, 루트 제외), 이름 변경이면 이전 경로의 조상 폴더다. 이미 본 후보는 다시
 * 조회하지 않는다.
 *
 * <p>입력은 필터의 대상 파일뿐이다(요구사항 4.5). 이전 경로는 {@link FileStatus#RENAMED}일 때만 쓴다. GitHub {@code copied}가
 * {@code ADDED}로 매핑된 파일의 {@code previousPath}(복사 원본)는 PR이 바꾸지 않은 파일이므로 탐색하지 않는다. 제외 판정에 {@code
 * previousPath}를 보는 {@code DiffFilter}와 다르다.
 */
final class ClaudeMdCollector {

  private static final String FILE_NAME = "CLAUDE.md";

  private final BaseFileReader reader;

  ClaudeMdCollector(BaseFileReader reader) {
    this.reader = reader;
  }

  List<ContextFile> collect(List<ChangedFile> targets) {
    Set<String> candidates = new LinkedHashSet<>();
    candidates.add(FILE_NAME);
    candidates.add(".claude/" + FILE_NAME);
    for (ChangedFile target : targets) {
      addAncestors(candidates, target.path());
      if (target.status() == FileStatus.RENAMED && target.previousPath() != null) {
        addAncestors(candidates, target.previousPath());
      }
    }
    List<ContextFile> files = new ArrayList<>();
    for (String candidate : candidates) {
      Optional<String> content = reader.read(candidate);
      content.ifPresent(
          text ->
              files.add(new ContextFile(candidate, text, ContextSource.CLAUDE_MD, Revision.BASE)));
    }
    return List.copyOf(files);
  }

  /** 루트를 뺀 조상 폴더의 {@code CLAUDE.md}를 가까운 순으로 더한다. */
  private static void addAncestors(Set<String> candidates, String path) {
    String normalized = RepoPaths.normalize(path);
    int slash = normalized.lastIndexOf('/');
    while (slash > 0) {
      String folder = normalized.substring(0, slash);
      candidates.add(folder + "/" + FILE_NAME);
      slash = folder.lastIndexOf('/');
    }
  }
}
