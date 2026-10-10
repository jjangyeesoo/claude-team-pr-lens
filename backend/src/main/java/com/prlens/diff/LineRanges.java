package com.prlens.diff;

import com.prlens.model.Hunk;
import java.util.List;

/** 파일 하나의 Changed_Line_Range. 본문은 작업 5.3에서 구현한다. */
public final class LineRanges {

  private LineRanges() {}

  public static LineRanges of(List<Hunk> hunks) {
    throw new UnsupportedOperationException("작업 5.3에서 구현한다");
  }

  public boolean contains(int line) {
    throw new UnsupportedOperationException("작업 5.3에서 구현한다");
  }
}
