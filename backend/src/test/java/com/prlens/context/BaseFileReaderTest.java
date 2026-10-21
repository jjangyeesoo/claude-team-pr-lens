package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.BaseFileReader.Outcome;
import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link BaseFileReader} (요구사항 4.1, 4.9, 설계 G-12). */
class BaseFileReaderTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");
  private static final String BASE = "base-sha";
  private static final String HEAD = "head-sha";

  private final List<Warning> warnings = new ArrayList<>();

  private BaseFileReader reader(FakeRepoClient github, RepoTree tree) {
    return new BaseFileReader(github, REPO, BASE, RepoTreeIndex.of(tree), warnings::add);
  }

  @Test
  void readReturnsBaseShaContent() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "CLAUDE.md", "base 내용\r\n")
            .file(HEAD, "CLAUDE.md", "head 내용\n");
    RepoTree tree = new RepoTree(List.of(new Entry("CLAUDE.md", Kind.BLOB)), false);

    Optional<String> content = reader(github, tree).read("CLAUDE.md");

    assertThat(content).isEqualTo(Optional.of("base 내용\r\n"));
    assertThat(github.fileRequests()).containsExactly(new FileRequest("CLAUDE.md", BASE));
    assertThat(warnings).isEmpty();
  }

  @Test
  void readAnswersAbsentAndDirectoryFromTreeWithoutRequest() {
    FakeRepoClient github = new FakeRepoClient().file(BASE, "lib/CLAUDE.md", "트리에 없으므로 읽지 않음");
    RepoTree tree =
        new RepoTree(
            List.of(
                new Entry("src", Kind.TREE),
                new Entry("src/CLAUDE.md", Kind.TREE),
                new Entry("vendor/CLAUDE.md", Kind.COMMIT)),
            false);
    BaseFileReader reader = reader(github, tree);

    assertThat(reader.read("lib/CLAUDE.md")).isEqualTo(Optional.empty());
    assertThat(reader.read("src/CLAUDE.md")).isEqualTo(Optional.empty());
    assertThat(reader.read("vendor/CLAUDE.md")).isEqualTo(Optional.empty());
    assertThat(github.fileRequests()).isEmpty();
    assertThat(warnings).isEmpty();
  }

  @Test
  void readFallsBackToContentsApiWhenTreeTruncated() {
    FakeRepoClient github = new FakeRepoClient().file(BASE, "lib/CLAUDE.md", "lib 규칙\n");
    RepoTree truncated = new RepoTree(List.of(new Entry("src", Kind.TREE)), true);

    Optional<String> content = reader(github, truncated).read("lib/CLAUDE.md");

    assertThat(content).isEqualTo(Optional.of("lib 규칙\n"));
    assertThat(github.fileRequests()).containsExactly(new FileRequest("lib/CLAUDE.md", BASE));
    assertThat(warnings).isEmpty();
  }

  @Test
  void readSkipsNotFoundAndDirectoryResponsesWithoutWarning() {
    FakeRepoClient github = new FakeRepoClient().directory(BASE, "src/CLAUDE.md");
    RepoTree truncated = new RepoTree(List.of(), true);
    BaseFileReader reader = reader(github, truncated);

    assertThat(reader.read("lib/CLAUDE.md")).isEqualTo(Optional.empty());
    assertThat(reader.read("src/CLAUDE.md")).isEqualTo(Optional.empty());
    assertThat(github.fileRequests())
        .containsExactly(
            new FileRequest("lib/CLAUDE.md", BASE), new FileRequest("src/CLAUDE.md", BASE));
    assertThat(warnings).isEmpty();
  }

  @Test
  void readWarnsOnceAndSkipsTooLargeFile() {
    FakeRepoClient github = new FakeRepoClient().tooLarge(BASE, "docs/CLAUDE.md");
    RepoTree tree = new RepoTree(List.of(new Entry("docs/CLAUDE.md", Kind.BLOB)), false);

    Optional<String> content = reader(github, tree).read("docs/CLAUDE.md");

    assertThat(content).isEqualTo(Optional.empty());
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("context_file_too_large");
    assertThat(warnings.get(0).message()).contains("docs/CLAUDE.md");
  }

  @Test
  void fetchDistinguishesMissingDirectoryAndTooLarge() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "a.md", "내용")
            .tooLarge(BASE, "big.md")
            .directory(BASE, "dir-resp");
    RepoTree tree =
        new RepoTree(
            List.of(
                new Entry("a.md", Kind.BLOB),
                new Entry("big.md", Kind.BLOB),
                new Entry("src", Kind.TREE)),
            false);
    BaseFileReader exact = reader(github, tree);

    assertThat(exact.fetch("lib/x.md")).isEqualTo(new Outcome.Missing());
    assertThat(exact.fetch("src")).isEqualTo(new Outcome.Directory());
    assertThat(github.fileRequests()).isEmpty();
    assertThat(exact.fetch("a.md")).isEqualTo(new Outcome.Found("내용"));
    assertThat(warnings).isEmpty();
    assertThat(exact.fetch("big.md")).isEqualTo(new Outcome.TooLarge());
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("context_file_too_large");

    BaseFileReader truncated = reader(github, new RepoTree(List.of(), true));
    assertThat(truncated.fetch("none.md")).isEqualTo(new Outcome.Missing());
    assertThat(truncated.fetch("dir-resp")).isEqualTo(new Outcome.Directory());
    assertThat(github.fileRequests())
        .containsExactly(
            new FileRequest("a.md", BASE),
            new FileRequest("big.md", BASE),
            new FileRequest("none.md", BASE),
            new FileRequest("dir-resp", BASE));
  }
}
