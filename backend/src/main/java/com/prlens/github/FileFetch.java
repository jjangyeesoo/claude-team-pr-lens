package com.prlens.github;

import java.util.Objects;

public sealed interface FileFetch {
  record Found(String content) implements FileFetch {
    public Found {
      Objects.requireNonNull(content, "content");
    }
  }

  record NotFound() implements FileFetch {}

  record IsDirectory() implements FileFetch {}

  /** contents API가 내용을 주지 않는 1MB 초과 파일. */
  record TooLarge() implements FileFetch {}
}
