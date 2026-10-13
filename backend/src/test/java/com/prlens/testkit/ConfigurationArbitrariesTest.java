package com.prlens.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.statistics.Statistics;

/** {@code Configuration} 생성기가 요구사항 18.6의 범위 안에서 round-trip 테스트에 필요한 값을 내는지 확인한다. */
class ConfigurationArbitrariesTest {

  @Provide
  Arbitrary<Configuration> configurations() {
    return ConfigurationArbitraries.configurations();
  }

  @Property(tries = 200)
  void generatedConfigurationsAreWithinValidRanges(
      @ForAll("configurations") Configuration configuration) {
    assertThat(configuration.sizeLimit()).isBetween(1, 10_000);
    assertThat(configuration.maxRetries()).isBetween(0, 10);
    assertThat(configuration.maxOutputTokens()).isBetween(1, 20_000);
    assertThat(configuration.maxCostUsdPerReview())
        .isBetween(new BigDecimal("0.01"), new BigDecimal("100.00"));
    assertThat(configuration.model()).isNotEmpty();
    assertThat(configuration.effort()).isIn("low", "medium", "high", "xhigh", "max");
    assertThat(configuration.pricing()).containsKeys(defaultPricedModels());
    assertThat(configuration.pricing().keySet())
        .allSatisfy(model -> assertThat(model).isNotEmpty());
    assertThat(configuration.pricing().values())
        .allSatisfy(
            pricing ->
                assertThat(prices(pricing))
                    .allSatisfy(price -> assertThat(price.signum()).isNotNegative()));
  }

  @Property(tries = 200)
  void generatedConfigurationsCoverStarPatternsAndSpecialModelNames(
      @ForAll("configurations") Configuration configuration) {
    List<String> patterns =
        Stream.concat(
                configuration.additionalExcludes().stream(),
                configuration.removedExcludes().stream())
            .toList();
    String model = configuration.model();

    covers("*로 시작하는 패턴", patterns.stream().anyMatch(pattern -> pattern.startsWith("*")));
    covers("제외 패턴 없음", patterns.isEmpty());
    covers("해제 패턴 있음", !configuration.removedExcludes().isEmpty());
    covers("특수 문자가 든 모델 이름", model.chars().anyMatch(character -> ":#\"' ".indexOf(character) >= 0));
    covers("역슬래시가 든 모델 이름", model.contains("\\"));
    covers(
        "특수 문자가 든 단가 키",
        configuration.pricing().keySet().stream()
            .anyMatch(key -> key.chars().anyMatch(character -> ":#\"' ".indexOf(character) >= 0)));
    covers(
        "역슬래시가 든 단가 키",
        configuration.pricing().keySet().stream().anyMatch(key -> key.contains("\\")));
    covers(
        "YAML이 다른 타입으로 읽을 수 있는 단가 키",
        configuration.pricing().keySet().stream()
            .anyMatch(key -> List.of("true", "123", "null").contains(key)));
    covers("기본값과 다른 effort", !configuration.effort().equals("medium"));
    covers("모델 단가 2개 이상", configuration.pricing().size() >= 2);
  }

  /** {@code ConfigLoader}가 기본 단가표에 합치므로(G-10) 유효한 설정에는 기본 단가 모델이 항상 있다. */
  private static String[] defaultPricedModels() {
    return Configuration.defaults().pricing().keySet().toArray(String[]::new);
  }

  private static List<BigDecimal> prices(ModelPricing pricing) {
    return List.of(
        pricing.inputPerMTok(),
        pricing.outputPerMTok(),
        pricing.cacheWritePerMTok(),
        pricing.cacheReadPerMTok());
  }

  /** 그 경우가 한 번 이상 생성됐는지 확인한다. */
  private static void covers(String label, boolean observed) {
    Statistics.label(label)
        .collect(observed)
        .coverage(coverage -> coverage.check(true).count(count -> count > 0));
  }
}
