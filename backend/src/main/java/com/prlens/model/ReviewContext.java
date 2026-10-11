package com.prlens.model;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 리뷰에 쓰는 팀 컨텍스트 (T2 출력). 같은 경로가 두 번 들어오면 생성을 거부한다. */
public record ReviewContext(List<ContextFile> files) {
  public ReviewContext {
    Objects.requireNonNull(files, "files");
    files = List.copyOf(files);
    Set<String> paths = new HashSet<>();
    for (ContextFile file : files) {
      if (!paths.add(file.path())) {
        throw new IllegalArgumentException("files: 경로가 중복됩니다: " + file.path());
      }
    }
  }
}
