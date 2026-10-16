package com.prlens.testkit;

import com.prlens.model.DiffLine;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/**
 * 유효한 Hunk 목록 생성기. 줄 수는 줄 목록에서 계산하고 구간은 이전 Hunk의 head 구간 끝보다 뒤에서 시작한다. 줄 내용에는 줄바꿈이 없고, 접두어처럼 보이는
 * 문자({@code +}, {@code -}, {@code @}, {@code \})와 앞뒤 공백이 들어갈 수 있다.
 */
public final class DiffArbitraries {

  private DiffArbitraries() {}

  private record LineSpec(LineKind kind, String content, boolean noNewline) {}

  private record HunkSpec(int baseGap, int headGap, String heading, List<LineSpec> lines) {}

  /** Hunk 0~5개(head 줄 수 0인 삭제 전용 Hunk 포함). */
  public static Arbitrary<List<Hunk>> validHunks() {
    return hunkSpecs().list().ofMinSize(0).ofMaxSize(5).map(DiffArbitraries::build);
  }

  /** Hunk 1~5개. */
  public static Arbitrary<List<Hunk>> nonEmptyValidHunks() {
    return hunkSpecs().list().ofMinSize(1).ofMaxSize(5).map(DiffArbitraries::build);
  }

  private static Arbitrary<HunkSpec> hunkSpecs() {
    Arbitrary<LineSpec> line =
        Combinators.combine(
                Arbitraries.of(LineKind.values()),
                Arbitraries.strings().withCharRange(' ', '~').ofMaxLength(20),
                Arbitraries.of(false, false, false, true))
            .as(LineSpec::new);
    Arbitrary<String> heading =
        Arbitraries.frequencyOf(
            Tuple.of(1, Arbitraries.just("")),
            Tuple.of(
                1,
                Arbitraries.strings()
                    .withCharRange(' ', '~')
                    .ofMaxLength(20)
                    .map(text -> " " + text)));
    return Combinators.combine(
            Arbitraries.integers().between(1, 50),
            Arbitraries.integers().between(1, 50),
            heading,
            line.list().ofMinSize(1).ofMaxSize(8))
        .as(HunkSpec::new);
  }

  private static List<Hunk> build(List<HunkSpec> specs) {
    List<Hunk> hunks = new ArrayList<>();
    int baseEnd = 0;
    int headEnd = 0;
    int prevHeadStart = 0;
    for (HunkSpec spec : specs) {
      int baseCount = 0;
      int headCount = 0;
      List<DiffLine> lines = new ArrayList<>();
      for (LineSpec line : spec.lines()) {
        if (line.kind() != LineKind.ADDED) {
          baseCount++;
        }
        if (line.kind() != LineKind.REMOVED) {
          headCount++;
        }
        lines.add(new DiffLine(line.kind(), line.content(), line.noNewline()));
      }
      int baseStart = baseEnd + spec.baseGap();
      // head 줄 수 0인 Hunk는 구간 끝이 시작보다 작으므로 이전 시작 줄보다도 뒤에서 시작해야 한다
      int headStart = Math.max(headEnd, prevHeadStart) + spec.headGap();
      hunks.add(new Hunk(baseStart, baseCount, headStart, headCount, spec.heading(), lines));
      baseEnd = baseStart + baseCount - 1;
      headEnd = headStart + headCount - 1;
      prevHeadStart = headStart;
    }
    return hunks;
  }
}
