package com.prlens.context;

import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import java.util.List;

/** PR 본문에서 스펙 경로를 뽑는다. */
final class SpecLinkExtractor {

  record Extraction(List<String> paths, List<Warning> warnings) {}

  private SpecLinkExtractor() {}

  static Extraction extract(String body, RepoRef repo) {
    return new Extraction(List.of(), List.of());
  }
}
