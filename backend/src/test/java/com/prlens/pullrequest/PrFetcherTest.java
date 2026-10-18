package com.prlens.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.diff.DiffParser;
import com.prlens.github.FilePage;
import com.prlens.github.FilePage.Entry;
import com.prlens.github.PullRequestMeta;
import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PrFetcherTest {

  private static final RepoRef REPO = new RepoRef("octo", "repo");
  private static final int NUMBER = 7;
  private static final String VALID_PATCH = "@@ -0,0 +1,3 @@\n+a\n+b\n+c";

  private final List<Warning> warnings = new ArrayList<>();
  private FakeGitHubClient github;

  private PullRequestSnapshot fetch(PullRequestMeta meta, FilePage... pages) {
    github = new FakeGitHubClient(meta, List.of(pages));
    return new PrFetcher(github, warnings::add).fetch(REPO, NUMBER);
  }

  private static PullRequestMeta meta(int changedFiles) {
    return new PullRequestMeta("T", "B", "b1", "h1", changedFiles);
  }

  private static Entry entry(String filename, String status) {
    return new Entry(filename, null, status, 1, 0, null);
  }

  private static List<Entry> entries(String prefix, int count) {
    return IntStream.range(0, count).mapToObj(i -> entry(prefix + i, "modified")).toList();
  }

  private static List<String> paths(PullRequestSnapshot snapshot) {
    return snapshot.files().stream().map(ChangedFile::path).toList();
  }

  @Test
  void copiesMetadataIntoSnapshot() {
    PullRequestSnapshot snapshot =
        fetch(meta(1), new FilePage(List.of(entry("a.txt", "modified")), false));

    assertThat(snapshot.repo()).isEqualTo(REPO);
    assertThat(snapshot.number()).isEqualTo(NUMBER);
    assertThat(snapshot.title()).isEqualTo("T");
    assertThat(snapshot.body()).isEqualTo("B");
    assertThat(snapshot.baseSha()).isEqualTo("b1");
    assertThat(snapshot.headSha()).isEqualTo("h1");
    assertThat(snapshot.reportedChangedFiles()).isEqualTo(1);
  }

  @Test
  void nullBodyBecomesEmptyString() {
    PullRequestSnapshot snapshot =
        fetch(
            new PullRequestMeta("Title", null, "b1", "h1", 1),
            new FilePage(List.of(entry("a.txt", "modified")), false));

    assertThat(snapshot.title()).isEqualTo("Title");
    assertThat(snapshot.body()).isEmpty();
  }

  @Test
  void mapsEntryFields() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(1),
            new FilePage(
                List.of(new Entry("src/A.java", null, "added", 3, 0, VALID_PATCH)), false));

    assertThat(snapshot.files()).hasSize(1);
    ChangedFile file = snapshot.files().get(0);
    assertThat(file.path()).isEqualTo("src/A.java");
    assertThat(file.previousPath()).isNull();
    assertThat(file.status()).isEqualTo(FileStatus.ADDED);
    assertThat(file.additions()).isEqualTo(3);
    assertThat(file.deletions()).isZero();
    assertThat(file.patch())
        .isEqualTo(new PatchContent.Parsed(DiffParser.parse("src/A.java", VALID_PATCH)));
    assertThat(((PatchContent.Parsed) file.patch()).hunks()).hasSize(1);
  }

  @ParameterizedTest
  @CsvSource({
    "added, ADDED",
    "modified, MODIFIED",
    "changed, MODIFIED",
    "removed, REMOVED",
    "renamed, RENAMED",
    "copied, ADDED",
    "unchanged, MODIFIED"
  })
  void mapsGitHubStatuses(String gitHubStatus, FileStatus expected) {
    PullRequestSnapshot snapshot =
        fetch(meta(1), new FilePage(List.of(entry("a.txt", gitHubStatus)), false));

    assertThat(snapshot.files()).extracting(ChangedFile::status).containsExactly(expected);
    assertThat(warnings).isEmpty();
  }

  @Test
  void unknownStatusBecomesModifiedWithWarning() {
    PullRequestSnapshot snapshot =
        fetch(meta(1), new FilePage(List.of(entry("dir/a.txt", "weird")), false));

    assertThat(snapshot.files())
        .extracting(ChangedFile::status)
        .containsExactly(FileStatus.MODIFIED);
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("unknown_file_status");
    assertThat(warnings.get(0).message()).contains("dir/a.txt").contains("weird");
  }

  @Test
  void normalizesPaths() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(1),
            new FilePage(
                List.of(new Entry("/src/A.java", "./old/A.java", "renamed", 0, 0, null)), false));

    assertThat(snapshot.files()).hasSize(1);
    assertThat(snapshot.files().get(0).path()).isEqualTo("src/A.java");
    assertThat(snapshot.files().get(0).previousPath()).isEqualTo("old/A.java");
  }

  @Test
  void keepsPreviousPathForRenameAndCopy() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(3),
            new FilePage(
                List.of(
                    new Entry("new.txt", "old.txt", "renamed", 0, 0, null),
                    new Entry("copy.txt", "orig.txt", "copied", 0, 0, null),
                    new Entry("mod.txt", null, "modified", 0, 0, null)),
                false));

    assertThat(snapshot.files())
        .extracting(ChangedFile::previousPath)
        .containsExactly("old.txt", "orig.txt", null);
  }

  @Test
  void requestsAllPagesUntilNoNext() {
    List<Entry> first = entries("p1-", 100);
    List<Entry> second = entries("p2-", 50);

    PullRequestSnapshot snapshot =
        fetch(meta(150), new FilePage(first, true), new FilePage(second, false));

    List<String> expected = new ArrayList<>();
    first.forEach(e -> expected.add(e.filename()));
    second.forEach(e -> expected.add(e.filename()));
    assertThat(paths(snapshot)).containsExactlyElementsOf(expected);
    assertThat(github.requestedPages()).containsExactly(1, 2);
  }

  @Test
  void stopsWhenCountReachesChangedFiles() {
    fetch(
        meta(100), new FilePage(entries("p1-", 100), true), new FilePage(entries("p2-", 1), false));

    assertThat(github.requestedPages()).containsExactly(1);
  }

  @Test
  void stopsOnEmptyPage() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(150),
            new FilePage(entries("p1-", 100), true),
            new FilePage(List.of(), true),
            new FilePage(entries("p3-", 50), false));

    assertThat(github.requestedPages()).containsExactly(1, 2);
    assertThat(snapshot.files()).hasSize(100);
  }

  @Test
  void dropsDuplicateFilenamesKeepingFirst() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(3),
            new FilePage(
                List.of(
                    new Entry("a", null, "modified", 1, 0, null),
                    new Entry("b", null, "modified", 2, 0, null)),
                true),
            new FilePage(
                List.of(
                    new Entry("a", null, "modified", 9, 0, null),
                    new Entry("c", null, "modified", 3, 0, null)),
                false));

    assertThat(paths(snapshot)).containsExactly("a", "b", "c");
    assertThat(snapshot.files().get(0).additions()).isEqualTo(1);
  }

  @Test
  void absentPatchKeepsOtherFields() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(1),
            new FilePage(List.of(new Entry("bin.dat", null, "modified", 5, 2, null)), false));

    assertThat(snapshot.files()).hasSize(1);
    ChangedFile file = snapshot.files().get(0);
    assertThat(file.patch()).isEqualTo(new PatchContent.Absent());
    assertThat(file.path()).isEqualTo("bin.dat");
    assertThat(file.status()).isEqualTo(FileStatus.MODIFIED);
    assertThat(file.additions()).isEqualTo(5);
    assertThat(file.deletions()).isEqualTo(2);
  }

  @Test
  void emptyPatchIsParsedWithoutHunks() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(1), new FilePage(List.of(new Entry("empty.txt", null, "added", 0, 0, "")), false));

    assertThat(snapshot.files())
        .extracting(ChangedFile::patch)
        .containsExactly(new PatchContent.Parsed(List.of()));
    assertThat(warnings).isEmpty();
  }

  @Test
  void unparseablePatchBecomesUnparseableWithWarning() {
    PullRequestSnapshot snapshot =
        fetch(
            meta(1),
            new FilePage(
                List.of(new Entry("src/B.java", null, "modified", 1, 1, "hello\n@@ -1 +1 @@\n x")),
                false));

    assertThat(snapshot.files())
        .extracting(ChangedFile::patch)
        .containsExactly(new PatchContent.Unparseable("content_before_header", 1, 0));
    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("unparseable_patch");
    assertThat(warnings.get(0).message()).contains("src/B.java");
  }

  @Test
  void fewerFilesThanReportedIsKeptWithoutWarning() {
    PullRequestSnapshot snapshot = fetch(meta(5), new FilePage(entries("f", 3), false));

    assertThat(snapshot.reportedChangedFiles()).isEqualTo(5);
    assertThat(snapshot.files()).hasSize(3);
    assertThat(warnings).isEmpty();
  }
}
