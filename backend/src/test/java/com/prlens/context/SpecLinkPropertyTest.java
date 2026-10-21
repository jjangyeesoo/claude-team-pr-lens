package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.SpecLinkExtractor.Extraction;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import com.prlens.testkit.SpecLinkArbitraries;
import com.prlens.testkit.SpecLinkArbitraries.SpecBody;
import java.util.List;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** 스펙 링크 추출 속성 (요구사항 7.1, 7.3, 7.5, 7.6, 7.7). */
class SpecLinkPropertyTest {

  private static final int LIMIT = 20;

  @Provide
  Arbitrary<SpecBody> bodies() {
    return SpecLinkArbitraries.bodies();
  }

  // Feature: pr-lens-p1-cli, Property 17: 스펙 링크 추출
  @Property(tries = 200)
  void specLinkExtraction(@ForAll("bodies") SpecBody sample) {
    Extraction result = SpecLinkExtractor.extract(sample.body(), new RepoRef("o", "r"));

    List<String> expected =
        sample.expectedPaths().subList(0, Math.min(LIMIT, sample.expectedPaths().size()));
    assertThat(result.paths()).containsExactlyElementsOf(expected);
    assertThat(result.paths()).allSatisfy(path -> assertThat(path).startsWith("docs/specs/"));
    assertThat(result.paths())
        .allSatisfy(path -> assertThat(List.of(path.split("/"))).doesNotContain(".."));

    List<Warning> truncated = byCode(result, "spec_links_truncated");
    int excess = sample.expectedPaths().size() - expected.size();
    if (excess > 0) {
      assertThat(truncated).hasSize(1);
      assertThat(truncated.get(0).message()).contains(String.valueOf(excess));
    } else {
      assertThat(truncated).isEmpty();
    }
    assertThat(byCode(result, "spec_link_other_repo")).hasSize(sample.otherRepo());
    assertThat(byCode(result, "spec_link_outside_specs")).hasSize(sample.outside());
  }

  private static List<Warning> byCode(Extraction result, String code) {
    return result.warnings().stream().filter(w -> w.code().equals(code)).toList();
  }
}
