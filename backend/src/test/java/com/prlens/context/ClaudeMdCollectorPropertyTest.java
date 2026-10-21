package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ChangedFile;
import com.prlens.model.ContextFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.RepoRef;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * {@link ClaudeMdCollector}의 속성 테스트 (design.md Property 13). 제외 파일은 수집기에 넘기지 않는 파일로 나타낸다. {@code
 * .claude} 폴더 이름은 쓰지 않으므로 수집 결과의 루트 아닌 항목은 모두 상위 폴더 후보다.
 */
class ClaudeMdCollectorPropertyTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");
  private static final String BASE = "base-sha";
  private static final PatchContent PARSED = new PatchContent.Parsed(List.of());

  /** 저장소 상태와 PR 파일. {@code claudeFolders}는 {@code CLAUDE.md}가 있는 루트 아닌 폴더다. */
  record Scenario(
      List<ChangedFile> targets,
      List<ChangedFile> excluded,
      Set<String> claudeFolders,
      boolean rootClaudeMd,
      boolean truncated) {}

  // Feature: pr-lens-p1-cli, Property 13: 상위 폴더 CLAUDE.md 후보
  @Property(tries = 200)
  void ancestorClaudeMdCandidates(@ForAll("scenarios") Scenario scenario) {
    FakeRepoClient github = new FakeRepoClient();
    List<Entry> entries = new ArrayList<>();
    if (scenario.rootClaudeMd()) {
      github.file(BASE, "CLAUDE.md", "root\n");
      entries.add(new Entry("CLAUDE.md", Kind.BLOB));
    }
    for (String folder : scenario.claudeFolders()) {
      github.file(BASE, folder + "/CLAUDE.md", folder + "\n");
      entries.add(new Entry(folder + "/CLAUDE.md", Kind.BLOB));
    }
    RepoTree tree = new RepoTree(entries, scenario.truncated());
    BaseFileReader reader =
        new BaseFileReader(github, REPO, BASE, RepoTreeIndex.of(tree), warning -> {});

    List<ContextFile> files = new ClaudeMdCollector(reader).collect(scenario.targets());

    Set<String> expected = new TreeSet<>();
    for (ChangedFile target : scenario.targets()) {
      List<String> paths = new ArrayList<>(List.of(target.path()));
      if (target.status() == FileStatus.RENAMED) {
        paths.add(target.previousPath());
      }
      for (String path : paths) {
        for (String folder : ancestors(path)) {
          if (scenario.claudeFolders().contains(folder)) {
            expected.add(folder + "/CLAUDE.md");
          }
        }
      }
    }
    assertThat(files).extracting(ContextFile::path).doesNotHaveDuplicates();
    assertThat(files)
        .extracting(ContextFile::path)
        .filteredOn(path -> !path.equals("CLAUDE.md"))
        .containsExactlyInAnyOrderElementsOf(expected);
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<List<ChangedFile>> targets = changedFiles().list().ofMinSize(1).ofMaxSize(6);
    Arbitrary<List<ChangedFile>> excluded = changedFiles().list().ofMaxSize(4);
    return Combinators.combine(
            targets, excluded, Arbitraries.of(true, false), Arbitraries.of(true, false))
        .flatAs(
            (t, e, root, truncated) -> {
              Set<String> folders = new LinkedHashSet<>();
              for (ChangedFile file : concat(t, e)) {
                folders.addAll(ancestors(file.path()));
                if (file.previousPath() != null) {
                  folders.addAll(ancestors(file.previousPath()));
                }
              }
              Arbitrary<Set<String>> chosen =
                  folders.isEmpty() ? Arbitraries.just(Set.of()) : Arbitraries.subsetOf(folders);
              return chosen.map(c -> new Scenario(t, e, c, root, truncated));
            });
  }

  private static List<ChangedFile> concat(List<ChangedFile> a, List<ChangedFile> b) {
    List<ChangedFile> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }

  private static Arbitrary<ChangedFile> changedFiles() {
    Arbitrary<String> kind = Arbitraries.of("MODIFIED", "ADDED", "REMOVED", "RENAMED", "COPIED");
    return Combinators.combine(kind, filePaths(), filePaths())
        .as(
            (k, path, previous) ->
                switch (k) {
                  case "RENAMED" ->
                      new ChangedFile(path, previous, FileStatus.RENAMED, 0, 0, PARSED);
                  case "COPIED" -> new ChangedFile(path, previous, FileStatus.ADDED, 0, 0, PARSED);
                  default -> new ChangedFile(path, null, FileStatus.valueOf(k), 0, 0, PARSED);
                });
  }

  /** 폴더 {@code a}, {@code b}, {@code c}로 된 깊이 0~3의 파일 경로. */
  private static Arbitrary<String> filePaths() {
    Arbitrary<List<String>> folders = Arbitraries.of("a", "b", "c").list().ofMaxSize(3);
    Arbitrary<String> name = Arbitraries.of("X.java", "Y.md", "Z.txt");
    return Combinators.combine(folders, name)
        .as(
            (segments, file) -> {
              List<String> parts = new ArrayList<>(segments);
              parts.add(file);
              return String.join("/", parts);
            });
  }

  /** 루트를 뺀 조상 폴더. */
  private static List<String> ancestors(String path) {
    List<String> result = new ArrayList<>();
    int slash = path.lastIndexOf('/');
    while (slash > 0) {
      String folder = path.substring(0, slash);
      result.add(folder);
      slash = folder.lastIndexOf('/');
    }
    return result;
  }
}
