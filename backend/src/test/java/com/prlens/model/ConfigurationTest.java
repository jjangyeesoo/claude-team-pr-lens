package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** 설정 타입 (요구사항 17.4~17.8, 기본값은 ADR 0005와 색인의 D-3 제안값). */
class ConfigurationTest {

  private static final ModelPricing PRICING =
      new ModelPricing(
          new BigDecimal("4.00"),
          new BigDecimal("20.00"),
          new BigDecimal("5.00"),
          new BigDecimal("0.20"));

  private static Configuration config(
      List<String> add, List<String> remove, String maxCost, Map<String, ModelPricing> pricing) {
    return new Configuration(
        add,
        remove,
        400,
        "claude-opus-5-5",
        "medium",
        16_000,
        3,
        new BigDecimal(maxCost),
        new TreeMap<>(pricing));
  }

  @Test
  void defaultsFollowAdr0005AndProposedValues() {
    Configuration defaults = Configuration.defaults();

    assertThat(defaults.additionalExcludes()).isEmpty();
    assertThat(defaults.removedExcludes()).isEmpty();
    assertThat(defaults.sizeLimit()).isEqualTo(400);
    assertThat(defaults.model()).isEqualTo("claude-opus-5-5");
    assertThat(defaults.effort()).isEqualTo("medium");
    assertThat(defaults.maxOutputTokens()).isEqualTo(16_000);
    assertThat(defaults.maxRetries()).isEqualTo(3);
    assertThat(defaults.maxCostUsdPerReview()).isEqualByComparingTo("0.50");
    assertThat(defaults.pricing()).containsOnlyKeys("claude-opus-5-5");
    assertThat(defaults.pricing().get("claude-opus-5-5")).isEqualTo(PRICING);
  }

  @Test
  void listsAreCopiedAndUnmodifiable() {
    List<String> add = new ArrayList<>(List.of("**/*.snap"));
    List<String> remove = new ArrayList<>(List.of("**/dist/**"));
    Configuration config = config(add, remove, "0.50", Map.of());

    add.clear();
    remove.clear();

    assertThat(config.additionalExcludes()).containsExactly("**/*.snap");
    assertThat(config.removedExcludes()).containsExactly("**/dist/**");
    assertThatThrownBy(() -> config.additionalExcludes().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> config.removedExcludes().add("x"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void pricingIsCopiedSortedAndUnmodifiable() {
    TreeMap<String, ModelPricing> source = new TreeMap<>(Map.of("b-model", PRICING));
    Configuration config =
        new Configuration(
            List.of(), List.of(), 400, "m", "medium", 16_000, 3, new BigDecimal("0.50"), source);

    source.put("a-model", PRICING);

    assertThat(config.pricing()).containsOnlyKeys("b-model");
    assertThatThrownBy(() -> config.pricing().put("c-model", PRICING))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThat(config(List.of(), List.of(), "0.50", unordered()).pricing().keySet())
        .containsExactly("a-model", "b-model", "c-model");
  }

  private static Map<String, ModelPricing> unordered() {
    Map<String, ModelPricing> map = new HashMap<>();
    map.put("c-model", PRICING);
    map.put("a-model", PRICING);
    map.put("b-model", PRICING);
    return map;
  }

  @Test
  void costLimitWithDifferentScaleIsEqual() {
    Configuration one = config(List.of(), List.of(), "0.5", Map.of("m", PRICING));
    Configuration other = config(List.of(), List.of(), "0.50", Map.of("m", PRICING));

    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
  }

  @Test
  void pricingUsesNaturalKeyOrderRegardlessOfSourceComparator() {
    TreeMap<String, ModelPricing> reversed = new TreeMap<>(Comparator.reverseOrder());
    reversed.putAll(unordered());
    Configuration config =
        new Configuration(
            List.of(), List.of(), 400, "m", "medium", 16_000, 3, new BigDecimal("0.50"), reversed);

    assertThat(config.pricing().keySet()).containsExactly("a-model", "b-model", "c-model");
  }

  @Test
  void nullPricingValueIsRejectedWithFieldName() {
    TreeMap<String, ModelPricing> source = new TreeMap<>();
    source.put("m", null);

    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Configuration(
                    List.of(),
                    List.of(),
                    400,
                    "m",
                    "medium",
                    16_000,
                    3,
                    new BigDecimal("0.50"),
                    source))
        .withMessage("pricing");
  }

  @Test
  void costLimitIsRoundedHalfUpAtFifthDecimalPlace() {
    assertThat(config(List.of(), List.of(), "0.12345", Map.of()).maxCostUsdPerReview())
        .isEqualTo(new BigDecimal("0.1235"));
    assertThat(config(List.of(), List.of(), "0.12344", Map.of()).maxCostUsdPerReview())
        .isEqualTo(new BigDecimal("0.1234"));
  }

  @Test
  void pricingWithDifferentScaleIsEqual() {
    ModelPricing other =
        new ModelPricing(
            new BigDecimal("4"),
            new BigDecimal("20.0"),
            new BigDecimal("5"),
            new BigDecimal("0.2"));

    assertThat(PRICING).isEqualTo(other).hasSameHashCodeAs(other);
  }

  @Test
  void nullRequiredFieldIsRejectedWithFieldName() {
    BigDecimal one = BigDecimal.ONE;
    TreeMap<String, ModelPricing> none = new TreeMap<>();

    assertThatNullPointerException()
        .isThrownBy(
            () -> new Configuration(null, List.of(), 400, "m", "medium", 16_000, 3, one, none))
        .withMessage("additionalExcludes");
    assertThatNullPointerException()
        .isThrownBy(
            () -> new Configuration(List.of(), null, 400, "m", "medium", 16_000, 3, one, none))
        .withMessage("removedExcludes");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Configuration(List.of(), List.of(), 400, null, "medium", 16_000, 3, one, none))
        .withMessage("model");
    assertThatNullPointerException()
        .isThrownBy(
            () -> new Configuration(List.of(), List.of(), 400, "m", null, 16_000, 3, one, none))
        .withMessage("effort");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new Configuration(List.of(), List.of(), 400, "m", "medium", 16_000, 3, null, none))
        .withMessage("maxCostUsdPerReview");
    assertThatNullPointerException()
        .isThrownBy(
            () -> new Configuration(List.of(), List.of(), 400, "m", "medium", 16_000, 3, one, null))
        .withMessage("pricing");
    assertThatNullPointerException()
        .isThrownBy(() -> new ModelPricing(null, one, one, one))
        .withMessage("inputPerMTok");
    assertThatNullPointerException()
        .isThrownBy(() -> new ModelPricing(one, null, one, one))
        .withMessage("outputPerMTok");
    assertThatNullPointerException()
        .isThrownBy(() -> new ModelPricing(one, one, null, one))
        .withMessage("cacheWritePerMTok");
    assertThatNullPointerException()
        .isThrownBy(() -> new ModelPricing(one, one, one, null))
        .withMessage("cacheReadPerMTok");
  }
}
