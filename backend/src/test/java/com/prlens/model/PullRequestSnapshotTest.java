package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** PR 스냅샷 타입 (요구사항 1.2, 17.4~17.8). */
class PullRequestSnapshotTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");

  private static ChangedFile file(String path) {
    return new ChangedFile(path, null, FileStatus.MODIFIED, 1, 0, new PatchContent.Absent());
  }

  private static PullRequestSnapshot snapshot(String body, List<ChangedFile> files) {
    return new PullRequestSnapshot(REPO, 7, "제목", body, "base", "head", files.size(), files);
  }

  private static Hunk hunk(List<DiffLine> lines) {
    return new Hunk(1, 0, 1, lines.size(), "", lines);
  }

  @Test
  void nullBodyBecomesEmptyString() {
    assertThat(snapshot(null, List.of()).body()).isEmpty();
  }

  @Test
  void filesAreCopiedDefensively() {
    List<ChangedFile> source = new ArrayList<>(List.of(file("a.java")));
    PullRequestSnapshot snapshot = snapshot("", source);

    source.add(file("b.java"));

    assertThat(snapshot.files()).containsExactly(file("a.java"));
  }

  @Test
  void exposedFilesRejectModification() {
    PullRequestSnapshot snapshot = snapshot("", new ArrayList<>(List.of(file("a.java"))));

    assertThatThrownBy(() -> snapshot.files().add(file("b.java")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullElementInFilesIsRejected() {
    assertThatNullPointerException()
        .isThrownBy(() -> snapshot("", Arrays.asList(file("a.java"), null)));
  }

  @Test
  void hunkLinesAreCopiedAndUnmodifiable() {
    DiffLine added = new DiffLine(LineKind.ADDED, "x", false);
    List<DiffLine> source = new ArrayList<>(List.of(added));
    Hunk hunk = hunk(source);

    source.clear();

    assertThat(hunk.lines()).containsExactly(added);
    assertThatThrownBy(() -> hunk.lines().add(added))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void parsedHunksAreCopiedAndUnmodifiable() {
    Hunk hunk = hunk(List.of(new DiffLine(LineKind.ADDED, "x", false)));
    List<Hunk> source = new ArrayList<>(List.of(hunk));
    PatchContent.Parsed parsed = new PatchContent.Parsed(source);

    source.clear();

    assertThat(parsed.hunks()).containsExactly(hunk);
    assertThatThrownBy(() -> parsed.hunks().add(hunk))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullRequiredFieldIsRejectedWithFieldName() {
    List<ChangedFile> none = List.of();
    PatchContent absent = new PatchContent.Absent();

    assertThatNullPointerException()
        .isThrownBy(() -> new RepoRef(null, "shop"))
        .withMessage("owner");
    assertThatNullPointerException()
        .isThrownBy(() -> new RepoRef("acme", null))
        .withMessage("name");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestSnapshot(null, 7, "t", "", "b", "h", 0, none))
        .withMessage("repo");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestSnapshot(REPO, 7, null, "", "b", "h", 0, none))
        .withMessage("title");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestSnapshot(REPO, 7, "t", "", null, "h", 0, none))
        .withMessage("baseSha");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestSnapshot(REPO, 7, "t", "", "b", null, 0, none))
        .withMessage("headSha");
    assertThatNullPointerException()
        .isThrownBy(() -> new PullRequestSnapshot(REPO, 7, "t", "", "b", "h", 0, null))
        .withMessage("files");
    assertThatNullPointerException()
        .isThrownBy(() -> new ChangedFile(null, null, FileStatus.ADDED, 0, 0, absent))
        .withMessage("path");
    assertThatNullPointerException()
        .isThrownBy(() -> new ChangedFile("a", null, null, 0, 0, absent))
        .withMessage("status");
    assertThatNullPointerException()
        .isThrownBy(() -> new ChangedFile("a", null, FileStatus.ADDED, 0, 0, null))
        .withMessage("patch");
    assertThatNullPointerException()
        .isThrownBy(() -> new PatchContent.Parsed(null))
        .withMessage("hunks");
    assertThatNullPointerException()
        .isThrownBy(() -> new PatchContent.Unparseable(null, 1, 1))
        .withMessage("errorKind");
    assertThatNullPointerException()
        .isThrownBy(() -> new Hunk(1, 1, 1, 1, null, List.of()))
        .withMessage("sectionHeading");
    assertThatNullPointerException()
        .isThrownBy(() -> new Hunk(1, 1, 1, 1, "", null))
        .withMessage("lines");
    assertThatNullPointerException()
        .isThrownBy(() -> new DiffLine(null, "x", false))
        .withMessage("kind");
    assertThatNullPointerException()
        .isThrownBy(() -> new DiffLine(LineKind.ADDED, null, false))
        .withMessage("content");
  }

  @Test
  void previousPathMayBeNullAndIsKeptForRenames() {
    ChangedFile renamed =
        new ChangedFile(
            "new.java", "old.java", FileStatus.RENAMED, 0, 0, new PatchContent.Absent());

    assertThat(file("a.java").previousPath()).isNull();
    assertThat(renamed.previousPath()).isEqualTo("old.java");
  }

  @Test
  void instancesWithSameFieldValuesAreEqual() {
    PullRequestSnapshot one = snapshot(null, new ArrayList<>(List.of(file("a.java"))));
    PullRequestSnapshot other = snapshot("", List.of(file("a.java")));

    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
  }
}
