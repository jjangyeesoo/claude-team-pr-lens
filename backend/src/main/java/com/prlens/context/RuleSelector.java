package com.prlens.context;

import com.prlens.model.ChangedFile;
import com.prlens.support.Warning;
import java.util.List;

/** 프런트매터 {@code paths}로 대상 파일에 맞는 rule을 고른다(요구사항 5.4~5.6, 5.9, 5.10). */
final class RuleSelector {

  record RuleFile(String path, String content) {}

  record RuleSelection(List<RuleFile> selected, List<Warning> warnings) {}

  private RuleSelector() {}

  static RuleSelection select(List<RuleFile> rules, List<ChangedFile> targets) {
    return null;
  }
}
