package com.prlens.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** `GitHubClient`가 돌려주는 타입 (design.md "공유 경계"). */
class GitHubTypesTest {

  private static final FilePage.Entry ENTRY =
      new FilePage.Entry("a.java", null, "modified", 1, 0, null);
  private static final RepoTree.Entry BLOB = new RepoTree.Entry("a.java", RepoTree.Kind.BLOB);

  @Test
  void filePageCopiesFilesAndRejectsModification() {
    List<FilePage.Entry> source = new ArrayList<>(List.of(ENTRY));
    FilePage page = new FilePage(source, true);

    source.clear();

    assertThat(page.files()).containsExactly(ENTRY);
    assertThat(page.hasNext()).isTrue();
    assertThatThrownBy(() -> page.files().add(ENTRY))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void filePageEntryAllowsMissingPatchAndPreviousFilename() {
    assertThat(ENTRY.patch()).isNull();
    assertThat(ENTRY.previousFilename()).isNull();
    assertThatNullPointerException()
        .isThrownBy(() -> new FilePage.Entry(null, null, "added", 0, 0, null))
        .withMessage("filename");
    assertThatNullPointerException()
        .isThrownBy(() -> new FilePage.Entry("a.java", null, null, 0, 0, null))
        .withMessage("status");
    assertThatNullPointerException()
        .isThrownBy(() -> new FilePage(null, false))
        .withMessage("files");
  }

  // 같은 타입이 나란한 구성 요소는 순서가 바뀌어도 컴파일되므로 값으로 고정한다
  @Test
  void filePageEntryKeepsComponentOrder() {
    FilePage.Entry renamed =
        new FilePage.Entry("new.java", "old.java", "renamed", 3, 1, "@@ -1 +1,3 @@");

    assertThat(renamed.filename()).isEqualTo("new.java");
    assertThat(renamed.previousFilename()).isEqualTo("old.java");
    assertThat(renamed.status()).isEqualTo("renamed");
    assertThat(renamed.additions()).isEqualTo(3);
    assertThat(renamed.deletions()).isEqualTo(1);
    assertThat(renamed.patch()).isEqualTo("@@ -1 +1,3 @@");
  }

  @Test
  void repoTreeCopiesEntriesAndRejectsModification() {
    List<RepoTree.Entry> source = new ArrayList<>(List.of(BLOB));
    RepoTree tree = new RepoTree(source, true);

    source.clear();

    assertThat(tree.entries()).containsExactly(BLOB);
    assertThat(tree.truncated()).isTrue();
    assertThatThrownBy(() -> tree.entries().add(BLOB))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void repoTreeRequiresEntriesPathAndKind() {
    assertThatNullPointerException()
        .isThrownBy(() -> new RepoTree(null, false))
        .withMessage("entries");
    assertThatNullPointerException()
        .isThrownBy(() -> new RepoTree.Entry(null, RepoTree.Kind.TREE))
        .withMessage("path");
    assertThatNullPointerException()
        .isThrownBy(() -> new RepoTree.Entry("docs", null))
        .withMessage("kind");
  }

  @Test
  void pullRequestMetaKeepsNullBodyForFetcherToNormalize() {
    PullRequestMeta meta = new PullRequestMeta("제목", null, "base", "head", 3);

    assertThat(meta.body()).isNull();
    assertThat(meta.changedFiles()).isEqualTo(3);
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestMeta(null, "", "base", "head", 0))
        .withMessage("title");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestMeta("t", "", null, "head", 0))
        .withMessage("baseSha");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestMeta("t", "", "base", null, 0))
        .withMessage("headSha");
  }

  @Test
  void fileFetchPermitsFourOutcomes() {
    assertThat(FileFetch.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(
            FileFetch.Found.class,
            FileFetch.NotFound.class,
            FileFetch.IsDirectory.class,
            FileFetch.TooLarge.class);
    assertThatNullPointerException()
        .isThrownBy(() -> new FileFetch.Found(null))
        .withMessage("content");
  }
}
