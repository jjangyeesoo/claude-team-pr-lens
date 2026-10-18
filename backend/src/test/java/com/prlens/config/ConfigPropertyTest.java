package com.prlens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prlens.model.Configuration;
import com.prlens.testkit.ConfigurationArbitraries;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.yaml.snakeyaml.Yaml;

class ConfigPropertyTest {

  private final ConfigLoader loader = new ConfigLoader(Path.of("."), Path.of("."));

  @Provide
  Arbitrary<Configuration> configurations() {
    return ConfigurationArbitraries.configurations();
  }

  @Provide
  Arbitrary<List<Boolean>> flags() {
    return Arbitraries.of(true, false).list().ofSize(10);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asMap(Object value) {
    return (Map<String, Object>) value;
  }

  private static Map<String, Object> treeOf(Configuration configuration) {
    return asMap(new Yaml().load(ConfigPrinter.print(configuration)));
  }

  private static String dump(Map<String, Object> tree) {
    return new Yaml().dump(tree);
  }

  private record Item(String section, String key) {}

  private static final List<Item> ITEMS =
      List.of(
          new Item("exclude", "add"),
          new Item("exclude", "remove"),
          new Item("review", "sizeLimit"),
          new Item("llm", "model"),
          new Item("llm", "effort"),
          new Item("llm", "maxOutputTokens"),
          new Item("retry", "maxRetries"),
          new Item("cost", "maxUsdPerReview"),
          new Item("cost", "pricing"));

  // Feature: pr-lens-p1-cli, Property 37: 설정 round-trip과 부분 지정
  @Property(tries = 300)
  void printedConfigurationReadsBackAndPartialSpecificationFillsDefaults(
      @ForAll("configurations") Configuration configuration, @ForAll("flags") List<Boolean> keep) {
    assertThat(loader.parse(ConfigPrinter.print(configuration))).isEqualTo(configuration);

    Map<String, Object> tree = treeOf(configuration);
    Configuration defaults = Configuration.defaults();
    for (int index = 0; index < ITEMS.size(); index++) {
      if (!keep.get(index)) {
        Item item = ITEMS.get(index);
        asMap(tree.get(item.section())).remove(item.key());
      }
    }
    Configuration loaded = loader.parse(dump(tree));

    assertThat(loaded)
        .isEqualTo(
            new Configuration(
                keep.get(0) ? configuration.additionalExcludes() : defaults.additionalExcludes(),
                keep.get(1) ? configuration.removedExcludes() : defaults.removedExcludes(),
                keep.get(2) ? configuration.sizeLimit() : defaults.sizeLimit(),
                keep.get(3) ? configuration.model() : defaults.model(),
                keep.get(4) ? configuration.effort() : defaults.effort(),
                keep.get(5) ? configuration.maxOutputTokens() : defaults.maxOutputTokens(),
                keep.get(6) ? configuration.maxRetries() : defaults.maxRetries(),
                keep.get(7) ? configuration.maxCostUsdPerReview() : defaults.maxCostUsdPerReview(),
                keep.get(8) ? configuration.pricing() : defaults.pricing()));
  }

  /** 주입할 문제: 항목 이름과, 유효한 설정 트리에 그 문제를 넣는 방법. */
  private record Injection(String item, Consumer<Map<String, Object>> apply) {}

  private static Map<String, Object> pricingOf(Map<String, Object> tree) {
    return asMap(asMap(tree.get("cost")).get("pricing"));
  }

  private static final List<Injection> INJECTIONS =
      List.of(
          new Injection(
              "review.sizeLimit", tree -> asMap(tree.get("review")).put("sizeLimit", 10_001)),
          new Injection("retry.maxRetries", tree -> asMap(tree.get("retry")).put("maxRetries", -1)),
          new Injection(
              "llm.maxOutputTokens", tree -> asMap(tree.get("llm")).put("maxOutputTokens", 20_001)),
          new Injection(
              "cost.maxUsdPerReview",
              tree -> asMap(tree.get("cost")).put("maxUsdPerReview", 100.01)),
          new Injection("llm.effort", tree -> asMap(tree.get("llm")).put("effort", "ultra")),
          new Injection("llm.model", tree -> asMap(tree.get("llm")).put("model", "")),
          new Injection(
              "cost.pricing.inj.input",
              tree ->
                  pricingOf(tree)
                      .put(
                          "inj",
                          new LinkedHashMap<>(
                              Map.of("input", -1, "output", 1, "cacheWrite", 1, "cacheRead", 1)))),
          new Injection(
              "cost.pricing.inj2.cacheRead",
              tree ->
                  pricingOf(tree)
                      .put(
                          "inj2",
                          new LinkedHashMap<>(Map.of("input", 1, "output", 1, "cacheWrite", 1)))),
          new Injection("unknownTop", tree -> tree.put("unknownTop", 1)),
          new Injection(
              "llm.unknownNested", tree -> asMap(tree.get("llm")).put("unknownNested", 1)));

  // Feature: pr-lens-p1-cli, Property 38: 설정 검증 오류 수집
  @Property(tries = 300)
  void reportedItemsEqualInjectedProblems(
      @ForAll("configurations") Configuration configuration,
      @ForAll("flags") List<Boolean> inject) {
    Map<String, Object> tree = treeOf(configuration);
    Set<String> injected = new HashSet<>();
    for (int index = 0; index < INJECTIONS.size(); index++) {
      if (inject.get(index)) {
        INJECTIONS.get(index).apply().accept(tree);
        injected.add(INJECTIONS.get(index).item());
      }
    }
    String yaml = dump(tree);

    if (injected.isEmpty()) {
      assertThat(loader.parse(yaml)).isEqualTo(configuration);
      return;
    }
    ConfigException error = assertThrows(ConfigException.class, () -> loader.parse(yaml));
    assertThat(error.problems().stream().map(ConfigException.Problem::item).toList())
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(injected);
  }
}
