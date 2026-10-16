package com.prlens.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.Hunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class LineRangesTest {

  private static Hunk hunk(int headStart, int headCount) {
    return new Hunk(headStart, 0, headStart, headCount, "", List.of());
  }

  @Test
  void buildsClosedRangeFromHeadStartAndCount() {
    LineRanges ranges = LineRanges.of(List.of(hunk(10, 3)));

    assertThat(ranges.contains(9)).isFalse();
    assertThat(ranges.contains(10)).isTrue();
    assertThat(ranges.contains(12)).isTrue();
    assertThat(ranges.contains(13)).isFalse();
  }

  @Test
  void ignoresHunkWithHeadCountZero() {
    LineRanges ranges = LineRanges.of(List.of(hunk(5, 0)));

    assertThat(ranges.contains(5)).isFalse();
    assertThat(ranges.contains(4)).isFalse();
  }

  @Test
  void mergesAdjacentAndOverlappingRanges() {
    LineRanges ranges = LineRanges.of(List.of(hunk(20, 2), hunk(1, 2), hunk(3, 2), hunk(22, 1)));

    assertThat(ranges.contains(1)).isTrue();
    assertThat(ranges.contains(4)).isTrue();
    assertThat(ranges.contains(5)).isFalse();
    assertThat(ranges.contains(22)).isTrue();
    assertThat(ranges.contains(23)).isFalse();
  }

  @Test
  void containsUsesBinarySearchBoundaries() {
    LineRanges ranges = LineRanges.of(List.of(hunk(1, 1), hunk(10, 1), hunk(100, 5)));

    assertThat(ranges.contains(0)).isFalse();
    assertThat(ranges.contains(1)).isTrue();
    assertThat(ranges.contains(2)).isFalse();
    assertThat(ranges.contains(10)).isTrue();
    assertThat(ranges.contains(104)).isTrue();
    assertThat(ranges.contains(105)).isFalse();
    assertThat(LineRanges.of(List.of()).contains(1)).isFalse();
  }
}
