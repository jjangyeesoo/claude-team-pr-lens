package com.prlens.model;

import java.util.Objects;

/** 근거 검증에서 강등되기 전의 basis. {@code originalRef}는 null일 수 있다. */
public record Demotion(BasisType originalType, String originalRef) {
  public Demotion {
    Objects.requireNonNull(originalType, "originalType");
  }
}
