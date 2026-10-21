package com.prlens.context;

import com.prlens.model.ContextFile;
import com.prlens.model.ReviewContext;
import java.util.List;
import java.util.Set;

/** 수집한 컨텍스트 파일을 경로 기준으로 모은다. 작업 8.11에서 구현한다. */
final class ContextAccumulator {

  void add(ContextFile file) {}

  Set<String> paths() {
    return Set.of();
  }

  List<ContextFile> files() {
    return List.of();
  }

  ReviewContext toContext() {
    return new ReviewContext(List.of());
  }
}
