package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.SpecLinkExtractor.Extraction;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** {@link SpecLinkExtractor} (요구사항 7.1~7.8). */
class SpecLinkExtractorTest {

  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

  private static Extraction extract(String body) {
    return SpecLinkExtractor.extract(body, REPO);
  }

  private static List<String> codes(Extraction extraction) {
    return extraction.warnings().stream().map(Warning::code).toList();
  }

  @Test
  void extractFindsRelativePathsWithOptionalDotSlashOrSlash() {
    Extraction result = extract("docs/specs/a.md\n./docs/specs/b.md\n/docs/specs/c.md");

    assertThat(result.paths())
        .containsExactly("docs/specs/a.md", "docs/specs/b.md", "docs/specs/c.md");
  }

  @Test
  void extractFindsSameRepoUrlRegardlessOfRef() {
    String body =
        "https://github.com/o/r/blob/main/docs/specs/a.md "
            + "https://github.com/o/r/blob/"
            + SHA
            + "/docs/specs/b.md";

    assertThat(extract(body).paths()).containsExactly("docs/specs/a.md", "docs/specs/b.md");
  }

  @Test
  void extractFindsSameRepoUrlWithSlashInRef() {
    String body =
        String.join(
            "\n",
            "https://github.com/o/r/blob/feat/x/docs/specs/a.md",
            "https://github.com/o/r/blob/release/1.0/docs/specs/b.md#L2",
            "https://github.com/other/r/blob/feat/x/docs/specs/c.md",
            "https://github.com/o/r/blob/feat/x/docs/specs/../y.md",
            "https://github.com/o/r/blob/feat/../x/docs/specs/d.md");

    Extraction result = extract(body);

    assertThat(result.paths())
        .containsExactly("docs/specs/a.md", "docs/specs/b.md", "docs/specs/d.md");
    assertThat(codes(result))
        .containsExactlyInAnyOrder("spec_link_other_repo", "spec_link_outside_specs");
  }

  @Test
  void extractDoesNotLetRefSpanAcrossAdjacentLinks() {
    String body =
        "[x](https://github.com/other/r/blob/main/x.md)"
            + "[y](https://github.com/o/r/blob/feat/x/docs/specs/a.md)";

    Extraction result = extract(body);

    assertThat(result.paths()).containsExactly("docs/specs/a.md");
    assertThat(codes(result)).doesNotContain("spec_link_other_repo");
  }

  @Test
  void extractStopsAtBacktick() {
    String body =
        "`docs/specs/a.md` `./docs/specs/b.md#x` "
            + "`https://github.com/o/r/blob/main/docs/specs/c.md`";

    assertThat(extract(body).paths())
        .containsExactly("docs/specs/a.md", "docs/specs/b.md", "docs/specs/c.md");
  }

  @Test
  void extractMergesRelativeAndUrlMatchesInBodyOrder() {
    String body =
        "https://github.com/o/r/blob/main/docs/specs/u1.md 그리고 docs/specs/rel.md 그리고 "
            + "https://github.com/o/r/blob/main/docs/specs/u2.md";

    assertThat(extract(body).paths())
        .containsExactly("docs/specs/u1.md", "docs/specs/rel.md", "docs/specs/u2.md");
  }

  @Test
  void extractStripsAnchorAndQuery() {
    String body =
        "docs/specs/a.md#goal https://github.com/o/r/blob/main/docs/specs/b.md?plain=1#L3";

    assertThat(extract(body).paths()).containsExactly("docs/specs/a.md", "docs/specs/b.md");
  }

  @Test
  void extractSkipsOtherRepoUrlWithWarningButMatchesCaseInsensitively() {
    String body =
        "https://github.com/other/r/blob/main/docs/specs/x.md\n"
            + "https://github.com/O/R/blob/main/docs/specs/y.md";

    Extraction result = extract(body);

    assertThat(result.paths()).containsExactly("docs/specs/y.md");
    assertThat(result.warnings()).hasSize(1);
    assertThat(result.warnings().get(0).code()).isEqualTo("spec_link_other_repo");
    assertThat(result.warnings().get(0).message()).contains("docs/specs/x.md");
  }

  @Test
  void extractSkipsDotDotAndOutsideSpecsWithWarning() {
    String body =
        "docs/specs/../secret.md\n"
            + "https://github.com/o/r/blob/main/docs/specs/a/../../x.md\n"
            + "docs/specs/.\n"
            + "../docs/specs/a.md";

    Extraction result = extract(body);

    assertThat(result.paths()).isEmpty();
    assertThat(codes(result))
        .containsExactly(
            "spec_link_outside_specs",
            "spec_link_outside_specs",
            "spec_link_outside_specs",
            "spec_link_outside_specs");
    assertThat(result.warnings().get(0).message()).contains("docs/specs/../secret.md");
  }

  @Test
  void extractDeduplicatesAcrossFormsAndEmptyOrDotSegments() {
    String body =
        String.join(
            "\n",
            "docs/specs/a.md",
            "./docs/specs/a.md",
            "/docs/specs/a.md#x",
            "https://github.com/o/r/blob/main/docs/specs/a.md",
            "docs/specs//a.md",
            "docs/specs/./a.md");

    Extraction result = extract(body);

    assertThat(result.paths()).containsExactly("docs/specs/a.md");
    assertThat(result.warnings()).isEmpty();
  }

  @Test
  void extractKeepsFirstTwentyDistinctPathsAndWarnsExcessCount() {
    StringBuilder body = new StringBuilder("docs/specs/f0.md docs/specs/f1.md\n");
    IntStream.range(0, 23).forEach(i -> body.append("docs/specs/f").append(i).append(".md\n"));

    Extraction result = extract(body.toString());

    assertThat(result.paths())
        .containsExactlyElementsOf(
            IntStream.range(0, 20).mapToObj(i -> "docs/specs/f" + i + ".md").toList());
    assertThat(result.warnings()).hasSize(1);
    assertThat(result.warnings().get(0).code()).isEqualTo("spec_links_truncated");
    assertThat(result.warnings().get(0).message()).contains("3");
  }

  @Test
  void extractIgnoresNonLinksWithoutWarnings() {
    Extraction empty = extract("");
    assertThat(empty.paths()).isEmpty();
    assertThat(empty.warnings()).isEmpty();

    Extraction result =
        extract(
            "mydocs/specs/x.md https://example.com/docs/specs/y.md docs/specsx/z.md"
                + " docs/specs/z.md");

    assertThat(result.paths()).containsExactly("docs/specs/z.md");
    assertThat(result.warnings()).isEmpty();
  }
}
