package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.codec.ResultCodec;
import com.prlens.filter.DiffFilter;
import com.prlens.filter.FilterOutcome;
import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.Category;
import com.prlens.model.Configuration;
import com.prlens.model.Finding;
import com.prlens.model.ModelPricing;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.Severity;
import com.prlens.model.Usage;
import com.prlens.output.MarkdownFormatter;
import com.prlens.testkit.FixtureLoader;
import com.prlens.testkit.FixtureLoader.Fixture;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 골든 픽스처가 요구사항 14.7, 17.10~17.12를 채우고 스스로 일관된지 본다. 엔진 실행은 20.2가 한다. */
class FixturesTest {

  private static final Set<String> INJECTIONS =
      Set.of("inject-diff-line", "inject-title", "inject-body", "inject-closing-tag");

  private final ResultCodec codec = new ResultCodec();

  @Test
  void everyFixtureHasAllFiles() throws Exception {
    assertThat(FixtureLoader.names())
        .contains("single-basic", "split-three-chunks", "excluded-files")
        .containsAll(INJECTIONS);
    for (String name : FixtureLoader.names()) {
      Path dir = FixtureLoader.directory(name);
      assertThat(dir.resolve("snapshot.json")).as(name).exists();
      assertThat(dir.resolve("context.json")).as(name).exists();
      assertThat(dir.resolve("expected-result.json")).as(name).exists();
      assertThat(dir.resolve("expected.md")).as(name).exists();
      try (var responses = Files.list(dir.resolve("llm-responses"))) {
        assertThat(responses.count()).as(name).isPositive();
      }
    }
  }

  @Test
  void expectedResultIsReadableByResultCodec() {
    for (String name : FixtureLoader.names()) {
      Fixture fixture = FixtureLoader.load(name);

      ReviewResult result = codec.read(fixture.expectedResultJson());

      assertThat(codec.write(result)).as(name).isEqualTo(fixture.expectedResultJson());
    }
  }

  @Test
  void expectedMarkdownEqualsFormatterOutput() {
    for (String name : FixtureLoader.names()) {
      Fixture fixture = FixtureLoader.load(name);
      ReviewResult result = codec.read(fixture.expectedResultJson());

      assertThat(new MarkdownFormatter().format(result, fixture.context()))
          .as(name)
          .isEqualTo(fixture.expectedMarkdown());
    }
  }

  @Test
  void modesCoverSingleSplitAndExcluded() {
    Configuration config = Configuration.defaults();
    ReviewMode single = modeOf("single-basic", config);
    ReviewMode split = modeOf("split-three-chunks", config);
    FilterOutcome excluded = filter("excluded-files", config);

    assertThat(single).isEqualTo(ReviewMode.SINGLE);
    assertThat(split).isEqualTo(ReviewMode.SPLIT);
    assertThat(excluded.excluded()).hasSizeGreaterThanOrEqualTo(3);
    assertThat(
            codec
                .read(FixtureLoader.load("excluded-files").expectedResultJson())
                .excludedFileDetails())
        .containsExactlyElementsOf(excluded.excluded());
  }

  @Test
  void expectedStatsMatchSnapshot() {
    Configuration config = Configuration.defaults();
    for (String name : FixtureLoader.names()) {
      ReviewResult result = codec.read(FixtureLoader.load(name).expectedResultJson());
      List<?> targets = filter(name, config).targets();

      assertThat(result.stats().mode()).as(name).isEqualTo(modeOf(name, config));
      assertThat(result.stats().changedLineCount())
          .as(name)
          .isEqualTo(ChunkPlanner.changedLineCount(filter(name, config).targets()));
      assertThat(result.stats().sizeLimit()).as(name).isEqualTo(config.sizeLimit());
      assertThat(result.stats().chunkCount())
          .as(name)
          .isEqualTo(
              targets.isEmpty()
                  ? 0
                  : ChunkPlanner.plan(filter(name, config).targets(), config.sizeLimit()).size());
      assertThat(FixtureLoader.load(name).llmResponses())
          .as(name)
          .hasSize(result.stats().chunkCount());
    }
  }

  @Test
  void expectedUsageSumsTokensAndPerChunkCosts() {
    ModelPricing pricing = Configuration.defaults().pricing().get("claude-opus-5-5");
    for (String name : FixtureLoader.names()) {
      Fixture fixture = FixtureLoader.load(name);
      Usage usage = codec.read(fixture.expectedResultJson()).usage();
      long input = 0;
      long output = 0;
      long cacheWrite = 0;
      long cacheRead = 0;
      BigDecimal cost = BigDecimal.ZERO;
      for (LlmResponse response : fixture.llmResponses()) {
        LlmUsage u = response.usage();
        input += u.inputTokens();
        output += u.outputTokens();
        cacheWrite += u.cacheWriteTokens();
        cacheRead += u.cacheReadTokens();
        cost = cost.add(chunkCost(u, pricing));
      }

      assertThat(usage.inputTokens()).as(name).isEqualTo(input);
      assertThat(usage.outputTokens()).as(name).isEqualTo(output);
      assertThat(usage.cacheWriteTokens()).as(name).isEqualTo(cacheWrite);
      assertThat(usage.cacheReadTokens()).as(name).isEqualTo(cacheRead);
      assertThat(usage.estimatedCostUsd()).as(name).isEqualByComparingTo(cost);
    }
  }

  /** 비용은 Chunk마다 소수 넷째 자리로 반올림한 뒤 더한다 (요구사항 13.5, 20.10). */
  private static BigDecimal chunkCost(LlmUsage u, ModelPricing pricing) {
    BigDecimal sum =
        pricing
            .inputPerMTok()
            .multiply(BigDecimal.valueOf(u.inputTokens()))
            .add(pricing.outputPerMTok().multiply(BigDecimal.valueOf(u.outputTokens())))
            .add(pricing.cacheWritePerMTok().multiply(BigDecimal.valueOf(u.cacheWriteTokens())))
            .add(pricing.cacheReadPerMTok().multiply(BigDecimal.valueOf(u.cacheReadTokens())));
    return sum.divide(BigDecimal.valueOf(1_000_000), 4, RoundingMode.HALF_UP);
  }

  @Test
  void injectionFixturesExpectSecurityAndBlocker() {
    for (String name : INJECTIONS) {
      Fixture fixture = FixtureLoader.load(name);
      ReviewResult result = codec.read(fixture.expectedResultJson());

      assertThat(result.findings())
          .as(name)
          .extracting(Finding::category)
          .contains(Category.SECURITY);
      assertThat(result.findings())
          .as(name)
          .extracting(Finding::severity)
          .contains(Severity.BLOCKER);
      assertThat(injectionPlace(name, fixture)).as(name).contains("prlens-injection");
    }
  }

  private static String injectionPlace(String name, Fixture fixture) {
    return switch (name) {
      case "inject-title" -> fixture.snapshot().title();
      case "inject-body" -> fixture.snapshot().body();
      case "inject-closing-tag" -> closingTagLine(fixture);
      default -> fixture.snapshot().files().toString();
    };
  }

  private static String closingTagLine(Fixture fixture) {
    String diff = fixture.snapshot().files().toString();
    assertThat(diff).contains("</prlens:file_diff>");
    return diff;
  }

  private static FilterOutcome filter(String name, Configuration config) {
    return DiffFilter.apply(FixtureLoader.load(name).snapshot().files(), config);
  }

  private static ReviewMode modeOf(String name, Configuration config) {
    return ChunkPlanner.decideMode(filter(name, config).targets(), config.sizeLimit());
  }
}
