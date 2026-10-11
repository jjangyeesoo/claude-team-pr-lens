package com.prlens.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/** 리뷰 한 번에 쓰는 설정. 값의 범위 검증은 {@code ConfigLoader}가 한다. */
public record Configuration(
    List<String> additionalExcludes,
    List<String> removedExcludes,
    int sizeLimit,
    String model,
    String effort,
    int maxOutputTokens,
    int maxRetries,
    BigDecimal maxCostUsdPerReview,
    SortedMap<String, ModelPricing> pricing) {
  public Configuration {
    Objects.requireNonNull(additionalExcludes, "additionalExcludes");
    Objects.requireNonNull(removedExcludes, "removedExcludes");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(effort, "effort");
    Objects.requireNonNull(maxCostUsdPerReview, "maxCostUsdPerReview");
    Objects.requireNonNull(pricing, "pricing");
    additionalExcludes = List.copyOf(additionalExcludes);
    removedExcludes = List.copyOf(removedExcludes);
    maxCostUsdPerReview = maxCostUsdPerReview.setScale(4, RoundingMode.HALF_UP);
    pricing = Collections.unmodifiableSortedMap(naturallyOrdered(pricing));
  }

  // new TreeMap<>(SortedMap)은 원본의 comparator를 물려받으므로 빈 맵에 옮겨 담아 키의 자연 순서로 고정한다.
  private static SortedMap<String, ModelPricing> naturallyOrdered(
      SortedMap<String, ModelPricing> source) {
    SortedMap<String, ModelPricing> copy = new TreeMap<>();
    source.forEach(
        (model, modelPricing) ->
            copy.put(
                Objects.requireNonNull(model, "pricing"),
                Objects.requireNonNull(modelPricing, "pricing")));
    return copy;
  }

  /** 모델, effort, 최대 출력 토큰은 ADR 0005, 비용 상한은 D-3 제안값, 단가는 PRD 6장(USD per 1M tokens)이다. */
  public static Configuration defaults() {
    SortedMap<String, ModelPricing> pricing = new TreeMap<>();
    pricing.put(
        "claude-opus-5-5",
        new ModelPricing(
            new BigDecimal("4.00"),
            new BigDecimal("20.00"),
            new BigDecimal("5.00"),
            new BigDecimal("0.20")));
    return new Configuration(
        List.of(),
        List.of(),
        400,
        "claude-opus-5-5",
        "medium",
        16_000,
        3,
        new BigDecimal("0.50"),
        pricing);
  }
}
