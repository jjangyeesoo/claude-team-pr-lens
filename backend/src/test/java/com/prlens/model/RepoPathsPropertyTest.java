package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.testkit.RepoPathArbitraries;
import com.prlens.testkit.RepoPathArbitraries.PathPair;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** {@code RepoPaths.normalize}의 속성 테스트 (design.md Property 22). */
class RepoPathsPropertyTest {

  @Provide
  Arbitrary<PathPair> equivalentPairs() {
    return RepoPathArbitraries.equivalentPairs();
  }

  @Provide
  Arbitrary<PathPair> differentPairs() {
    return RepoPathArbitraries.differentPairs();
  }

  // Feature: pr-lens-p1-cli, Property 22: 경로 정규화 비교
  @Property(tries = 200)
  void normalizedComparisonAcceptsPrefixAndSeparatorVariantsOnly(
      @ForAll("equivalentPairs") PathPair equivalent,
      @ForAll("differentPairs") PathPair different) {
    assertThat(RepoPaths.normalize(equivalent.variant())).isEqualTo(equivalent.base());
    assertThat(RepoPaths.normalize(different.variant())).isNotEqualTo(different.base());
  }
}
