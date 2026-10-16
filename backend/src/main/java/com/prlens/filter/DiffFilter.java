package com.prlens.filter;

import com.prlens.glob.GlobMatcher;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ExcludedFile;
import com.prlens.model.PatchContent;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 변경 파일을 리뷰 대상과 제외 파일로 나눈다. */
public final class DiffFilter {

  /** 비밀정보 패턴. 설정으로 해제할 수 없다. */
  private static final List<String> SECRET_PATTERNS = List.of("**/.env*", "**/secrets/**");

  /** 기본 제외 패턴 (요구사항 3.1의 순서. 생성 코드 4개는 D-6 제안값). */
  private static final List<String> DEFAULT_PATTERNS =
      List.of(
          "**/*.png",
          "**/*.jpg",
          "**/*.gif",
          "**/*.ico",
          "**/*.pdf",
          "**/*.zip",
          "**/*.jar",
          "**/*.class",
          "**/package-lock.json",
          "**/yarn.lock",
          "**/pnpm-lock.yaml",
          "**/gradle.lockfile",
          "**/generated/**",
          "**/build/**",
          "**/*.min.js",
          "**/dist/**",
          "**/.env*",
          "**/secrets/**");

  private static final String UNLOCK_IGNORED = "exclude_unlock_ignored";
  private static final String BINARY_OR_TOO_LARGE = "binary_or_too_large";
  private static final String UNPARSEABLE_PATCH = "unparseable_patch";

  private DiffFilter() {}

  public static FilterOutcome apply(List<ChangedFile> files, Configuration config) {
    List<Warning> warnings = new ArrayList<>();
    List<String> patterns = composePatterns(config, warnings);

    List<ChangedFile> targets = new ArrayList<>();
    List<ExcludedFile> excluded = new ArrayList<>();
    for (ChangedFile file : files) {
      String reason = excludeReason(file, patterns);
      if (reason == null) {
        targets.add(file);
      } else {
        excluded.add(new ExcludedFile(file.path(), reason));
      }
    }
    return new FilterOutcome(targets, excluded, warnings);
  }

  /** 기본 − (해제 ∩ 비밀정보 아닌 기본) + 추가. 비밀정보 해제 요청은 경고만 남긴다. */
  static List<String> composePatterns(Configuration config, List<Warning> warnings) {
    Set<String> removed = new LinkedHashSet<>(config.removedExcludes());
    for (String pattern : removed) {
      if (SECRET_PATTERNS.contains(pattern)) {
        warnings.add(new Warning(UNLOCK_IGNORED, "비밀정보 제외 패턴은 해제할 수 없어 요청을 무시했습니다: " + pattern));
      }
    }
    List<String> patterns = new ArrayList<>();
    for (String pattern : DEFAULT_PATTERNS) {
      if (SECRET_PATTERNS.contains(pattern) || !removed.contains(pattern)) {
        patterns.add(pattern);
      }
    }
    patterns.addAll(config.additionalExcludes());
    return patterns;
  }

  private static String excludeReason(ChangedFile file, List<String> patterns) {
    for (String pattern : patterns) {
      if (GlobMatcher.matches(pattern, file.path())
          || (file.previousPath() != null && GlobMatcher.matches(pattern, file.previousPath()))) {
        return pattern;
      }
    }
    if (file.patch() instanceof PatchContent.Absent) {
      return BINARY_OR_TOO_LARGE;
    }
    if (file.patch() instanceof PatchContent.Unparseable) {
      return UNPARSEABLE_PATCH;
    }
    return null;
  }
}
