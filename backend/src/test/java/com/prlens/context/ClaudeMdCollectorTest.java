package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.filter.DiffFilter;
import com.prlens.filter.FilterOutcome;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.RepoRef;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ClaudeMdCollector} (요구사항 4.1~4.7, 4.9, 4.10). */
class ClaudeMdCollectorTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");
  private static final String BASE = "base-sha";
  private static final String HEAD = "head-sha";
  private static final PatchContent PARSED = new PatchContent.Parsed(List.of());

  private final List<Warning> warnings = new ArrayList<>();

  private static ChangedFile modified(String path) {
    return new ChangedFile(path, null, FileStatus.MODIFIED, 0, 0, PARSED);
  }

  private static ContextFile claudeMd(String path, String content) {
    return new ContextFile(path, content, ContextSource.CLAUDE_MD, Revision.BASE);
  }

  /** 주어진 경로를 모두 blob으로 담은 잘리지 않은 트리. */
  private static RepoTree treeOf(String... paths) {
    List<Entry> entries = new ArrayList<>();
    for (String path : paths) {
      entries.add(new Entry(path, Kind.BLOB));
    }
    return new RepoTree(entries, false);
  }

  private List<ContextFile> collect(
      FakeRepoClient github, RepoTree tree, List<ChangedFile> targets) {
    BaseFileReader reader =
        new BaseFileReader(github, REPO, BASE, RepoTreeIndex.of(tree), warnings::add);
    return new ClaudeMdCollector(reader).collect(targets);
  }

  @Test
  void collectsRootAndDotClaudeClaudeMdInOrder() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "CLAUDE.md", "루트\n")
            .file(BASE, ".claude/CLAUDE.md", "닷클로드\n");
    RepoTree tree = treeOf("CLAUDE.md", ".claude/CLAUDE.md", "Main.java");

    List<ContextFile> files = collect(github, tree, List.of(modified("Main.java")));

    assertThat(files)
        .containsExactly(claudeMd("CLAUDE.md", "루트\n"), claudeMd(".claude/CLAUDE.md", "닷클로드\n"));
    assertThat(warnings).isEmpty();
  }

  @Test
  void dotClaudeClaudeMdRequiresExactCase() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, ".claude/claude.md", "소문자\n")
            .file(BASE, ".Claude/CLAUDE.md", "대문자 폴더\n");
    RepoTree tree = treeOf(".claude/claude.md", ".Claude/CLAUDE.md", "Main.java");

    List<ContextFile> files = collect(github, tree, List.of(modified("Main.java")));

    assertThat(files).isEqualTo(List.of());
    assertThat(warnings).isEmpty();
  }

  @Test
  void collectsAncestorClaudeMdNearestFirstExcludingRoot() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "backend/CLAUDE.md", "backend\n")
            .file(BASE, "backend/src/CLAUDE.md", "src\n");
    RepoTree tree = treeOf("backend/CLAUDE.md", "backend/src/CLAUDE.md", "backend/src/Foo.java");

    List<ContextFile> files = collect(github, tree, List.of(modified("backend/src/Foo.java")));

    assertThat(files)
        .containsExactly(
            claudeMd("backend/src/CLAUDE.md", "src\n"), claudeMd("backend/CLAUDE.md", "backend\n"));
  }

  @Test
  void excludedFileFoldersAreNotSearched() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "src/CLAUDE.md", "src\n")
            .file(BASE, "img/CLAUDE.md", "img\n")
            .file(BASE, "web/CLAUDE.md", "web\n");
    RepoTree tree =
        treeOf(
            "src/CLAUDE.md",
            "img/CLAUDE.md",
            "web/CLAUDE.md",
            "src/A.java",
            "img/logo.png",
            "web/package-lock.json");
    FilterOutcome outcome =
        DiffFilter.apply(
            List.of(
                modified("img/logo.png"),
                modified("src/A.java"),
                modified("web/package-lock.json")),
            Configuration.defaults());

    List<ContextFile> files = collect(github, tree, outcome.targets());

    assertThat(files).containsExactly(claudeMd("src/CLAUDE.md", "src\n"));
  }

  @Test
  void renamedFileSearchesNewAndPreviousPathAncestors() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "new/CLAUDE.md", "new\n")
            .file(BASE, "old/CLAUDE.md", "old\n")
            .file(BASE, "old/pkg/CLAUDE.md", "old pkg\n");
    RepoTree tree = treeOf("new/CLAUDE.md", "old/CLAUDE.md", "old/pkg/CLAUDE.md");
    ChangedFile renamed =
        new ChangedFile("new/Foo.java", "old/pkg/Foo.java", FileStatus.RENAMED, 0, 0, PARSED);

    List<ContextFile> files = collect(github, tree, List.of(renamed));

    assertThat(files)
        .containsExactly(
            claudeMd("new/CLAUDE.md", "new\n"),
            claudeMd("old/pkg/CLAUDE.md", "old pkg\n"),
            claudeMd("old/CLAUDE.md", "old\n"));
  }

  @Test
  void copiedFileDoesNotSearchCopySourceAncestors() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "copy/CLAUDE.md", "copy\n")
            .file(BASE, "origin/CLAUDE.md", "origin\n");
    RepoTree tree = treeOf("copy/CLAUDE.md", "origin/CLAUDE.md", "origin/Foo.java");
    ChangedFile copied =
        new ChangedFile("copy/Foo.java", "origin/Foo.java", FileStatus.ADDED, 0, 0, PARSED);

    List<ContextFile> files = collect(github, tree, List.of(copied));

    assertThat(files).containsExactly(claudeMd("copy/CLAUDE.md", "copy\n"));
  }

  @Test
  void sharedAncestorIsCollectedAndRequestedOnce() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "app/CLAUDE.md", "app\n")
            .file(BASE, "app/a/CLAUDE.md", "a\n");
    RepoTree truncated = new RepoTree(List.of(), true);

    List<ContextFile> files =
        collect(
            github,
            truncated,
            List.of(
                modified("app/a/One.java"),
                modified("app/b/Two.java"),
                modified("app/a/Three.java")));

    assertThat(files)
        .containsExactly(claudeMd("app/a/CLAUDE.md", "a\n"), claudeMd("app/CLAUDE.md", "app\n"));
    assertThat(github.fileRequests())
        .containsExactly(
            new FileRequest("CLAUDE.md", BASE),
            new FileRequest(".claude/CLAUDE.md", BASE),
            new FileRequest("app/a/CLAUDE.md", BASE),
            new FileRequest("app/CLAUDE.md", BASE),
            new FileRequest("app/b/CLAUDE.md", BASE));
  }

  @Test
  void usesBaseContentWhenPrModifiesClaudeMd() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "backend/CLAUDE.md", "base 규칙\r\n")
            .file(HEAD, "backend/CLAUDE.md", "head 규칙\n");
    RepoTree tree = treeOf("backend/CLAUDE.md");

    List<ContextFile> files = collect(github, tree, List.of(modified("backend/CLAUDE.md")));

    assertThat(files).containsExactly(claudeMd("backend/CLAUDE.md", "base 규칙\r\n"));
    assertThat(github.fileRequests()).allMatch(request -> request.sha().equals(BASE));
  }

  @Test
  void missingCandidatesAreSkippedWithoutWarning() {
    FakeRepoClient github = new FakeRepoClient();
    RepoTree truncated = new RepoTree(List.of(), true);

    List<ContextFile> files = collect(github, truncated, List.of(modified("x/y/Z.java")));

    assertThat(files).isEqualTo(List.of());
    assertThat(github.fileRequests())
        .containsExactly(
            new FileRequest("CLAUDE.md", BASE),
            new FileRequest(".claude/CLAUDE.md", BASE),
            new FileRequest("x/y/CLAUDE.md", BASE),
            new FileRequest("x/CLAUDE.md", BASE));
    assertThat(warnings).isEmpty();
  }
}
