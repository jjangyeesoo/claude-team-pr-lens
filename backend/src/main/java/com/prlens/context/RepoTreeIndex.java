package com.prlens.context;

import com.prlens.github.GitHubClient;
import com.prlens.github.RepoTree;
import com.prlens.model.RepoRef;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * base SHA 재귀 트리로 경로마다 있는지 답한다. 트리가 잘렸으면(설계 G-9) 받은 항목도 쓰지 않고 모든 경로를 {@link Presence#UNKNOWN}으로 답해,
 * 읽는 쪽이 경로별 조회로 대체하게 한다.
 */
final class RepoTreeIndex {

  enum Presence {
    /** blob. */
    FILE,
    /** tree 또는 서브모듈(commit). contents API의 {@code IsDirectory}와 같게 본다. */
    DIRECTORY,
    ABSENT,
    /** 트리가 잘려 알 수 없다. */
    UNKNOWN
  }

  /** {@code null}이면 트리가 잘린 것이다. */
  private final Map<String, Presence> entries;

  private RepoTreeIndex(Map<String, Presence> entries) {
    this.entries = entries;
  }

  /** {@code sha}의 트리를 한 번 조회한다. 조회 예외는 그대로 전파한다. */
  static RepoTreeIndex load(GitHubClient github, RepoRef repo, String sha) {
    return of(github.getTree(repo, sha));
  }

  static RepoTreeIndex of(RepoTree tree) {
    if (tree.truncated()) {
      return new RepoTreeIndex(null);
    }
    Map<String, Presence> entries = new HashMap<>();
    for (RepoTree.Entry entry : tree.entries()) {
      entries.put(
          entry.path(), entry.kind() == RepoTree.Kind.BLOB ? Presence.FILE : Presence.DIRECTORY);
    }
    return new RepoTreeIndex(Map.copyOf(entries));
  }

  Presence lookup(String path) {
    if (entries == null) {
      return Presence.UNKNOWN;
    }
    return entries.getOrDefault(path, Presence.ABSENT);
  }

  /**
   * {@code folder} 아래 모든 깊이의 blob 경로를 {@link String#compareTo} 오름차순으로 돌려준다. 폴더가 없으면 빈 목록, 트리가 잘렸으면
   * {@link Optional#empty()}다.
   */
  Optional<List<String>> filesUnder(String folder) {
    if (entries == null) {
      return Optional.empty();
    }
    String prefix = folder + "/";
    return Optional.of(
        entries.entrySet().stream()
            .filter(e -> e.getValue() == Presence.FILE && e.getKey().startsWith(prefix))
            .map(Map.Entry::getKey)
            .sorted()
            .toList());
  }
}
