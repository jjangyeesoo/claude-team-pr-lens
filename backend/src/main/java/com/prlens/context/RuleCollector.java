package com.prlens.context;

import com.prlens.model.ChangedFile;
import com.prlens.model.ContextFile;
import com.prlens.support.WarningSink;
import java.util.List;

/** base SHA의 {@code .claude/rules} 아래 rule을 모아 고른다(요구사항 5.1, 5.11). */
final class RuleCollector {

  private final RepoTreeIndex index;
  private final BaseFileReader reader;
  private final WarningSink warnings;

  RuleCollector(RepoTreeIndex index, BaseFileReader reader, WarningSink warnings) {
    this.index = index;
    this.reader = reader;
    this.warnings = warnings;
  }

  List<ContextFile> collect(List<ChangedFile> targets) {
    return null;
  }
}
