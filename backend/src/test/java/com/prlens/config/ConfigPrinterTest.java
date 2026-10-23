package com.prlens.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class ConfigPrinterTest {

  private static Configuration with(
      List<String> add, String model, BigDecimal cost, SortedMap<String, ModelPricing> pricing) {
    Configuration base = Configuration.defaults();
    return new Configuration(
        add,
        base.removedExcludes(),
        base.sizeLimit(),
        model,
        base.effort(),
        base.maxOutputTokens(),
        base.maxRetries(),
        cost,
        pricing);
  }

  private static SortedMap<String, ModelPricing> pricingOf(String model, ModelPricing pricing) {
    SortedMap<String, ModelPricing> map = new TreeMap<>();
    map.put(model, pricing);
    return map;
  }

  private static Configuration roundTrip(Configuration configuration) {
    return new ConfigLoader(Path.of("."), Path.of(".")).parse(ConfigPrinter.print(configuration));
  }

  // 18.10
  @Test
  void printsDefaultsInFixedOrder() {
    assertThat(ConfigPrinter.print(Configuration.defaults()))
        .isEqualTo(
            "exclude:\n"
                + "  add: []\n"
                + "  remove: []\n"
                + "review:\n"
                + "  sizeLimit: 400\n"
                + "llm:\n"
                + "  model: \"claude-opus-5-5\"\n"
                + "  effort: \"medium\"\n"
                + "  maxOutputTokens: 16000\n"
                + "retry:\n"
                + "  maxRetries: 3\n"
                + "cost:\n"
                + "  maxUsdPerReview: 0.50\n"
                + "  pricing:\n"
                + "    \"claude-opus-5-5\": { input: 4, output: 20, cacheWrite: 5, cacheRead: 0.2"
                + " }\n");
  }

  // 18.10, 18.11
  @Test
  void quotesStringsAlways() {
    List<String> patterns =
        List.of(
            "**/x", "true", "123", "null", "a b/*.txt", "key: value/*", "\"quoted\"/*", "한글/**");
    Configuration configuration =
        with(
            patterns,
            "123",
            Configuration.defaults().maxCostUsdPerReview(),
            Configuration.defaults().pricing());

    String printed = ConfigPrinter.print(configuration);
    assertThat(printed)
        .contains(
            "  add: [\"**/x\", \"true\", \"123\", \"null\", \"a b/*.txt\", \"key: value/*\","
                + " \"\\\"quoted\\\"/*\", \"한글/**\"]")
        .contains("  model: \"123\"");
    assertThat(roundTrip(configuration)).isEqualTo(configuration);
  }

  // 18.11 (주의: pricing의 키도 따옴표로 감싸고 `"`와 `\`를 이스케이프한다)
  @Test
  void printsPricingKeysQuotedAndEscaped() {
    ModelPricing price =
        new ModelPricing(
            new BigDecimal("1"), new BigDecimal("2"), new BigDecimal("3"), new BigDecimal("4"));
    SortedMap<String, ModelPricing> pricing = new TreeMap<>();
    pricing.put("claude-opus-5-5", price);
    pricing.put("a\\b\"c", price);
    pricing.put("model: v1", price);
    pricing.put("null", price);
    Configuration configuration =
        with(List.of(), "m", Configuration.defaults().maxCostUsdPerReview(), pricing);

    assertThat(ConfigPrinter.print(configuration))
        .contains("    \"a\\\\b\\\"c\": { input: 1, output: 2, cacheWrite: 3, cacheRead: 4 }")
        .contains("    \"model: v1\": {")
        .contains("    \"null\": {");
    assertThat(roundTrip(configuration)).isEqualTo(configuration);
  }

  // 18.11 (주의: 단가는 `2E+1`이 아니라 평문, 비용 상한은 `0.5000`이 아니라 `0.50`)
  @Test
  void printsNumbersPlain() {
    ModelPricing price =
        new ModelPricing(
            new BigDecimal("20.00"),
            new BigDecimal("0.50"),
            new BigDecimal("1000"),
            new BigDecimal("0.0125"));
    Configuration configuration =
        with(List.of(), "m", new BigDecimal("100"), pricingOf("claude-opus-5-5", price));

    assertThat(ConfigPrinter.print(configuration))
        .contains("  maxUsdPerReview: 100.00")
        .contains("{ input: 20, output: 0.5, cacheWrite: 1000, cacheRead: 0.0125 }")
        .doesNotContain("E+");
    assertThat(
            ConfigPrinter.print(
                with(List.of(), "m", new BigDecimal("0.0123"), Configuration.defaults().pricing())))
        .contains("  maxUsdPerReview: 0.0123");
    assertThat(roundTrip(configuration)).isEqualTo(configuration);
  }

  // 18.11
  @Test
  void escapesControlCharacters() {
    Configuration configuration =
        with(
            List.of("a\tb"),
            "a\nb\r\u0001\u2028c",
            Configuration.defaults().maxCostUsdPerReview(),
            Configuration.defaults().pricing());

    String printed = ConfigPrinter.print(configuration);
    assertThat(printed).contains("\"a\\nb\\r\\u0001\\u2028c\"").contains("\"a\\tb\"");
    assertThat(printed.split("\n", -1)).hasSize(16);
    assertThat(roundTrip(configuration)).isEqualTo(configuration);
  }
}
