package com.prlens.filter;

import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import java.util.List;

/** 변경 파일을 리뷰 대상과 제외 파일로 나눈다. 본문은 작업 6.3에서 구현한다. */
public final class DiffFilter {

  private DiffFilter() {}

  public static FilterOutcome apply(List<ChangedFile> files, Configuration config) {
    throw new UnsupportedOperationException("작업 6.3에서 구현한다");
  }
}
