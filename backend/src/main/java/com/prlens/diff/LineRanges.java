package com.prlens.diff;

import com.prlens.model.Hunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 파일 하나의 Changed_Line_Range. head 줄 수가 1 이상인 Hunk의 닫힌 구간을 정렬·병합해 둔다. */
public final class LineRanges {

  private final long[] starts;
  private final long[] ends;

  private LineRanges(long[] starts, long[] ends) {
    this.starts = starts;
    this.ends = ends;
  }

  public static LineRanges of(List<Hunk> hunks) {
    List<long[]> ranges = new ArrayList<>();
    for (Hunk hunk : hunks) {
      if (hunk.headCount() >= 1) {
        ranges.add(new long[] {hunk.headStart(), (long) hunk.headStart() + hunk.headCount() - 1});
      }
    }
    ranges.sort(Comparator.comparingLong(r -> r[0]));

    List<long[]> merged = new ArrayList<>();
    for (long[] range : ranges) {
      long[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
      if (last != null && range[0] <= last[1] + 1) {
        last[1] = Math.max(last[1], range[1]);
      } else {
        merged.add(range);
      }
    }
    return new LineRanges(
        merged.stream().mapToLong(r -> r[0]).toArray(),
        merged.stream().mapToLong(r -> r[1]).toArray());
  }

  public boolean contains(int line) {
    int low = 0;
    int high = starts.length - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (line < starts[mid]) {
        high = mid - 1;
      } else if (line > ends[mid]) {
        low = mid + 1;
      } else {
        return true;
      }
    }
    return false;
  }
}
