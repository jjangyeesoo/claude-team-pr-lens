package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

// 작업 11.2: Property 28(청크 계획)
// 입력은 PullRequestSnapshotArbitraries.snapshots()가 아니라 플래너가 읽는 값(path, additions, deletions)만
// 맞춘 별도 생성기를 쓴다 (tasks.md 11.2 주의: snapshots()는 경로가 겹치거나 ""일 수 있다).
class ChunkPlannerPropertyTest {

  @Provide
  Arbitrary<List<ChangedFile>> targets() {
    Arbitrary<ChangedFile> file =
        Combinators.combine(
                Arbitraries.strings().alpha().numeric().ofMinLength(1).ofMaxLength(8),
                Arbitraries.integers().between(0, 300),
                Arbitraries.integers().between(0, 300))
            .as(
                (name, additions, deletions) ->
                    new ChangedFile(
                        "src/" + name,
                        null,
                        FileStatus.MODIFIED,
                        additions,
                        deletions,
                        new PatchContent.Parsed(List.of())));
    return file.list()
        .ofMinSize(1)
        .ofMaxSize(25)
        .map(
            list -> {
              Set<String> seen = new LinkedHashSet<>();
              List<ChangedFile> unique = new ArrayList<>();
              for (ChangedFile f : list) {
                if (seen.add(f.path())) {
                  unique.add(f);
                }
              }
              return unique;
            });
  }

  @Provide
  Arbitrary<Integer> sizeLimit() {
    return Arbitraries.integers().between(1, 500);
  }

  private static int lines(ChangedFile f) {
    return f.additions() + f.deletions();
  }

  // Feature: pr-lens-p1-cli, Property 28: 청크 계획
  @Property
  void everyFileInExactlyOneChunkAndMultiFileChunksFitLimit(
      @ForAll("targets") List<ChangedFile> files, @ForAll("sizeLimit") int limit) {
    List<List<ChangedFile>> chunks = ChunkPlanner.plan(files, limit);

    List<String> planned = chunks.stream().flatMap(List::stream).map(ChangedFile::path).toList();
    assertThat(planned).hasSize(files.size());
    assertThat(planned)
        .containsExactlyInAnyOrderElementsOf(files.stream().map(ChangedFile::path).toList());
    for (List<ChangedFile> chunk : chunks) {
      assertThat(chunk).isNotEmpty();
      if (chunk.size() >= 2) {
        assertThat(chunk.stream().mapToInt(ChunkPlannerPropertyTest::lines).sum())
            .isLessThanOrEqualTo(limit);
      }
    }
  }

  // Feature: pr-lens-p1-cli, Property 28: 청크 계획
  @Property
  void planIsDeterministicForShuffledInput(
      @ForAll("targets") List<ChangedFile> files,
      @ForAll("sizeLimit") int limit,
      @ForAll long seed) {
    List<ChangedFile> shuffled = new ArrayList<>(files);
    Collections.shuffle(shuffled, new Random(seed));

    assertThat(ChunkPlanner.plan(shuffled, limit)).isEqualTo(ChunkPlanner.plan(files, limit));
  }
}
