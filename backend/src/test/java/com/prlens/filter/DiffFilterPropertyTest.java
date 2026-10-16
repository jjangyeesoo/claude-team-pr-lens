package com.prlens.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.glob.GlobMatcher;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * {@code DiffFilter}의 속성 테스트 (design.md Property 8, 9, 10). 입력은 Hunk 없는 {@code ChangedFile}이다. 필터는
 * path, previousPath, patch 상태만 읽는다.
 */
class DiffFilterPropertyTest {

  /** 요구사항 3.1의 기본 목록. 구현과 따로 적어 순서와 내용을 고정한다. */
  private static final List<String> DEFAULTS =
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

  private static final List<String> SECRETS = List.of("**/.env*", "**/secrets/**");

  private static final List<String> EXTRA_PATTERNS =
      List.of("src/**", "**/*.md", "docs/*", "*.txt", "a/{b,c}/**", "**/Foo.java", "**/*.PNG");

  private static final List<String> SEGMENTS =
      List.of(
          "src",
          "docs",
          "a",
          "b",
          "c",
          "generated",
          "build",
          "dist",
          "secrets",
          "Foo.java",
          "foo.java",
          "x.png",
          "x.PNG",
          "y.jpg",
          "z.md",
          "n.txt",
          ".env",
          ".env.local",
          "package-lock.json",
          "app.min.js");

  @Provide
  Arbitrary<List<ChangedFile>> files() {
    Arbitrary<String> path =
        Arbitraries.of(SEGMENTS).list().ofMinSize(1).ofMaxSize(4).map(s -> String.join("/", s));
    Arbitrary<ChangedFile> file =
        Combinators.combine(
                path, path, Arbitraries.of(false, false, true), Arbitraries.of(0, 0, 1, 2))
            .as(DiffFilterPropertyTest::changedFile);
    return file.list().ofMinSize(0).ofMaxSize(12);
  }

  private static ChangedFile changedFile(String path, String previous, boolean renamed, int patch) {
    PatchContent content =
        switch (patch) {
          case 1 -> new PatchContent.Absent();
          case 2 -> new PatchContent.Unparseable("header_syntax", 1, 0);
          default -> new PatchContent.Parsed(List.of());
        };
    return renamed
        ? new ChangedFile(path, previous, FileStatus.RENAMED, 0, 0, content)
        : new ChangedFile(path, null, FileStatus.MODIFIED, 0, 0, content);
  }

  @Provide
  Arbitrary<Configuration> configs() {
    Arbitrary<List<String>> additional =
        Arbitraries.of(EXTRA_PATTERNS).list().ofMinSize(0).ofMaxSize(3);
    List<String> removable = new ArrayList<>(DEFAULTS);
    removable.addAll(List.of("no/such", "*.png", "**/*.PNG"));
    Arbitrary<List<String>> removed = Arbitraries.of(removable).list().ofMinSize(0).ofMaxSize(5);
    return Combinators.combine(additional, removed).as(DiffFilterPropertyTest::config);
  }

  private static Configuration config(List<String> additional, List<String> removed) {
    Configuration d = Configuration.defaults();
    return new Configuration(
        additional,
        removed,
        d.sizeLimit(),
        d.model(),
        d.effort(),
        d.maxOutputTokens(),
        d.maxRetries(),
        d.maxCostUsdPerReview(),
        d.pricing());
  }

  /** 모델: 요구사항 3.8~3.10의 합성 규칙을 그대로 적은 것. */
  private static List<String> modelPatterns(Configuration config) {
    List<String> patterns = new ArrayList<>();
    for (String pattern : DEFAULTS) {
      if (SECRETS.contains(pattern) || !config.removedExcludes().contains(pattern)) {
        patterns.add(pattern);
      }
    }
    patterns.addAll(config.additionalExcludes());
    return patterns;
  }

  // Feature: pr-lens-p1-cli, Property 8: 필터 분할 불변과 사유 결정
  @Property(tries = 200)
  void filterPartitionsFilesAndDecidesReasons(
      @ForAll("files") List<ChangedFile> files, @ForAll("configs") Configuration config) {
    List<String> patterns = modelPatterns(config);
    List<ChangedFile> expectedTargets = new ArrayList<>();
    List<ExcludedFile> expectedExcluded = new ArrayList<>();
    for (ChangedFile file : files) {
      String reason = null;
      for (String pattern : patterns) {
        if (GlobMatcher.matches(pattern, file.path())
            || (file.previousPath() != null && GlobMatcher.matches(pattern, file.previousPath()))) {
          reason = pattern;
          break;
        }
      }
      if (reason == null && file.patch() instanceof PatchContent.Absent) {
        reason = "binary_or_too_large";
      }
      if (reason == null && file.patch() instanceof PatchContent.Unparseable) {
        reason = "unparseable_patch";
      }
      if (reason == null) {
        expectedTargets.add(file);
      } else {
        expectedExcluded.add(new ExcludedFile(file.path(), reason));
      }
    }

    FilterOutcome outcome = DiffFilter.apply(files, config);

    assertThat(outcome.targets()).containsExactlyElementsOf(expectedTargets);
    assertThat(outcome.excluded()).containsExactlyElementsOf(expectedExcluded);
    assertThat(outcome.targets().size() + outcome.excluded().size()).isEqualTo(files.size());
  }

  // Feature: pr-lens-p1-cli, Property 9: 필터 멱등
  @Property(tries = 200)
  void filteringTargetsAgainChangesNothing(
      @ForAll("files") List<ChangedFile> files, @ForAll("configs") Configuration config) {
    FilterOutcome first = DiffFilter.apply(files, config);
    FilterOutcome second = DiffFilter.apply(first.targets(), config);

    assertThat(second.targets()).containsExactlyElementsOf(first.targets());
    assertThat(second.excluded()).isEmpty();
  }

  // Feature: pr-lens-p1-cli, Property 10: 제외 패턴 목록 합성
  @Property(tries = 200)
  void composedPatternsAreDefaultsMinusRemovedPlusAdditional(
      @ForAll("configs") Configuration config) {
    List<Warning> warnings = new ArrayList<>();

    List<String> composed = DiffFilter.composePatterns(config, warnings);

    assertThat(composed).containsExactlyElementsOf(modelPatterns(config));
    assertThat(composed).containsAll(SECRETS);
    long ignoredSecrets = SECRETS.stream().filter(config.removedExcludes()::contains).count();
    assertThat(warnings).hasSize((int) ignoredSecrets);
    assertThat(warnings).allSatisfy(w -> assertThat(w.code()).isEqualTo("exclude_unlock_ignored"));
  }
}
