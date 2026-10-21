package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ChangedFile;
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

/** {@link RuleCollector} (요구사항 5.1, 5.9, 5.11, 설계 G-9). */
class RuleCollectorTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");
  private static final String BASE = "base-sha";
  private static final PatchContent PARSED = new PatchContent.Parsed(List.of());

  private final List<Warning> warnings = new ArrayList<>();

  private static ChangedFile modified(String path) {
    return new ChangedFile(path, null, FileStatus.MODIFIED, 0, 0, PARSED);
  }

  private static ContextFile rule(String path, String content) {
    return new ContextFile(path, content, ContextSource.RULE, Revision.BASE);
  }

  private List<ContextFile> collect(FakeRepoClient github, RepoTree tree) {
    RepoTreeIndex index = RepoTreeIndex.of(tree);
    BaseFileReader reader = new BaseFileReader(github, REPO, BASE, index, warnings::add);
    return new RuleCollector(index, reader, warnings::add).collect(List.of(modified("src/A.java")));
  }

  @Test
  void collectsLowercaseMdUnderClaudeRulesAtAnyDepth() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, ".claude/rules/a.md", "# a\r\n")
            .file(BASE, ".claude/rules/web.md", "---\npaths: \"web/**\"\n---\n")
            .file(BASE, ".claude/rules/x/y/b.md", "# b\n")
            .file(BASE, ".claude/rules/c.MD", "# c\n")
            .file(BASE, ".claude/rules/d.txt", "d\n")
            .file(BASE, ".claude/other.md", "other\n");
    RepoTree tree =
        new RepoTree(
            List.of(
                new Entry(".claude", Kind.TREE),
                new Entry(".claude/other.md", Kind.BLOB),
                new Entry(".claude/rules", Kind.TREE),
                new Entry(".claude/rules/x/y/b.md", Kind.BLOB),
                new Entry(".claude/rules/x", Kind.TREE),
                new Entry(".claude/rules/x/y", Kind.TREE),
                new Entry(".claude/rules/c.MD", Kind.BLOB),
                new Entry(".claude/rules/d.txt", Kind.BLOB),
                new Entry(".claude/rules/web.md", Kind.BLOB),
                new Entry(".claude/rules/a.md", Kind.BLOB),
                new Entry("src/A.java", Kind.BLOB)),
            false);

    List<ContextFile> files = collect(github, tree);

    assertThat(files)
        .containsExactly(
            rule(".claude/rules/a.md", "# a\r\n"), rule(".claude/rules/x/y/b.md", "# b\n"));
    assertThat(github.fileRequests())
        .containsExactly(
            new FileRequest(".claude/rules/a.md", BASE),
            new FileRequest(".claude/rules/web.md", BASE),
            new FileRequest(".claude/rules/x/y/b.md", BASE));
    assertThat(warnings).isEmpty();
  }

  @Test
  void missingRulesFolderGivesNoRulesWithoutWarning() {
    FakeRepoClient github = new FakeRepoClient();
    RepoTree tree =
        new RepoTree(
            List.of(new Entry(".claude", Kind.TREE), new Entry("src/A.java", Kind.BLOB)), false);

    List<ContextFile> files = collect(github, tree);

    assertThat(files).isEqualTo(List.of());
    assertThat(github.fileRequests()).isEmpty();
    assertThat(warnings).isEmpty();
  }

  @Test
  void truncatedTreeSkipsRulesWithWarning() {
    FakeRepoClient github = new FakeRepoClient().file(BASE, ".claude/rules/a.md", "# a\n");
    RepoTree tree = new RepoTree(List.of(new Entry(".claude/rules/a.md", Kind.BLOB)), true);

    List<ContextFile> files = collect(github, tree);

    assertThat(warnings).extracting(Warning::code).containsExactly("rules_skipped_tree_truncated");
    assertThat(files).isEqualTo(List.of());
    assertThat(github.fileRequests()).isEmpty();
  }

  @Test
  void selectionWarningsAreSentToSink() {
    FakeRepoClient github =
        new FakeRepoClient().file(BASE, ".claude/rules/open.md", "---\npaths: \"web/**\"\n");
    RepoTree tree = new RepoTree(List.of(new Entry(".claude/rules/open.md", Kind.BLOB)), false);

    List<ContextFile> files = collect(github, tree);

    assertThat(warnings).extracting(Warning::code).containsExactly("frontmatter_unclosed");
    assertThat(warnings.get(0).message()).contains(".claude/rules/open.md");
    assertThat(files).containsExactly(rule(".claude/rules/open.md", "---\npaths: \"web/**\"\n"));
  }
}
