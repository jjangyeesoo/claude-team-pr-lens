package com.prlens.context;

import com.prlens.context.RuleSelector.RuleFile;
import com.prlens.context.RuleSelector.RuleSelection;
import com.prlens.model.ChangedFile;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * base SHA의 {@code .claude/rules} 아래 모든 깊이의 {@code .md}(대소문자 구분) rule을 경로순으로 읽어 {@link
 * RuleSelector}로 고른다(요구사항 5.1, 5.11). 폴더가 없으면 경고 없이 빈 목록이고, 트리가 잘려 폴더를 열거할 수 없으면 경고 후 빈 목록이다(설계
 * G-9). 내용은 받은 그대로 담는다.
 */
final class RuleCollector {

  private static final String RULES_FOLDER = ".claude/rules";

  private final RepoTreeIndex index;
  private final BaseFileReader reader;
  private final WarningSink warnings;

  RuleCollector(RepoTreeIndex index, BaseFileReader reader, WarningSink warnings) {
    this.index = index;
    this.reader = reader;
    this.warnings = warnings;
  }

  List<ContextFile> collect(List<ChangedFile> targets) {
    Optional<List<String>> files = index.filesUnder(RULES_FOLDER);
    if (files.isEmpty()) {
      warnings.warn(
          new Warning(
              "rules_skipped_tree_truncated", "저장소 트리가 잘려 " + RULES_FOLDER + "의 rule을 수집하지 않습니다"));
      return List.of();
    }
    List<RuleFile> rules = new ArrayList<>();
    for (String path : files.get()) {
      if (path.endsWith(".md")) {
        reader.read(path).ifPresent(content -> rules.add(new RuleFile(path, content)));
      }
    }
    RuleSelection selection = RuleSelector.select(rules, targets);
    selection.warnings().forEach(warnings::warn);
    return selection.selected().stream()
        .map(
            rule -> new ContextFile(rule.path(), rule.content(), ContextSource.RULE, Revision.BASE))
        .toList();
  }
}
