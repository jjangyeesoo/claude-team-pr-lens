package com.prlens.context;

import com.prlens.model.ContextFile;
import com.prlens.model.RepoPaths;
import com.prlens.model.ReviewContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 수집한 컨텍스트 파일을 정규화한 경로 기준으로 모은다(요구사항 4.8, 8.2, 8.5). 같은 경로는 {@code ContextSource}가 앞선(우선순위가 높은) 쪽만
 * 남기고, 같은 출처면 먼저 들어온 쪽을 둔다. 교체해도 처음 자리를 유지한다.
 */
final class ContextAccumulator {

  private final Map<String, ContextFile> byPath = new LinkedHashMap<>();

  void add(ContextFile file) {
    String path = RepoPaths.normalize(file.path());
    ContextFile existing = byPath.get(path);
    if (existing == null || file.source().ordinal() < existing.source().ordinal()) {
      byPath.put(path, new ContextFile(path, file.content(), file.source(), file.revision()));
    }
  }

  Set<String> paths() {
    return new LinkedHashSet<>(byPath.keySet());
  }

  List<ContextFile> files() {
    return new ArrayList<>(byPath.values());
  }

  ReviewContext toContext() {
    return new ReviewContext(files());
  }
}
