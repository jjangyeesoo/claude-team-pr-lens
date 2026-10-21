package com.prlens.context;

import java.util.List;
import java.util.Optional;

/** rule 파일의 YAML 프런트매터에서 {@code paths}를 읽는다(요구사항 5.2, 5.3, 5.9, 5.10, 22.4). */
final class FrontmatterParser {

  enum Kind {
    YAML_ERROR,
    UNCLOSED,
    INVALID_PATHS
  }

  record Problem(Kind kind, String detail) {}

  record Frontmatter(Optional<List<String>> paths, Optional<Problem> problem) {}

  private FrontmatterParser() {}

  static Frontmatter parse(String content) {
    return null;
  }
}
