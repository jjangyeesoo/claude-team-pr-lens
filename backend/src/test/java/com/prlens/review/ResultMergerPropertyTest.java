package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ChunkIssue;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Severity;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

// 작업 11.9: Property 29(청크 결과 병합)
// 입력은 Chunk별 부분 결과(complete, 각종 incomplete, 재시도 소진 실패)를 섞고 적어도 하나는 usage가 있다.
class ResultMergerPropertyTest {

  private static final IncompleteReason[] CHUNK_REASONS = {
    IncompleteReason.SCHEMA_VIOLATION, IncompleteReason.REFUSAL, IncompleteReason.MAX_TOKENS
  };

  @Provide
  Arbitrary<List<ChunkOutcome>> outcomes() {
    return Arbitraries.integers()
        .between(1, 8)
        .flatMap(
            size ->
                Arbitraries.integers()
                    .between(0, 4)
                    .list()
                    .ofSize(size)
                    .flatMap(
                        kinds ->
                            Arbitraries.integers()
                                .between(0, 3000)
                                .list()
                                .ofSize(size)
                                .map(seeds -> build(kinds, seeds))));
  }

  // kind 0,1: complete, 2,3: 불완전(사유 3종), 4: 재시도 소진 실패. 첫 Chunk는 usage가 있게 고정한다
  private static List<ChunkOutcome> build(List<Integer> kinds, List<Integer> seeds) {
    List<ChunkOutcome> outcomes = new ArrayList<>();
    for (int i = 0; i < kinds.size(); i++) {
      int index = i + 1;
      int kind = i == 0 && kinds.get(i) == 4 ? 0 : kinds.get(i);
      int seed = seeds.get(i);
      List<String> files = List.of("dir/f" + index + ".java");
      if (kind == 4) {
        outcomes.add(ChunkOutcome.failed(index, files, seed % 2 == 0 ? 503 : null));
        continue;
      }
      Usage usage =
          new Usage(
              seed, seed / 2, seed / 3, seed / 5, "claude-opus-5-5", BigDecimal.valueOf(seed, 4));
      List<Finding> findings = new ArrayList<>();
      for (int f = 0; f < seed % 3; f++) {
        findings.add(finding("dir/f" + index + ".java", "m" + index + "-" + f));
      }
      IncompleteReason reason = kind >= 2 ? CHUNK_REASONS[(seed + kind) % 3] : null;
      String excerpt = reason == IncompleteReason.SCHEMA_VIOLATION ? "raw" + index : null;
      outcomes.add(
          new ChunkOutcome(index, files, "요약" + index, findings, reason, excerpt, usage, null));
    }
    return outcomes;
  }

  private static Finding finding(String file, String message) {
    return new Finding(
        file,
        1,
        Severity.NIT,
        Category.TEST,
        message,
        null,
        new Basis(BasisType.GENERAL, null),
        null,
        LineVerdict.INLINE_ELIGIBLE,
        null);
  }

  // Feature: pr-lens-p1-cli, Property 29: 청크 결과 병합
  @Property(tries = 300)
  void mergeKeepsFindingsOrderSumsUsageAndRecordsEveryIncompleteChunk(
      @ForAll("outcomes") List<ChunkOutcome> outcomes) {
    ReviewResult result =
        ResultMerger.merge(
            outcomes, List.of(), null, new ReviewStats(ReviewMode.SPLIT, 1, 400, outcomes.size()));

    assertThat(result.findings())
        .containsExactlyElementsOf(outcomes.stream().flatMap(o -> o.findings().stream()).toList());

    Usage usage = result.usage();
    List<Usage> usages = outcomes.stream().map(ChunkOutcome::usage).filter(u -> u != null).toList();
    assertThat(usage.inputTokens()).isEqualTo(usages.stream().mapToLong(Usage::inputTokens).sum());
    assertThat(usage.outputTokens())
        .isEqualTo(usages.stream().mapToLong(Usage::outputTokens).sum());
    assertThat(usage.cacheWriteTokens())
        .isEqualTo(usages.stream().mapToLong(Usage::cacheWriteTokens).sum());
    assertThat(usage.cacheReadTokens())
        .isEqualTo(usages.stream().mapToLong(Usage::cacheReadTokens).sum());
    BigDecimal costSum =
        usages.stream().map(Usage::estimatedCostUsd).reduce(BigDecimal.ZERO, BigDecimal::add);
    assertThat(usage.estimatedCostUsd()).isEqualByComparingTo(costSum);
    assertThat(usage.estimatedCostUsd().signum()).isGreaterThanOrEqualTo(0);

    List<ChunkOutcome> problems =
        outcomes.stream().filter(o -> o.incompleteReason() != null).toList();
    assertThat(result.incompleteReasons()).doesNotHaveDuplicates();
    assertThat(result.incompleteReasons())
        .containsExactlyInAnyOrderElementsOf(
            problems.stream().map(ChunkOutcome::incompleteReason).distinct().toList());
    assertThat(result.status())
        .isEqualTo(problems.isEmpty() ? ResultStatus.COMPLETE : ResultStatus.INCOMPLETE);
    assertThat(result.incompleteDetails().chunks())
        .extracting(ChunkIssue::chunkIndex, ChunkIssue::files, ChunkIssue::reason)
        .containsExactlyElementsOf(
            problems.stream().map(o -> tuple(o.index(), o.files(), o.incompleteReason())).toList());
  }
}
