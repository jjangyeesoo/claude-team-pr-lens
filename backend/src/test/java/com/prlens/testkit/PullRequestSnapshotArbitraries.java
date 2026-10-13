package com.prlens.testkit;

import com.prlens.model.ChangedFile;
import com.prlens.model.DiffLine;
import com.prlens.model.FileStatus;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * {@code PullRequestSnapshot} 생성기. 공유 타입의 불변성과 값 동등성을 보는 데 쓴다.
 *
 * <p>Hunk의 줄 번호, 줄 수, 줄 목록은 서로 맞추지 않으므로 diff로는 유효하지 않다. 유효한 Hunk가 필요한 테스트는 T2의 Hunk 생성기를 쓴다.
 *
 * <p>그 밖에 맞추지 않는 것: {@code files}의 경로는 중복되거나 드물게 {@code ""}일 수 있고, {@code additions}와 {@code
 * deletions}는 Hunk의 줄 수와 무관하며, {@code reportedChangedFiles}는 {@code files}의 크기와 무관하다. 필터, 청크 계획,
 * 프롬프트 테스트의 입력으로는 쓰지 않는다.
 */
public final class PullRequestSnapshotArbitraries {

  private PullRequestSnapshotArbitraries() {}

  public static Arbitrary<PullRequestSnapshot> snapshots() {
    Arbitrary<RepoRef> repo =
        Combinators.combine(Arbitraries.of("octo-org", "jjang"), Arbitraries.of("pr-lens", "repo"))
            .as(RepoRef::new);
    return Combinators.combine(
            repo,
            Arbitraries.integers().between(1, Integer.MAX_VALUE),
            ReviewResultArbitraries.texts(),
            ReviewResultArbitraries.texts().injectNull(0.2),
            shas(),
            shas(),
            Arbitraries.integers().between(0, 3_500),
            changedFiles().list().ofMinSize(0).ofMaxSize(4))
        .as(PullRequestSnapshot::new);
  }

  /** {@code previousPath}는 이름 변경일 때만 있다. */
  public static Arbitrary<ChangedFile> changedFiles() {
    return Arbitraries.of(FileStatus.class)
        .flatMap(
            status -> {
              Arbitrary<String> previousPath =
                  status == FileStatus.RENAMED
                      ? RepoPathArbitraries.normalized()
                      : Arbitraries.just(null);
              return Combinators.combine(
                      RepoPathArbitraries.normalized(),
                      previousPath,
                      Arbitraries.integers().between(0, 5_000),
                      Arbitraries.integers().between(0, 5_000),
                      patches())
                  .as(
                      (path, previous, additions, deletions, patch) ->
                          new ChangedFile(path, previous, status, additions, deletions, patch));
            });
  }

  private static Arbitrary<String> shas() {
    return Arbitraries.strings().withChars("0123456789abcdef".toCharArray()).ofLength(40);
  }

  private static Arbitrary<PatchContent> patches() {
    Arbitrary<PatchContent> parsed =
        hunks().list().ofMinSize(0).ofMaxSize(3).map(PatchContent.Parsed::new);
    Arbitrary<PatchContent> unparseable =
        Combinators.combine(
                Arbitraries.of("bad_hunk_header", "line_count_mismatch"),
                Arbitraries.integers().between(1, 500),
                Arbitraries.integers().between(0, 5))
            .as(PatchContent.Unparseable::new);
    return Arbitraries.oneOf(parsed, Arbitraries.just(new PatchContent.Absent()), unparseable);
  }

  private static Arbitrary<Hunk> hunks() {
    Arbitrary<Integer> number = Arbitraries.integers().between(0, 5_000);
    Arbitrary<DiffLine> line =
        Combinators.combine(
                Arbitraries.of(LineKind.class),
                ReviewResultArbitraries.texts(),
                Arbitraries.of(true, false))
            .as(DiffLine::new);
    return Combinators.combine(
            number,
            number,
            number,
            number,
            Arbitraries.of("", "class Foo {", "한글 제목"),
            line.list().ofMinSize(0).ofMaxSize(5))
        .as(Hunk::new);
  }
}
