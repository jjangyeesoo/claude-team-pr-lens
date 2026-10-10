package com.prlens.model;

import java.util.Objects;

/**
 * @param reason 처음 일치한 제외 패턴 문자열, 또는 {@code binary_or_too_large}, {@code unparseable_patch}
 */
public record ExcludedFile(String path, String reason) {
  public ExcludedFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(reason, "reason");
  }
}
