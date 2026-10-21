package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.RepoTreeIndex.Presence;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.RepoRef;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link RepoTreeIndex} (요구사항 4.1, 4.9, 설계 G-9). */
class RepoTreeIndexTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");

  private static final List<Entry> ENTRIES =
      List.of(
          new Entry("CLAUDE.md", Kind.BLOB),
          new Entry("src", Kind.TREE),
          new Entry("src/CLAUDE.md", Kind.BLOB),
          new Entry("vendor/lib", Kind.COMMIT),
          new Entry(".claude", Kind.TREE),
          new Entry(".claude/rules", Kind.TREE),
          new Entry(".claude/rules/z.md", Kind.BLOB),
          new Entry(".claude/rules/api", Kind.TREE),
          new Entry(".claude/rules/api/b.md", Kind.BLOB),
          new Entry(".claude/rules/a.md", Kind.BLOB),
          new Entry(".claude/rules/notes.txt", Kind.BLOB),
          new Entry(".claude/rules/sub", Kind.COMMIT),
          new Entry(".claude/rulesx/c.md", Kind.BLOB));

  @Test
  void loadRequestsTreeOfGivenShaOnce() {
    FakeRepoClient github =
        new FakeRepoClient()
            .tree("base", new RepoTree(ENTRIES, false))
            .tree("head", new RepoTree(List.of(new Entry("HEAD_ONLY.md", Kind.BLOB)), false));

    RepoTreeIndex index = RepoTreeIndex.load(github, REPO, "base");

    assertThat(github.treeRequests()).containsExactly("base");
    assertThat(index.lookup("CLAUDE.md")).isEqualTo(Presence.FILE);
    assertThat(index.lookup("HEAD_ONLY.md")).isEqualTo(Presence.ABSENT);
  }

  @Test
  void lookupMapsBlobToFileAndTreeOrSubmoduleToDirectory() {
    RepoTreeIndex index = RepoTreeIndex.of(new RepoTree(ENTRIES, false));

    assertThat(index.lookup("CLAUDE.md")).isEqualTo(Presence.FILE);
    assertThat(index.lookup("src/CLAUDE.md")).isEqualTo(Presence.FILE);
    assertThat(index.lookup("src")).isEqualTo(Presence.DIRECTORY);
    assertThat(index.lookup("vendor/lib")).isEqualTo(Presence.DIRECTORY);
  }

  @Test
  void lookupReportsMissingPathAsAbsent() {
    RepoTreeIndex index = RepoTreeIndex.of(new RepoTree(ENTRIES, false));

    assertThat(index.lookup("lib/CLAUDE.md")).isEqualTo(Presence.ABSENT);
    assertThat(index.lookup("claude.md")).isEqualTo(Presence.ABSENT);
    assertThat(index.lookup(".claude/CLAUDE.md")).isEqualTo(Presence.ABSENT);
  }

  @Test
  void truncatedTreeReportsEveryPathUnknown() {
    RepoTreeIndex index = RepoTreeIndex.of(new RepoTree(ENTRIES, true));

    assertThat(index.lookup("CLAUDE.md")).isEqualTo(Presence.UNKNOWN);
    assertThat(index.lookup("src")).isEqualTo(Presence.UNKNOWN);
    assertThat(index.lookup("vendor/lib")).isEqualTo(Presence.UNKNOWN);
    assertThat(index.lookup("lib/CLAUDE.md")).isEqualTo(Presence.UNKNOWN);
  }

  @Test
  void filesUnderListsBlobsAtAnyDepthSortedAndIsEmptyWhenTruncated() {
    RepoTreeIndex index = RepoTreeIndex.of(new RepoTree(ENTRIES, false));

    assertThat(index.filesUnder(".claude/rules"))
        .isEqualTo(
            Optional.of(
                List.of(
                    ".claude/rules/a.md",
                    ".claude/rules/api/b.md",
                    ".claude/rules/notes.txt",
                    ".claude/rules/z.md")));
    assertThat(index.filesUnder("docs")).isEqualTo(Optional.of(List.of()));
    assertThat(RepoTreeIndex.of(new RepoTree(ENTRIES, true)).filesUnder(".claude/rules"))
        .isEqualTo(Optional.empty());
  }
}
