package com.prlens.model;

import java.util.Objects;

/**
 * @param ref 근거 파일 경로. {@code GENERAL}이면 null
 */
public record Basis(BasisType type, String ref) {
  public Basis {
    Objects.requireNonNull(type, "type");
  }
}
