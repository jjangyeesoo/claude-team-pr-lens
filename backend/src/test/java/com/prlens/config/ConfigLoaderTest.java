package com.prlens.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConfigLoaderTest {

  @TempDir Path work;
  @TempDir Path home;

  private ConfigLoader loader() {
    return new ConfigLoader(work, home);
  }

  private static String yaml(String... lines) {
    return String.join("\n", lines) + "\n";
  }

  private static void write(Path file, String text) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, text, StandardCharsets.UTF_8);
  }

  private Set<String> itemsOf(String yaml) {
    ConfigException error = assertThrows(ConfigException.class, () -> loader().parse(yaml));
    return Set.copyOf(error.problems().stream().map(ConfigException.Problem::item).toList());
  }

  // 18.1
  @Test
  void loadsExplicitFileOnly() throws IOException {
    write(work.resolve(".prlens.yml"), yaml("review:", "  sizeLimit: 111"));
    write(home.resolve(".prlens.yml"), yaml("review:", "  sizeLimit: 222"));
    Path explicit = work.resolve("other.yml");
    write(explicit, yaml("review:", "  sizeLimit: 333"));

    assertThat(loader().load(explicit).sizeLimit()).isEqualTo(333);
  }

  // 18.1
  @Test
  void loadsFirstOfWorkingDirThenHome() throws IOException {
    write(home.resolve(".prlens.yml"), yaml("review:", "  sizeLimit: 222"));
    assertThat(loader().load(null).sizeLimit()).isEqualTo(222);

    write(work.resolve(".prlens.yml"), yaml("review:", "  sizeLimit: 111"));
    Configuration loaded = loader().load(null);
    assertThat(loaded.sizeLimit()).isEqualTo(111);
    assertThat(loaded.maxRetries()).as("홈 파일의 항목은 섞이지 않는다").isEqualTo(3);
  }

  // 22.2
  @Test
  void resolvesRelativeConfigPathAgainstWorkingDirectory() throws IOException {
    write(work.resolve("conf/x.yml"), yaml("retry:", "  maxRetries: 7"));

    assertThat(loader().load(Path.of("conf/x.yml")).maxRetries()).isEqualTo(7);
  }

  // 18.2, 18.3, 18.4
  @Test
  void returnsDefaultsWhenNoFile() {
    Configuration loaded = loader().load(null);

    assertThat(loaded).isEqualTo(Configuration.defaults());
    assertThat(loaded.additionalExcludes()).isEmpty();
    assertThat(loaded.removedExcludes()).isEmpty();
    assertThat(loaded.sizeLimit()).isEqualTo(400);
    assertThat(loaded.model()).isEqualTo("claude-opus-5-5");
    assertThat(loaded.effort()).isEqualTo("medium");
    assertThat(loaded.maxOutputTokens()).isEqualTo(16_000);
    assertThat(loaded.maxRetries()).isEqualTo(3);
    assertThat(loaded.maxCostUsdPerReview()).isEqualByComparingTo("0.50");
    ModelPricing opus = loaded.pricing().get("claude-opus-5-5");
    assertThat(opus.inputPerMTok()).isEqualByComparingTo("4.00");
    assertThat(opus.outputPerMTok()).isEqualByComparingTo("20.00");
    assertThat(opus.cacheWritePerMTok()).isEqualByComparingTo("5.00");
    assertThat(opus.cacheReadPerMTok()).isEqualByComparingTo("0.20");
  }

  // 18.2
  @Test
  void returnsDefaultsForEmptyFile() throws IOException {
    assertThat(loader().parse("")).isEqualTo(Configuration.defaults());
    assertThat(loader().parse("# 주석만 있다\n")).isEqualTo(Configuration.defaults());
    write(work.resolve(".prlens.yml"), "");
    assertThat(loader().load(null)).isEqualTo(Configuration.defaults());
  }

  // 18.3
  @Test
  void parsesEveryItem() {
    Configuration loaded =
        loader()
            .parse(
                yaml(
                    "exclude:",
                    "  add: [\"**/*.snap\", \"docs/**\"]",
                    "  remove: [\"**/dist/**\"]",
                    "review:",
                    "  sizeLimit: 250",
                    "llm:",
                    "  model: \"claude-sonnet-5-5\"",
                    "  effort: \"high\"",
                    "  maxOutputTokens: 12000",
                    "retry:",
                    "  maxRetries: 5",
                    "cost:",
                    "  maxUsdPerReview: 1.25",
                    "  pricing:",
                    "    \"claude-sonnet-5-5\": { input: 3, output: 15, cacheWrite: 3.75,"
                        + " cacheRead: 0.3 }"));

    assertThat(loaded.additionalExcludes()).containsExactly("**/*.snap", "docs/**");
    assertThat(loaded.removedExcludes()).containsExactly("**/dist/**");
    assertThat(loaded.sizeLimit()).isEqualTo(250);
    assertThat(loaded.model()).isEqualTo("claude-sonnet-5-5");
    assertThat(loaded.effort()).isEqualTo("high");
    assertThat(loaded.maxOutputTokens()).isEqualTo(12_000);
    assertThat(loaded.maxRetries()).isEqualTo(5);
    assertThat(loaded.maxCostUsdPerReview()).isEqualByComparingTo("1.25");
    assertThat(loaded.pricing().get("claude-sonnet-5-5"))
        .isEqualTo(
            new ModelPricing(
                new BigDecimal("3"),
                new BigDecimal("15"),
                new BigDecimal("3.75"),
                new BigDecimal("0.3")));
  }

  // 18.5
  @Test
  void fillsUnspecifiedItemsWithDefaults() {
    Configuration loaded = loader().parse(yaml("review:", "  sizeLimit: 123"));

    assertThat(loaded.sizeLimit()).isEqualTo(123);
    assertThat(loaded).isEqualTo(withSizeLimit(Configuration.defaults(), 123));
  }

  private static Configuration withSizeLimit(Configuration base, int sizeLimit) {
    return new Configuration(
        base.additionalExcludes(),
        base.removedExcludes(),
        sizeLimit,
        base.model(),
        base.effort(),
        base.maxOutputTokens(),
        base.maxRetries(),
        base.maxCostUsdPerReview(),
        base.pricing());
  }

  // 18.6
  @Test
  void acceptsBoundaryValues() {
    for (String effort : List.of("low", "medium", "high", "xhigh", "max")) {
      assertThat(loader().parse(yaml("llm:", "  effort: \"" + effort + "\"")).effort())
          .isEqualTo(effort);
    }
    Configuration low =
        loader()
            .parse(
                yaml(
                    "review: { sizeLimit: 1 }",
                    "llm: { maxOutputTokens: 1 }",
                    "retry: { maxRetries: 0 }",
                    "cost:",
                    "  maxUsdPerReview: 0.01",
                    "  pricing:",
                    "    \"m\": { input: 0, output: 0, cacheWrite: 0, cacheRead: 0 }"));
    assertThat(low.sizeLimit()).isEqualTo(1);
    assertThat(low.maxOutputTokens()).isEqualTo(1);
    assertThat(low.maxRetries()).isZero();
    assertThat(low.maxCostUsdPerReview()).isEqualByComparingTo("0.01");
    assertThat(low.pricing().get("m").inputPerMTok()).isEqualByComparingTo("0");

    Configuration high =
        loader()
            .parse(
                yaml(
                    "review: { sizeLimit: 10000 }",
                    "llm: { maxOutputTokens: 20000 }",
                    "retry: { maxRetries: 10 }",
                    "cost: { maxUsdPerReview: 100.00 }"));
    assertThat(high.sizeLimit()).isEqualTo(10_000);
    assertThat(high.maxOutputTokens()).isEqualTo(20_000);
    assertThat(high.maxRetries()).isEqualTo(10);
    assertThat(high.maxCostUsdPerReview()).isEqualByComparingTo("100.00");
  }

  static Stream<Arguments> outOfRange() {
    return Stream.of(
        Arguments.of("review: { sizeLimit: 0 }", "review.sizeLimit"),
        Arguments.of("review: { sizeLimit: 10001 }", "review.sizeLimit"),
        Arguments.of("retry: { maxRetries: -1 }", "retry.maxRetries"),
        Arguments.of("retry: { maxRetries: 11 }", "retry.maxRetries"),
        Arguments.of("llm: { maxOutputTokens: 0 }", "llm.maxOutputTokens"),
        Arguments.of("llm: { maxOutputTokens: 20001 }", "llm.maxOutputTokens"),
        Arguments.of("cost: { maxUsdPerReview: 0.009 }", "cost.maxUsdPerReview"),
        Arguments.of("cost: { maxUsdPerReview: 100.01 }", "cost.maxUsdPerReview"),
        Arguments.of("llm: { effort: \"ultra\" }", "llm.effort"),
        Arguments.of("llm: { effort: \"Medium\" }", "llm.effort"),
        Arguments.of("llm: { model: \"\" }", "llm.model"),
        Arguments.of("llm: { model: \"   \" }", "llm.model"),
        Arguments.of(
            "cost: { pricing: { \"m\": { input: -1, output: 1, cacheWrite: 1, cacheRead: 1 } } }",
            "cost.pricing.m.input"));
  }

  // 18.6, 18.7
  @ParameterizedTest
  @MethodSource("outOfRange")
  void rejectsEachOutOfRangeValue(String yaml, String item) {
    assertThat(itemsOf(yaml)).containsExactly(item);
  }

  static Stream<Arguments> wrongTypes() {
    return Stream.of(
        Arguments.of("review: { sizeLimit: 400.5 }", "review.sizeLimit"),
        Arguments.of("review: { sizeLimit: \"400\" }", "review.sizeLimit"),
        Arguments.of("review: { sizeLimit: [1] }", "review.sizeLimit"),
        Arguments.of("llm: \"x\"", "llm"),
        Arguments.of("exclude: { add: \"x\" }", "exclude.add"),
        Arguments.of("exclude: { add: [1] }", "exclude.add[0]"),
        Arguments.of("llm: { model: 123 }", "llm.model"),
        Arguments.of("- a\n- b", "$"));
  }

  // 18.6, 18.7
  @ParameterizedTest
  @MethodSource("wrongTypes")
  void rejectsNonIntegerAndWrongTypes(String yaml, String item) {
    assertThat(itemsOf(yaml)).containsExactly(item);
  }

  // 18.6 (G-10)
  @Test
  void mergesPricingByModel() {
    Configuration added =
        loader()
            .parse(
                yaml(
                    "cost:",
                    "  pricing:",
                    "    \"m1\": { input: 1, output: 2, cacheWrite: 3, cacheRead: 4 }"));
    assertThat(added.pricing().keySet()).containsExactly("claude-opus-5-5", "m1");
    assertThat(added.pricing().get("claude-opus-5-5"))
        .isEqualTo(Configuration.defaults().pricing().get("claude-opus-5-5"));

    Configuration overridden =
        loader()
            .parse(
                yaml(
                    "cost:",
                    "  pricing:",
                    "    \"claude-opus-5-5\": { input: 9, output: 9, cacheWrite: 9, cacheRead:"
                        + " 9 }"));
    assertThat(overridden.pricing().keySet()).containsExactly("claude-opus-5-5");
    assertThat(overridden.pricing().get("claude-opus-5-5").inputPerMTok())
        .isEqualByComparingTo("9");
  }

  // 18.6 (G-10)
  @Test
  void requiresAllFourPricesForSpecifiedModel() {
    assertThat(itemsOf(yaml("cost:", "  pricing:", "    \"m\": { input: 1, output: 2 }")))
        .containsExactlyInAnyOrder("cost.pricing.m.cacheWrite", "cost.pricing.m.cacheRead");
  }

  // 18.6 (G-10): 값 없이 적은 모델도 네 단가 누락이다
  @Test
  void rejectsPricedModelWithoutValue() {
    Set<String> four =
        Set.of(
            "cost.pricing.m.input",
            "cost.pricing.m.output",
            "cost.pricing.m.cacheWrite",
            "cost.pricing.m.cacheRead");

    assertThat(itemsOf(yaml("cost:", "  pricing:", "    \"m\":"))).isEqualTo(four);
    assertThat(itemsOf(yaml("cost:", "  pricing:", "    \"m\": ~"))).isEqualTo(four);
    assertThat(itemsOf(yaml("cost:", "  pricing:", "    \"m\": {}"))).isEqualTo(four);
  }

  // 18.7
  @Test
  void reportsAllProblemsAtOnce() {
    String yaml =
        yaml(
            "review: { sizeLimit: 0 }",
            "retry: { maxRetries: 99 }",
            "llm: { effort: \"ultra\", foo: 1 }",
            "bar: 2");

    ConfigException error = assertThrows(ConfigException.class, () -> loader().parse(yaml));
    assertThat(error.problems().stream().map(ConfigException.Problem::item))
        .containsExactlyInAnyOrder(
            "review.sizeLimit", "retry.maxRetries", "llm.effort", "llm.foo", "bar");
    assertThat(error.getMessage())
        .contains("review.sizeLimit", "retry.maxRetries", "llm.effort", "llm.foo", "bar");
  }

  // 18.7
  @Test
  void rejectsUnknownKeys() {
    assertThat(itemsOf(yaml("foo: 1", "llm: { foo: 1 }", "exclude: { bar: [] }")))
        .containsExactlyInAnyOrder("foo", "llm.foo", "exclude.bar");
  }

  // 18.7
  @Test
  void reportsSyntaxErrorWithLine() {
    ConfigException error =
        assertThrows(
            ConfigException.class, () -> loader().parse("review:\n  sizeLimit: 1\n  bad: [\n"));

    assertThat(error.problems()).hasSize(1);
    assertThat(error.problems().get(0).item()).isEqualTo("syntax");
    assertThat(error.problems().get(0).message()).contains("줄");
  }

  // 18.7
  @Test
  void rejectsDuplicateKeys() {
    assertThat(itemsOf(yaml("review:", "  sizeLimit: 1", "  sizeLimit: 2")))
        .containsExactly("review.sizeLimit");
  }

  static Stream<Arguments> secretKeys() {
    return Stream.of(
        Arguments.of("github_token", "GITHUB_TOKEN"),
        Arguments.of("githubToken", "GITHUB_TOKEN"),
        Arguments.of("Token", "GITHUB_TOKEN"),
        Arguments.of("API-KEY", "ANTHROPIC_API_KEY"),
        Arguments.of("anthropicApiKey", "ANTHROPIC_API_KEY"),
        Arguments.of("apiKey", "ANTHROPIC_API_KEY"));
  }

  // 18.8, 19.1
  @ParameterizedTest
  @MethodSource("secretKeys")
  void rejectsSecretKeysWithoutPrintingValue(String key, String environmentVariable) {
    for (String yaml :
        List.of(key + ": ghp_secretvalue123", "llm:\n  " + key + ": ghp_secretvalue123")) {
      ConfigException error = assertThrows(ConfigException.class, () -> loader().parse(yaml));

      assertThat(error.getMessage()).contains(key).contains(environmentVariable);
      assertThat(error.getMessage()).doesNotContain("ghp_secretvalue123");
      assertThat(error.problems()).hasSize(1);
    }
  }

  // 18.9
  @Test
  void explicitMissingFileFailsWithoutFallback() throws IOException {
    write(work.resolve(".prlens.yml"), yaml("review:", "  sizeLimit: 111"));

    assertThatThrownBy(() -> loader().load(Path.of("nope.yml")))
        .isInstanceOfSatisfying(
            ConfigException.class,
            e -> {
              assertThat(e.problems().get(0).item()).isEqualTo("--config");
              assertThat(e.getMessage()).contains("nope.yml").contains("파일이 없습니다");
            });
    assertThatThrownBy(() -> loader().load(work))
        .isInstanceOfSatisfying(
            ConfigException.class,
            e -> {
              assertThat(e.problems().get(0).item()).isEqualTo("--config");
              assertThat(e.getMessage()).contains("폴더");
            });
  }

  // 3.11
  @Test
  void rejectsInvalidGlobWithItemAndPattern() {
    ConfigException error =
        assertThrows(
            ConfigException.class,
            () ->
                loader()
                    .parse(yaml("exclude:", "  add: [\"ok/**\", \"a{b\"]", "  remove: ['a\\b']")));

    assertThat(error.problems().stream().map(ConfigException.Problem::item))
        .containsExactlyInAnyOrder("exclude.add[1]", "exclude.remove[0]");
    assertThat(error.getMessage()).contains("a{b");
  }

  // 22.3
  @Test
  void readsUtf8WithoutBom() throws IOException {
    byte[] body =
        yaml("llm:", "  model: \"모델\"", "exclude:", "  add: [\"한글/**\"]")
            .getBytes(StandardCharsets.UTF_8);
    byte[] withBom = new byte[body.length + 3];
    withBom[0] = (byte) 0xEF;
    withBom[1] = (byte) 0xBB;
    withBom[2] = (byte) 0xBF;
    System.arraycopy(body, 0, withBom, 3, body.length);
    Path file = work.resolve("utf8.yml");
    Files.write(file, withBom);

    Configuration loaded = loader().load(file);
    assertThat(loaded.model()).isEqualTo("모델");
    assertThat(loaded.additionalExcludes()).containsExactly("한글/**");
  }

  // 22.3
  @Test
  void rejectsInvalidUtf8() throws IOException {
    Path file = work.resolve("bad.yml");
    Files.write(file, new byte[] {'m', ':', ' ', (byte) 0xFF, (byte) 0xFE, '\n'});

    assertThatThrownBy(() -> loader().load(file))
        .isInstanceOfSatisfying(
            ConfigException.class,
            e -> assertThat(e.problems().get(0).item()).isEqualTo("encoding"));
  }

  // 결정(16.1)
  @Test
  void roundsCostBeyondFourDecimals() {
    assertThat(loader().parse(yaml("cost: { maxUsdPerReview: 0.123456 }")).maxCostUsdPerReview())
        .isEqualTo(new BigDecimal("0.1235"));
  }

  // 결정(16.1): 범위 검사는 반올림 전 원본 값에 한다
  @Test
  void rejectsCostBelowMinimumBeforeRounding() {
    assertThat(itemsOf(yaml("cost: { maxUsdPerReview: 0.00996 }")))
        .containsExactly("cost.maxUsdPerReview");
    assertThat(itemsOf(yaml("cost: { maxUsdPerReview: 100.00001 }")))
        .containsExactly("cost.maxUsdPerReview");
  }
}
