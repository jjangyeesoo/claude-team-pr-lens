package com.prlens.context;

import com.prlens.context.FrontmatterParser.Frontmatter;
import com.prlens.context.FrontmatterParser.Problem;
import com.prlens.glob.GlobMatcher;
import com.prlens.glob.GlobSyntaxException;
import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.RepoPaths;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 프런트매터 {@code paths}로 대상 파일에 맞는 rule을 고른다(요구사항 5.4~5.6, 5.9, 5.10). 순수 함수이고 경고는 결과에 담는다.
 *
 * <p>{@code paths}가 없거나 프런트매터에 문제가 있으면 포함한다. 무효 패턴은 rule마다 먼저 걸러 패턴당 경고 1건을 내고, 모두 무효면 포함한다(설계
 * G-4). 대상 파일의 이전 경로는 {@link FileStatus#RENAMED}일 때만 본다. GitHub {@code copied}가 {@code ADDED}로 매핑된
 * 파일의 {@code previousPath}(복사 원본)는 PR이 바꾸지 않은 파일이므로 맞추지 않는다. 제외 판정에 {@code previousPath}를 보는 {@code
 * DiffFilter}와 다르다.
 */
final class RuleSelector {

  record RuleFile(String path, String content) {}

  /**
   * @param selected 입력 순서를 지킨, 중복 없는 선택 결과
   * @param warnings 호출자가 {@code WarningSink}로 넘길 경고
   */
  record RuleSelection(List<RuleFile> selected, List<Warning> warnings) {
    RuleSelection {
      selected = List.copyOf(selected);
      warnings = List.copyOf(warnings);
    }
  }

  private RuleSelector() {}

  static RuleSelection select(List<RuleFile> rules, List<ChangedFile> targets) {
    List<String> targetPaths = targetPaths(targets);
    List<RuleFile> selected = new ArrayList<>();
    List<Warning> warnings = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (RuleFile rule : rules) {
      if (seen.add(rule.path()) && include(rule, targetPaths, warnings)) {
        selected.add(rule);
      }
    }
    return new RuleSelection(selected, warnings);
  }

  private static boolean include(RuleFile rule, List<String> targetPaths, List<Warning> warnings) {
    Frontmatter frontmatter = FrontmatterParser.parse(rule.content());
    if (frontmatter.problem().isPresent()) {
      Problem problem = frontmatter.problem().get();
      warnings.add(
          new Warning(
              "frontmatter_" + problem.kind().name().toLowerCase(Locale.ROOT),
              "rule 프런트매터를 해석하지 못해 rule을 포함합니다: " + rule.path() + " (" + problem.detail() + ")"));
      return true;
    }
    if (frontmatter.paths().isEmpty()) {
      return true;
    }
    List<String> valid = new ArrayList<>();
    for (String pattern : frontmatter.paths().get()) {
      try {
        GlobMatcher.validate(pattern);
        valid.add(pattern);
      } catch (GlobSyntaxException e) {
        warnings.add(
            new Warning(
                "rule_glob_invalid",
                "rule의 glob 패턴이 유효하지 않아 무시합니다: "
                    + rule.path()
                    + " 패턴 "
                    + pattern
                    + " ("
                    + e.reason()
                    + ")"));
      }
    }
    if (valid.isEmpty()) {
      warnings.add(
          new Warning(
              "rule_globs_all_invalid", "rule의 glob 패턴이 모두 유효하지 않아 rule을 포함합니다: " + rule.path()));
      return true;
    }
    for (String pattern : valid) {
      for (String path : targetPaths) {
        if (GlobMatcher.matches(pattern, path)) {
          return true;
        }
      }
    }
    return false;
  }

  /** 대상 파일의 새 경로와, 이름 변경이면 이전 경로. */
  private static List<String> targetPaths(List<ChangedFile> targets) {
    List<String> paths = new ArrayList<>();
    for (ChangedFile target : targets) {
      paths.add(RepoPaths.normalize(target.path()));
      if (target.status() == FileStatus.RENAMED && target.previousPath() != null) {
        paths.add(RepoPaths.normalize(target.previousPath()));
      }
    }
    return paths;
  }
}
