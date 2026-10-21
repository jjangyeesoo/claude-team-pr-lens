package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.RuleSelector.RuleFile;
import com.prlens.context.RuleSelector.RuleSelection;
import com.prlens.filter.DiffFilter;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.support.Warning;
import java.util.List;
import org.junit.jupiter.api.Test;

/** {@link RuleSelector} (요구사항 5.4, 5.5, 5.6, 5.9, 5.10, 설계 G-4). */
class RuleSelectorTest {

  private static final PatchContent PARSED = new PatchContent.Parsed(List.of());

  private static ChangedFile modified(String path) {
    return new ChangedFile(path, null, FileStatus.MODIFIED, 0, 0, PARSED);
  }

  private static RuleFile rule(String name, String content) {
    return new RuleFile(".claude/rules/" + name, content);
  }

  private static RuleFile ruleWithPaths(String name, String pathsYaml) {
    return rule(name, "---\npaths: " + pathsYaml + "\n---\n# " + name + "\n");
  }

  private static RuleSelection select(List<RuleFile> rules, List<ChangedFile> targets) {
    RuleSelection selection = RuleSelector.select(rules, targets);
    assertThat(selection).isNotNull();
    return selection;
  }

  @Test
  void ruleWithoutFrontmatterOrPathsIsIncluded() {
    RuleFile plain = rule("plain.md", "# 규칙\n본문\n");
    RuleFile noPaths = rule("no-paths.md", "---\ntitle: t\n---\n본문\n");

    RuleSelection selection = select(List.of(plain, noPaths), List.of(modified("src/A.java")));

    assertThat(selection.selected()).containsExactly(plain, noPaths);
    assertThat(selection.warnings()).isEmpty();
  }

  @Test
  void ruleIncludedOnceWhenAnyPatternMatchesAnyTarget() {
    RuleFile java = ruleWithPaths("java.md", "[\"web/**\", \"src/**\", \"**/*.java\"]");
    RuleFile web = ruleWithPaths("web.md", "\"web/**\"");

    RuleSelection selection =
        select(
            List.of(java, web),
            List.of(modified("src/A.java"), modified("src/B.java"), modified("lib/C.java")));

    assertThat(selection.selected()).containsExactly(java);
    assertThat(selection.warnings()).isEmpty();
  }

  @Test
  void ruleMatchingOnlyExcludedFilesIsNotIncluded() {
    RuleFile images = ruleWithPaths("images.md", "\"**/*.png\"");
    RuleFile source = ruleWithPaths("source.md", "\"src/**\"");
    List<ChangedFile> targets =
        DiffFilter.apply(
                List.of(modified("img/logo.png"), modified("src/A.java")), Configuration.defaults())
            .targets();

    RuleSelection selection = select(List.of(images, source), targets);

    assertThat(selection.selected()).containsExactly(source);
  }

  @Test
  void renamedTargetMatchesByPreviousOrNewPath() {
    RuleFile oldPath = ruleWithPaths("old.md", "\"src/**\"");
    RuleFile newPath = ruleWithPaths("new.md", "\"lib/**\"");
    RuleFile other = ruleWithPaths("other.md", "\"test/**\"");
    ChangedFile renamed =
        new ChangedFile("lib/A.java", "src/A.java", FileStatus.RENAMED, 0, 0, PARSED);

    RuleSelection selection = select(List.of(oldPath, newPath, other), List.of(renamed));

    assertThat(selection.selected()).containsExactly(oldPath, newPath);
  }

  @Test
  void copiedTargetDoesNotMatchByCopySource() {
    RuleFile source = ruleWithPaths("source.md", "\"src/**\"");
    RuleFile docs = ruleWithPaths("docs.md", "\"docs/**\"");
    ChangedFile copied =
        new ChangedFile("docs/A.java", "src/A.java", FileStatus.ADDED, 0, 0, PARSED);

    RuleSelection selection = select(List.of(source, docs), List.of(copied));

    assertThat(selection.selected()).containsExactly(docs);
  }

  @Test
  void frontmatterProblemsIncludeRuleWithPathInWarning() {
    RuleFile yamlError = rule("yaml.md", "---\npaths: [a\n---\n");
    RuleFile unclosed = rule("unclosed.md", "---\npaths: \"web/**\"\n본문\n");
    RuleFile invalidPaths = ruleWithPaths("invalid.md", "1");

    RuleSelection selection =
        select(List.of(yamlError, unclosed, invalidPaths), List.of(modified("src/A.java")));

    assertThat(selection.selected()).containsExactly(yamlError, unclosed, invalidPaths);
    assertThat(selection.warnings())
        .extracting(Warning::code)
        .containsExactly(
            "frontmatter_yaml_error", "frontmatter_unclosed", "frontmatter_invalid_paths");
    assertThat(selection.warnings().get(0).message()).contains(".claude/rules/yaml.md");
    assertThat(selection.warnings().get(1).message()).contains(".claude/rules/unclosed.md");
    assertThat(selection.warnings().get(2).message()).contains(".claude/rules/invalid.md");
  }

  @Test
  void invalidGlobIsIgnoredWithOneWarningRegardlessOfTargetCount() {
    RuleFile mixed = ruleWithPaths("mixed.md", "[\"a**b\", \"src/**\"]");

    RuleSelection selection =
        select(
            List.of(mixed),
            List.of(modified("lib/A.java"), modified("lib/B.java"), modified("docs/C.md")));

    assertThat(selection.selected()).isEmpty();
    assertThat(selection.warnings()).extracting(Warning::code).containsExactly("rule_glob_invalid");
    assertThat(selection.warnings().get(0).message())
        .contains(".claude/rules/mixed.md")
        .contains("a**b");
  }

  @Test
  void validPatternLeftAfterInvalidOneStillSelects() {
    RuleFile mixed = ruleWithPaths("mixed.md", "[\"a**b\", \"src/**\"]");

    RuleSelection selection =
        select(List.of(mixed), List.of(modified("src/A.java"), modified("src/B.java")));

    assertThat(selection.selected()).containsExactly(mixed);
    assertThat(selection.warnings()).extracting(Warning::code).containsExactly("rule_glob_invalid");
  }

  @Test
  void allInvalidGlobsIncludeRuleWithWarning() {
    RuleFile broken = ruleWithPaths("broken.md", "[\"a**b\", \"{x\"]");

    RuleSelection selection = select(List.of(broken), List.of(modified("src/A.java")));

    assertThat(selection.selected()).containsExactly(broken);
    assertThat(selection.warnings())
        .extracting(Warning::code)
        .containsExactly("rule_glob_invalid", "rule_glob_invalid", "rule_globs_all_invalid");
    assertThat(selection.warnings().get(2).message()).contains(".claude/rules/broken.md");
  }
}
