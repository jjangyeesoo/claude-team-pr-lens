package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.ReviewMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class ChunkPlannerTest {

  private static ChangedFile file(String path, int changedLines) {
    int additions = changedLines / 2;
    return new ChangedFile(
        path,
        null,
        FileStatus.MODIFIED,
        additions,
        changedLines - additions,
        new PatchContent.Parsed(List.of()));
  }

  private static List<String> paths(List<ChangedFile> chunk) {
    return chunk.stream().map(ChangedFile::path).toList();
  }

  private static List<List<String>> plannedPaths(List<ChangedFile> files, int limit) {
    return ChunkPlanner.plan(files, limit).stream().map(ChunkPlannerTest::paths).toList();
  }

  // 요구사항 13.1
  @Test
  void singleModeAtLimit() {
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 400)), 400)).isEqualTo(ReviewMode.SINGLE);
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 1)), 400)).isEqualTo(ReviewMode.SINGLE);
  }

  // 설계 G-2: 대상은 있지만 변경 줄 수가 0이면 단일 모드
  @Test
  void singleModeWhenTargetsHaveZeroChangedLines() {
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 0)), 400)).isEqualTo(ReviewMode.SINGLE);
  }

  @Test
  void noTargetWhenNoFiles() {
    assertThat(ChunkPlanner.decideMode(List.of(), 400)).isEqualTo(ReviewMode.NO_TARGET);
  }

  // 요구사항 13.2
  @Test
  void splitModeFromLimitPlusOneToTriple() {
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 401)), 400)).isEqualTo(ReviewMode.SPLIT);
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 1200)), 400)).isEqualTo(ReviewMode.SPLIT);
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 400), file("b", 1)), 400))
        .isEqualTo(ReviewMode.SPLIT);
  }

  // 요구사항 13.9
  @Test
  void summaryOnlyAboveTriple() {
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 1201)), 400))
        .isEqualTo(ReviewMode.SUMMARY_ONLY);
  }

  // 요구사항 13.10
  @Test
  void usesGivenSizeLimit() {
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 100)), 100)).isEqualTo(ReviewMode.SINGLE);
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 101)), 100)).isEqualTo(ReviewMode.SPLIT);
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 300)), 100)).isEqualTo(ReviewMode.SPLIT);
    assertThat(ChunkPlanner.decideMode(List.of(file("a", 301)), 100))
        .isEqualTo(ReviewMode.SUMMARY_ONLY);
  }

  // 요구사항 3.6: 엔진은 제외 파일을 뺀 대상만 넘기므로 플래너는 받은 대상의 줄 수만 센다
  @Test
  void changedLineCountSumsAdditionsAndDeletions() {
    ChangedFile f =
        new ChangedFile("a", null, FileStatus.MODIFIED, 3, 4, new PatchContent.Parsed(List.of()));

    assertThat(ChunkPlanner.changedLineCount(List.of(f, file("b", 10)))).isEqualTo(17);
    assertThat(ChunkPlanner.changedLineCount(List.of())).isZero();
  }

  // 요구사항 13.2
  @Test
  void planGroupsGreedilyInPathOrder() {
    List<ChangedFile> files = List.of(file("c", 100), file("a", 300), file("b", 200));

    assertThat(plannedPaths(files, 400)).containsExactly(List.of("a"), List.of("b", "c"));
  }

  @Test
  void planFillsChunkUpToLimitInclusive() {
    List<ChangedFile> files = List.of(file("a", 300), file("b", 100), file("c", 1));

    assertThat(plannedPaths(files, 400)).containsExactly(List.of("a", "b"), List.of("c"));
  }

  // 요구사항 13.4
  @Test
  void oversizedFileGetsItsOwnChunk() {
    List<ChangedFile> files = List.of(file("a", 10), file("big", 500), file("c", 10));

    assertThat(plannedPaths(files, 400))
        .containsExactly(List.of("a"), List.of("big"), List.of("c"));
  }

  // 요구사항 13.2: 정렬은 UTF-16이 아니라 코드 포인트 기준
  @Test
  void planSortsByCodePointNotUtf16() {
    String bmp = "～.txt"; // U+FF5E
    String supplementary = new String(Character.toChars(0x1F600)) + ".txt"; // 서로게이트 쌍
    List<ChangedFile> files = List.of(file(supplementary, 400), file(bmp, 400));

    assertThat(plannedPaths(files, 400)).containsExactly(List.of(bmp), List.of(supplementary));
  }

  // 요구사항 13.3
  @Test
  void planIsIndependentOfInputOrder() {
    List<ChangedFile> files = new ArrayList<>();
    for (int i = 0; i < 12; i++) {
      files.add(file("dir/f" + i, 50 + i * 37 % 300));
    }
    List<List<String>> expected = plannedPaths(files, 400);

    for (int seed = 0; seed < 5; seed++) {
      List<ChangedFile> shuffled = new ArrayList<>(files);
      Collections.shuffle(shuffled, new Random(seed));
      assertThat(plannedPaths(shuffled, 400)).isEqualTo(expected);
    }
  }
}
