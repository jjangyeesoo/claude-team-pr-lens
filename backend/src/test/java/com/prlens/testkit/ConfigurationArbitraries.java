package com.prlens.testkit;

import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/**
 * 요구사항 18.6의 범위 안에 있는 {@code Configuration} 생성기 (design.md "속성 기반 테스트 규칙"의 "Configuration"). 범위 밖
 * 값은 내지 않는다.
 */
public final class ConfigurationArbitraries {

  /** {@code ConfigLoader}가 지정한 단가를 기본 단가표에 합치므로(G-10) 읽은 결과에는 기본 단가표의 모델이 항상 있다. */
  private static final List<String> DEFAULT_PRICED_MODELS =
      List.copyOf(Configuration.defaults().pricing().keySet());

  private ConfigurationArbitraries() {}

  private record Limits(
      int sizeLimit, int maxOutputTokens, int maxRetries, BigDecimal maxCostUsdPerReview) {}

  public static Arbitrary<Configuration> configurations() {
    return Combinators.combine(
            patterns(),
            patterns(),
            limits(),
            modelNames(),
            Arbitraries.of("low", "medium", "high", "xhigh", "max"),
            pricing())
        .as(
            (additionalExcludes, removedExcludes, limits, model, effort, pricing) ->
                new Configuration(
                    additionalExcludes,
                    removedExcludes,
                    limits.sizeLimit(),
                    model,
                    effort,
                    limits.maxOutputTokens(),
                    limits.maxRetries(),
                    limits.maxCostUsdPerReview(),
                    pricing));
  }

  /**
   * 중복 없는 glob 패턴 0~3개. {@code *}로 시작하는 것과 YAML에서 따옴표가 필요한 것을 포함한다. 중복은 한 목록 안에서만 없고, 같은 패턴이 추가 목록과
   * 해제 목록에 함께 나올 수 있다.
   */
  private static Arbitrary<List<String>> patterns() {
    return Arbitraries.of(
            "*.lock",
            "**/generated/**",
            "*.min.js",
            "docs/**",
            "src/**/*.{java,kt}",
            "한글/**",
            "a b/*.txt",
            "dir/#not-comment",
            "it's/**",
            "\"quoted\"/*",
            "key: value/*")
        .list()
        .uniqueElements()
        .ofMinSize(0)
        .ofMaxSize(3);
  }

  /**
   * 빈 문자열이 아닌 모델 이름. YAML이 다른 타입으로 읽을 수 있는 값({@code true}, {@code 123}, {@code null})과 역슬래시가 든 값을
   * 포함한다.
   */
  private static Arbitrary<String> modelNames() {
    return Arbitraries.of(
        "claude-opus-5-5",
        "claude-sonnet-5-5",
        "a\\b",
        "model: v1",
        "모델 #1",
        "it's-a-model",
        "\"quoted\"",
        "true",
        "123",
        "null");
  }

  private static Arbitrary<Limits> limits() {
    Arbitrary<BigDecimal> maxCost =
        Arbitraries.integers().between(100, 1_000_000).map(units -> BigDecimal.valueOf(units, 4));
    return Combinators.combine(
            Arbitraries.integers().between(1, 10_000),
            Arbitraries.integers().between(1, 20_000),
            Arbitraries.integers().between(0, 10),
            maxCost)
        .as(Limits::new);
  }

  /** 기본 단가 모델은 항상 있고(단가는 임의), 다른 모델이 0~2개 더 있다. */
  private static Arbitrary<SortedMap<String, ModelPricing>> pricing() {
    Arbitrary<SortedMap<String, ModelPricing>> others =
        Arbitraries.maps(modelNames(), modelPricing()).ofMaxSize(2).map(TreeMap::new);
    Arbitrary<List<ModelPricing>> defaults =
        modelPricing().list().ofSize(DEFAULT_PRICED_MODELS.size());
    return Combinators.combine(defaults, others)
        .as(
            (defaultModelPricing, pricing) -> {
              for (int index = 0; index < DEFAULT_PRICED_MODELS.size(); index++) {
                pricing.put(DEFAULT_PRICED_MODELS.get(index), defaultModelPricing.get(index));
              }
              return pricing;
            });
  }

  private static Arbitrary<ModelPricing> modelPricing() {
    Arbitrary<BigDecimal> price =
        Arbitraries.integers().between(0, 100_000).map(cents -> BigDecimal.valueOf(cents, 2));
    return Combinators.combine(price, price, price, price).as(ModelPricing::new);
  }
}
