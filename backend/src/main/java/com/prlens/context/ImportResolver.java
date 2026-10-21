package com.prlens.context;

import com.prlens.model.ContextFile;
import com.prlens.support.WarningSink;
import java.util.List;
import java.util.Optional;

/** 스텁: 구현은 다음 커밋에서 한다. */
final class ImportResolver {

  ImportResolver(BaseFileReader reader, WarningSink warnings) {}

  List<ContextFile> resolve(List<ContextFile> collected) {
    return List.of();
  }

  static List<String> scan(String content) {
    return List.of();
  }

  static Optional<String> resolvePath(String declaredBy, String raw) {
    return Optional.empty();
  }
}
