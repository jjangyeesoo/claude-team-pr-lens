package com.prlens.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.RepoPaths;
import com.prlens.testkit.RepoPathArbitraries.PathPair;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/** 저장소 경로 생성기가 작업 2.5(Property 22)에서 그대로 쓸 수 있는 짝을 내는지 확인한다. */
class RepoPathArbitrariesTest {

  @Provide
  Arbitrary<String> normalized() {
    return RepoPathArbitraries.normalized();
  }

  @Provide
  Arbitrary<PathPair> equivalentPairs() {
    return RepoPathArbitraries.equivalentPairs();
  }

  @Provide
  Arbitrary<PathPair> differentPairs() {
    return RepoPathArbitraries.differentPairs();
  }

  @Property(tries = 200)
  void basePathsAreAlreadyNormalized(@ForAll("normalized") String path) {
    assertThat(RepoPaths.normalize(path)).isEqualTo(path);

    covers("하위 폴더 경로", path.contains("/"));
    covers("대문자 포함", path.chars().anyMatch(Character::isUpperCase));
  }

  @Property(tries = 200)
  void equivalentVariantsNormalizeToBase(@ForAll("equivalentPairs") PathPair pair) {
    assertThat(RepoPaths.normalize(pair.base())).isEqualTo(pair.base());
    assertThat(RepoPaths.normalize(pair.variant())).isEqualTo(pair.base());

    String variant = pair.variant();
    covers("./ 접두어", variant.startsWith("./") || variant.startsWith(".\\"));
    covers("/ 접두어", variant.startsWith("/") || variant.startsWith("\\"));
    covers("\\ 구분자", variant.contains("\\"));
  }

  @Property(tries = 200)
  void differentVariantsDoNotNormalizeToBase(@ForAll("differentPairs") PathPair pair) {
    assertThat(RepoPaths.normalize(pair.base())).isEqualTo(pair.base());
    assertThat(RepoPaths.normalize(pair.variant())).isNotEqualTo(pair.base());

    covers("../ 접두어", pair.variant().startsWith("../"));
    covers("대소문자 변형", pair.variant().equalsIgnoreCase(pair.base()));
  }

  /** 그 경우가 한 번 이상 생성됐는지 확인한다. */
  private static void covers(String label, boolean observed) {
    Statistics.label(label)
        .collect(observed)
        .coverage(coverage -> coverage.check(true).count(count -> count > 0));
  }
}
