package com.prlens.diff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.diff.DiffParseException.Kind;
import com.prlens.model.DiffLine;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import com.prlens.testkit.DiffArbitraries;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** diff 해석·출력·변경 줄 범위의 속성 테스트 (design.md Property 3~7). */
class DiffPropertyTest {

  private static final String PATH = "f";

  @Provide
  Arbitrary<List<Hunk>> hunks() {
    return DiffArbitraries.validHunks();
  }

  @Provide
  Arbitrary<List<Hunk>> nonEmptyHunks() {
    return DiffArbitraries.nonEmptyValidHunks();
  }

  // Feature: pr-lens-p1-cli, Property 3: diff round-trip
  @Property(tries = 200)
  void printThenParseRestoresHunks(@ForAll("hunks") List<Hunk> hunks) {
    assertThat(DiffParser.parse(PATH, DiffPrinter.print(hunks))).isEqualTo(hunks);
  }

  // Feature: pr-lens-p1-cli, Property 4: 추가·삭제 줄 수 보존
  @Property(tries = 200)
  void addedAndRemovedCountsMatchPrefixCounts(@ForAll("hunks") List<Hunk> hunks) {
    String patch = DiffPrinter.print(hunks);
    long plus = 0;
    long minus = 0;
    for (String line : patch.split("\n")) {
      if (line.startsWith("@@") || line.isEmpty()) {
        continue;
      }
      if (line.charAt(0) == '+') {
        plus++;
      } else if (line.charAt(0) == '-') {
        minus++;
      }
    }

    List<DiffLine> parsed =
        DiffParser.parse(PATH, patch).stream().flatMap(h -> h.lines().stream()).toList();

    assertThat(parsed.stream().filter(l -> l.kind() == LineKind.ADDED).count()).isEqualTo(plus);
    assertThat(parsed.stream().filter(l -> l.kind() == LineKind.REMOVED).count()).isEqualTo(minus);
  }

  // Feature: pr-lens-p1-cli, Property 5: Changed_Line_Range 모델 일치
  @Property(tries = 200)
  void containsMatchesSetModel(@ForAll("hunks") List<Hunk> hunks) {
    LineRanges ranges = LineRanges.of(hunks);
    int max = hunks.stream().mapToInt(h -> h.headStart() + h.headCount()).max().orElse(0) + 2;

    for (int k = 0; k <= max; k++) {
      final int line = k;
      boolean expected =
          hunks.stream()
              .anyMatch(
                  h ->
                      h.headCount() >= 1
                          && line >= h.headStart()
                          && line <= h.headStart() + h.headCount() - 1);
      assertThat(ranges.contains(line)).as("line %d", line).isEqualTo(expected);
    }
  }

  // Feature: pr-lens-p1-cli, Property 6: 잘못된 patch 거부
  @Property(tries = 200)
  void invalidPatchesAreRejectedWithoutPartialResult(
      @ForAll("nonEmptyHunks") List<Hunk> hunks, @ForAll @IntRange(min = 0, max = 4) int mutation) {
    String patch = DiffPrinter.print(hunks);
    // 손상별 기대 오류. 줄 번호는 위치가 정해진 손상만 확인한다(-1이면 확인하지 않음)
    Kind expectedKind =
        switch (mutation) {
          case 0 -> Kind.LINE_COUNT_MISMATCH;
          case 1 -> Kind.CONTENT_BEFORE_HEADER;
          case 2 -> Kind.INVALID_PREFIX;
          case 3 -> Kind.HUNK_OVERLAP;
          default -> Kind.HEADER_SYNTAX;
        };
    int expectedHunkIndex = mutation == 3 ? 1 : 0;
    int expectedLineNo =
        switch (mutation) {
          case 1, 4 -> 1;
          case 2 -> 2;
          default -> -1;
        };
    String invalid =
        switch (mutation) {
          case 0 ->
              patch.replaceFirst(
                  "^@@ -(\\d+),(\\d+) ", "@@ -$1," + (hunks.get(0).baseCount() + 1) + " ");
          case 1 -> "garbage\n" + patch;
          case 2 -> replaceFirstBodyPrefix(patch, 'x');
          case 4 -> patch.replaceFirst("^(@@ -\\d+,\\d+) \\+", "$1 ");
          default -> {
            Hunk first = hunks.get(0);
            Hunk overlapping =
                new Hunk(
                    first.baseStart() + first.baseCount() + 1,
                    first.baseCount(),
                    first.headStart() + first.headCount() - 1,
                    first.headCount(),
                    first.sectionHeading(),
                    first.lines());
            yield DiffPrinter.print(List.of(first, overlapping));
          }
        };

    assertThatThrownBy(() -> DiffParser.parse(PATH, invalid))
        .isInstanceOfSatisfying(
            DiffParseException.class,
            e -> {
              assertThat(e.path()).isEqualTo(PATH);
              assertThat(e.kind()).isEqualTo(expectedKind);
              assertThat(e.hunkIndex()).isEqualTo(expectedHunkIndex);
              if (expectedLineNo > 0) {
                assertThat(e.lineNo()).isEqualTo(expectedLineNo);
              }
            });
  }

  // Feature: pr-lens-p1-cli, Property 7: 줄바꿈 무관 해석 (diff 부분)
  @Property(tries = 200)
  void lineEndingStyleDoesNotChangeResult(
      @ForAll("hunks") List<Hunk> hunks, @ForAll Random random) {
    String lf = DiffPrinter.print(hunks);
    List<Hunk> expected = DiffParser.parse(PATH, lf);

    StringBuilder mixed = new StringBuilder();
    for (char c : lf.toCharArray()) {
      if (c == '\n') {
        mixed.append(new String[] {"\n", "\r\n", "\r"}[random.nextInt(3)]);
      } else {
        mixed.append(c);
      }
    }

    assertThat(DiffParser.parse(PATH, lf.replace("\n", "\r\n"))).isEqualTo(expected);
    assertThat(DiffParser.parse(PATH, lf.replace("\n", "\r"))).isEqualTo(expected);
    assertThat(DiffParser.parse(PATH, mixed.toString())).isEqualTo(expected);
  }

  /** 첫 본문 줄(헤더 다음 줄)의 접두어를 바꾼다. */
  private static String replaceFirstBodyPrefix(String patch, char prefix) {
    List<String> lines = new ArrayList<>(List.of(patch.split("\n", -1)));
    String body = lines.get(1);
    lines.set(1, prefix + body.substring(1));
    return String.join("\n", lines);
  }
}
