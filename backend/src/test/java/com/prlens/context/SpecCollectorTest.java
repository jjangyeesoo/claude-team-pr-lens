package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.RepoRef;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** {@link SpecCollector} (요구사항 7.2, 7.4, 7.6). */
class SpecCollectorTest {

  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final String BASE = "base-sha";
  private static final String HEAD = "head-sha";
  private static final String PATH = "docs/specs/a.md";

  private final List<Warning> warnings = new ArrayList<>();

  private List<ContextFile> collect(
      FakeRepoClient github, RepoTree tree, List<String> paths, Set<String> taken) {
    BaseFileReader reader =
        new BaseFileReader(github, REPO, BASE, RepoTreeIndex.of(tree), warnings::add);
    return new SpecCollector(github, REPO, HEAD, reader, warnings::add).collect(paths, taken);
  }

  private static RepoTree treeWith(String path, Kind kind) {
    return new RepoTree(List.of(new Entry(path, kind)), false);
  }

  @Test
  void collectReadsBaseShaFirstAndMarksBase() {
    FakeRepoClient github = new FakeRepoClient().file(BASE, PATH, "base 스펙");

    List<ContextFile> files = collect(github, treeWith(PATH, Kind.BLOB), List.of(PATH), Set.of());

    assertThat(files)
        .containsExactly(new ContextFile(PATH, "base 스펙", ContextSource.SPEC, Revision.BASE));
    assertThat(github.fileRequests()).containsExactly(new FileRequest(PATH, BASE));
  }

  @Test
  void collectFallsBackToHeadWhenBaseTreeLacksPathOrBaseReturns404() {
    FakeRepoClient absent = new FakeRepoClient().file(HEAD, PATH, "head 스펙");
    List<ContextFile> fromAbsent =
        collect(absent, new RepoTree(List.of(), false), List.of(PATH), Set.of());

    assertThat(fromAbsent)
        .containsExactly(new ContextFile(PATH, "head 스펙", ContextSource.SPEC, Revision.HEAD));
    assertThat(absent.fileRequests()).containsExactly(new FileRequest(PATH, HEAD));

    FakeRepoClient truncated = new FakeRepoClient().file(HEAD, PATH, "head 스펙");
    List<ContextFile> fromTruncated =
        collect(truncated, new RepoTree(List.of(), true), List.of(PATH), Set.of());

    assertThat(fromTruncated)
        .containsExactly(new ContextFile(PATH, "head 스펙", ContextSource.SPEC, Revision.HEAD));
    assertThat(truncated.fileRequests())
        .containsExactly(new FileRequest(PATH, BASE), new FileRequest(PATH, HEAD));
    assertThat(warnings).isEmpty();
  }

  @Test
  void collectWarnsWhenMissingAtBothBaseAndHead() {
    FakeRepoClient github = new FakeRepoClient();

    List<ContextFile> files =
        collect(github, new RepoTree(List.of(), true), List.of(PATH), Set.of());

    assertThat(files).isEmpty();
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("spec_not_found");
    assertThat(warnings.get(0).message()).contains(PATH);
  }

  @Test
  void collectWarnsDirectoryWithoutHeadLookupWhenBaseIsDirectory() {
    FakeRepoClient github = new FakeRepoClient().file(HEAD, PATH, "head");

    List<ContextFile> files = collect(github, treeWith(PATH, Kind.TREE), List.of(PATH), Set.of());

    assertThat(files).isEmpty();
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("spec_is_directory");
    assertThat(warnings.get(0).message()).contains(PATH);
    assertThat(github.fileRequests()).isEmpty();
  }

  @Test
  void collectWarnsDirectoryWhenHeadIsDirectory() {
    FakeRepoClient github = new FakeRepoClient().directory(HEAD, PATH);

    List<ContextFile> files =
        collect(github, new RepoTree(List.of(), false), List.of(PATH), Set.of());

    assertThat(files).isEmpty();
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("spec_is_directory");
  }

  @Test
  void collectDoesNotRetryHeadWhenBaseIsTooLarge() {
    FakeRepoClient github = new FakeRepoClient().tooLarge(BASE, PATH).file(HEAD, PATH, "head");

    List<ContextFile> files = collect(github, treeWith(PATH, Kind.BLOB), List.of(PATH), Set.of());

    assertThat(files).isEmpty();
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("context_file_too_large");
    assertThat(github.fileRequests()).containsExactly(new FileRequest(PATH, BASE));
  }

  @Test
  void collectSkipsTakenPathsWithoutRequestOrWarning() {
    String other = "docs/specs/b.md";
    FakeRepoClient github = new FakeRepoClient().file(BASE, PATH, "x").file(BASE, other, "b 스펙");
    RepoTree tree =
        new RepoTree(List.of(new Entry(PATH, Kind.BLOB), new Entry(other, Kind.BLOB)), false);

    List<ContextFile> files = collect(github, tree, List.of(PATH, other), Set.of(PATH));

    assertThat(files)
        .containsExactly(new ContextFile(other, "b 스펙", ContextSource.SPEC, Revision.BASE));
    assertThat(github.fileRequests()).containsExactly(new FileRequest(other, BASE));
    assertThat(warnings).isEmpty();
  }
}
