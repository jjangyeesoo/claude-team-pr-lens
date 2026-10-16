package com.prlens.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffFilterTest {

  private static final PatchContent PARSED = new PatchContent.Parsed(List.of());

  private static ChangedFile file(String path) {
    return new ChangedFile(path, null, FileStatus.MODIFIED, 0, 0, PARSED);
  }

  private static ChangedFile file(String path, PatchContent patch) {
    return new ChangedFile(path, null, FileStatus.MODIFIED, 0, 0, patch);
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

  @Test
  void excludesByFirstMatchingDefaultPatternAndKeepsInputOrder() {
    ChangedFile a = file("src/A.java");
    ChangedFile png = file("img/logo.png");
    ChangedFile b = file("src/B.java");
    ChangedFile lock = file("web/package-lock.json");
    ChangedFile gen = file("x/generated/G.java");
    ChangedFile secret = file("config/.env.local");

    FilterOutcome out =
        DiffFilter.apply(List.of(a, png, b, lock, gen, secret), Configuration.defaults());

    assertThat(out.targets()).containsExactly(a, b);
    assertThat(out.excluded())
        .containsExactly(
            new ExcludedFile("img/logo.png", "**/*.png"),
            new ExcludedFile("web/package-lock.json", "**/package-lock.json"),
            new ExcludedFile("x/generated/G.java", "**/generated/**"),
            new ExcludedFile("config/.env.local", "**/.env*"));
    assertThat(out.warnings()).isEmpty();
  }

  @Test
  void patternMatchingIsCaseSensitive() {
    ChangedFile upper = file("img/LOGO.PNG");
    FilterOutcome out = DiffFilter.apply(List.of(upper), Configuration.defaults());
    assertThat(out.targets()).containsExactly(upper);
  }

  @Test
  void absentPatchWithoutPatternMatchIsBinaryOrTooLarge() {
    ChangedFile big = file("src/Big.java", new PatchContent.Absent());
    ChangedFile png = file("a.png", new PatchContent.Absent());
    FilterOutcome out = DiffFilter.apply(List.of(big, png), Configuration.defaults());
    assertThat(out.excluded())
        .containsExactly(
            new ExcludedFile("src/Big.java", "binary_or_too_large"),
            new ExcludedFile("a.png", "**/*.png"));
  }

  @Test
  void unparseablePatchIsExcludedWithUnparseableReason() {
    ChangedFile bad = file("src/Bad.java", new PatchContent.Unparseable("header_syntax", 3, 0));
    FilterOutcome out = DiffFilter.apply(List.of(bad), Configuration.defaults());
    assertThat(out.targets()).isEmpty();
    assertThat(out.excluded())
        .containsExactly(new ExcludedFile("src/Bad.java", "unparseable_patch"));
  }

  @Test
  void renameIsExcludedWhenEitherPathMatches() {
    ChangedFile fromPng =
        new ChangedFile("docs/a.txt", "img/a.png", FileStatus.RENAMED, 0, 0, PARSED);
    ChangedFile toPng =
        new ChangedFile("img/b.png", "docs/b.txt", FileStatus.RENAMED, 0, 0, PARSED);
    ChangedFile plain =
        new ChangedFile("src/N.java", "src/O.java", FileStatus.RENAMED, 0, 0, PARSED);

    FilterOutcome out = DiffFilter.apply(List.of(fromPng, toPng, plain), Configuration.defaults());

    assertThat(out.targets()).containsExactly(plain);
    assertThat(out.excluded())
        .containsExactly(
            new ExcludedFile("docs/a.txt", "**/*.png"), new ExcludedFile("img/b.png", "**/*.png"));
  }

  @Test
  void additionalPatternsComeAfterDefaults() {
    FilterOutcome out =
        DiffFilter.apply(
            List.of(file("docs/guide.md"), file("a.png")), config(List.of("docs/**"), List.of()));
    assertThat(out.excluded())
        .containsExactly(
            new ExcludedFile("docs/guide.md", "docs/**"), new ExcludedFile("a.png", "**/*.png"));
  }

  @Test
  void removedPatternsDropOnlyExactDefaultMatches() {
    ChangedFile png = file("a.png");
    ChangedFile jpg = file("a.jpg");
    FilterOutcome out =
        DiffFilter.apply(
            List.of(png, jpg), config(List.of(), List.of("**/*.png", "*.jpg", "no/such")));
    assertThat(out.targets()).containsExactly(png);
    assertThat(out.excluded()).containsExactly(new ExcludedFile("a.jpg", "**/*.jpg"));
    assertThat(out.warnings()).isEmpty();
  }

  @Test
  void removingSecretPatternIsIgnoredWithWarning() {
    FilterOutcome out =
        DiffFilter.apply(
            List.of(file(".env"), file("a/secrets/key.txt")),
            config(List.of(), List.of("**/.env*", "**/secrets/**")));
    assertThat(out.targets()).isEmpty();
    assertThat(out.excluded())
        .extracting(ExcludedFile::reason)
        .containsExactly("**/.env*", "**/secrets/**");
    assertThat(out.warnings()).hasSize(2);
    assertThat(out.warnings().get(0).code()).isEqualTo("exclude_unlock_ignored");
    assertThat(out.warnings().get(0).message()).contains("**/.env*");
    assertThat(out.warnings().get(1).message()).contains("**/secrets/**");
  }

  @Test
  void emptyInputGivesEmptyOutcome() {
    FilterOutcome out = DiffFilter.apply(List.of(), Configuration.defaults());
    assertThat(out.targets()).isEmpty();
    assertThat(out.excluded()).isEmpty();
  }
}
