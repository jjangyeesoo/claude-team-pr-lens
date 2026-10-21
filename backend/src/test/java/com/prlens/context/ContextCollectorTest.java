package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoPaths;
import com.prlens.model.RepoRef;
import com.prlens.model.ReviewContext;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link ContextCollector}의 조립 (요구사항 4.1, 4.5, 4.8, 6.7, 7.4, 8.2, 8.5). */
class ContextCollectorTest {

  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final String BASE = "b1";
  private static final String HEAD = "h1";

  private final List<Warning> warnings = new ArrayList<>();

  private static RepoTree treeOf(String... paths) {
    List<Entry> entries = new ArrayList<>();
    for (String path : paths) {
      entries.add(new Entry(path, Kind.BLOB));
    }
    return new RepoTree(entries, false);
  }

  private static ChangedFile modified(String path) {
    return new ChangedFile(
        path, null, FileStatus.MODIFIED, 0, 0, new PatchContent.Parsed(List.of()));
  }

  private static PullRequestSnapshot snapshot(String body, ChangedFile... files) {
    return new PullRequestSnapshot(REPO, 1, "t", body, BASE, HEAD, files.length, List.of(files));
  }

  private ReviewContext collect(
      FakeRepoClient github, PullRequestSnapshot snapshot, Configuration config) {
    return new ContextCollector(github, warnings::add).collect(snapshot, config);
  }

  @Test
  void collectGathersClaudeMdRuleImportSpecInOrder() {
    FakeRepoClient github =
        new FakeRepoClient()
            .tree(
                BASE, treeOf("CLAUDE.md", "docs/guide.md", ".claude/rules/a.md", "docs/specs/p.md"))
            .file(BASE, "CLAUDE.md", "@docs/guide.md")
            .file(BASE, "docs/guide.md", "guide")
            .file(BASE, ".claude/rules/a.md", "rule")
            .file(BASE, "docs/specs/p.md", "spec");

    ReviewContext context =
        collect(
            github,
            snapshot("see docs/specs/p.md", modified("src/A.java")),
            Configuration.defaults());

    assertThat(context.files())
        .extracting(ContextFile::source)
        .containsExactly(
            ContextSource.CLAUDE_MD, ContextSource.RULE, ContextSource.IMPORT, ContextSource.SPEC);
    assertThat(context.files()).extracting(ContextFile::revision).containsOnly(Revision.BASE);
    assertThat(context.files())
        .extracting(ContextFile::path)
        .allSatisfy(p -> assertThat(RepoPaths.normalize(p)).isEqualTo(p));
  }

  @Test
  void collectKeepsExistingSourceWhenImportOrSpecPointsToCollectedPath() {
    FakeRepoClient github =
        new FakeRepoClient()
            .tree(BASE, treeOf("CLAUDE.md", ".claude/rules/a.md", "docs/specs/p.md"))
            .file(BASE, "CLAUDE.md", "@.claude/rules/a.md @docs/specs/p.md")
            .file(BASE, ".claude/rules/a.md", "rule")
            .file(BASE, "docs/specs/p.md", "spec");

    ReviewContext context =
        collect(
            github, snapshot("docs/specs/p.md", modified("src/A.java")), Configuration.defaults());

    assertThat(context.files())
        .extracting(ContextFile::path, ContextFile::source)
        .containsExactlyInAnyOrder(
            tuple("CLAUDE.md", ContextSource.CLAUDE_MD),
            tuple(".claude/rules/a.md", ContextSource.RULE),
            tuple("docs/specs/p.md", ContextSource.IMPORT));
    assertThat(github.fileRequests())
        .filteredOn(r -> r.path().equals(".claude/rules/a.md"))
        .hasSize(1);
    assertThat(github.fileRequests())
        .filteredOn(r -> r.path().equals("docs/specs/p.md"))
        .hasSize(1);
  }

  @Test
  void collectRequestsTreeOnceAndBaseFilesAtBaseSha() {
    FakeRepoClient github =
        new FakeRepoClient()
            .tree(BASE, treeOf("CLAUDE.md"))
            .file(BASE, "CLAUDE.md", "root")
            .file(HEAD, "docs/specs/new.md", "head spec");

    ReviewContext context =
        collect(
            github,
            snapshot("docs/specs/new.md", modified("src/A.java")),
            Configuration.defaults());

    assertThat(github.treeRequests()).containsExactly(BASE);
    assertThat(github.fileRequests())
        .filteredOn(r -> !r.path().equals("docs/specs/new.md"))
        .allSatisfy(r -> assertThat(r.sha()).isEqualTo(BASE));
    assertThat(github.fileRequests()).contains(new FileRequest("docs/specs/new.md", HEAD));
    assertThat(context.files())
        .anySatisfy(
            f -> {
              assertThat(f.source()).isEqualTo(ContextSource.SPEC);
              assertThat(f.revision()).isEqualTo(Revision.HEAD);
            });
  }

  @Test
  void collectUsesOnlyFilterTargetsAndForwardsFilterWarnings() {
    Configuration defaults = Configuration.defaults();
    Configuration config =
        new Configuration(
            List.of("web/**"),
            List.of("**/.env*"),
            defaults.sizeLimit(),
            defaults.model(),
            defaults.effort(),
            defaults.maxOutputTokens(),
            defaults.maxRetries(),
            defaults.maxCostUsdPerReview(),
            defaults.pricing());
    FakeRepoClient github =
        new FakeRepoClient()
            .tree(BASE, treeOf("CLAUDE.md", "web/CLAUDE.md"))
            .file(BASE, "CLAUDE.md", "root")
            .file(BASE, "web/CLAUDE.md", "web");

    ReviewContext context = collect(github, snapshot("", modified("web/x.java")), config);

    assertThat(context.files()).extracting(ContextFile::path).containsExactly("CLAUDE.md");
    assertThat(github.fileRequests()).noneMatch(r -> r.path().equals("web/CLAUDE.md"));
    assertThat(warnings).hasSize(1);
  }

  @Test
  void collectForwardsSpecExtractionWarnings() {
    FakeRepoClient github = new FakeRepoClient().tree(BASE, treeOf("CLAUDE.md"));

    collect(
        github,
        snapshot("https://github.com/x/y/blob/main/docs/specs/a.md", modified("src/A.java")),
        Configuration.defaults());

    assertThat(warnings).extracting(Warning::code).containsExactly("spec_link_other_repo");
  }
}
