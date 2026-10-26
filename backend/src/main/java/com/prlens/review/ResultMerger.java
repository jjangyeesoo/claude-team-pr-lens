package com.prlens.review;

import com.prlens.model.ChunkIssue;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Chunk별 결과를 ReviewResult 하나로 합친다 (요구사항 13.5~13.7, 16.5, 20.10). */
final class ResultMerger {

  private ResultMerger() {}

  /**
   * @param outcomes Chunk 순서. 하나 이상이고, 적어도 하나는 {@code usage}가 있어야 한다
   * @param fileCountGap 파일 목록이 잘렸을 때만 있다. 그 밖에는 null
   */
  static ReviewResult merge(
      List<ChunkOutcome> outcomes,
      List<ExcludedFile> excluded,
      FileCountGap fileCountGap,
      ReviewStats stats) {
    if (outcomes.isEmpty()) {
      throw new IllegalArgumentException("outcomes: 비어 있습니다");
    }
    List<Finding> findings = new ArrayList<>();
    Set<IncompleteReason> reasons = new LinkedHashSet<>();
    List<ChunkIssue> issues = new ArrayList<>();
    for (ChunkOutcome outcome : outcomes) {
      findings.addAll(outcome.findings());
      if (outcome.incompleteReason() != null) {
        reasons.add(outcome.incompleteReason());
        issues.add(issue(outcome));
      }
    }
    if (fileCountGap != null) {
      reasons.add(IncompleteReason.FILES_TRUNCATED);
    }
    List<ExcludedFile> distinct = distinctByPath(excluded);
    return new ReviewResult(
        reasons.isEmpty() ? ResultStatus.COMPLETE : ResultStatus.INCOMPLETE,
        summary(outcomes),
        findings,
        distinct.stream().map(ExcludedFile::path).toList(),
        distinct,
        usage(outcomes),
        List.copyOf(reasons),
        new IncompleteDetails(issues, fileCountGap),
        stats);
  }

  private static ChunkIssue issue(ChunkOutcome outcome) {
    // 원본 응답 발췌는 schema_violation일 때만 싣는다 (ParsedResponse.rawResponseExcerpt와 같은 규칙)
    String excerpt =
        outcome.incompleteReason() == IncompleteReason.SCHEMA_VIOLATION
            ? outcome.rawResponseExcerpt()
            : null;
    return new ChunkIssue(
        outcome.index(),
        outcome.files(),
        outcome.incompleteReason(),
        outcome.failedStatus(),
        excerpt);
  }

  /** Chunk가 둘 이상이면 {@code [n/N] 파일 목록} 머리줄을 붙여 빈 줄로 잇는다. 실패한 Chunk는 뺀다. */
  private static String summary(List<ChunkOutcome> outcomes) {
    if (outcomes.size() == 1) {
      return outcomes.get(0).summary();
    }
    List<String> sections = new ArrayList<>();
    for (ChunkOutcome outcome : outcomes) {
      if (!outcome.isFailed()) {
        sections.add(
            "["
                + outcome.index()
                + "/"
                + outcomes.size()
                + "] "
                + String.join(", ", outcome.files())
                + "\n"
                + outcome.summary());
      }
    }
    return String.join("\n\n", sections);
  }

  /**
   * 토큰은 더하고 비용은 Chunk별 값(이미 넷째 자리로 맞춤)을 더한다. 하나라도 null이면 합도 null이다. 실패한 Chunk는 토큰 0, 비용 0으로 더한다.
   */
  private static Usage usage(List<ChunkOutcome> outcomes) {
    long input = 0;
    long output = 0;
    long cacheWrite = 0;
    long cacheRead = 0;
    BigDecimal cost = BigDecimal.ZERO;
    String model = null;
    for (ChunkOutcome outcome : outcomes) {
      Usage usage = outcome.usage();
      if (usage == null) {
        continue;
      }
      input += usage.inputTokens();
      output += usage.outputTokens();
      cacheWrite += usage.cacheWriteTokens();
      cacheRead += usage.cacheReadTokens();
      cost =
          cost == null || usage.estimatedCostUsd() == null
              ? null
              : cost.add(usage.estimatedCostUsd());
      if (model == null) {
        model = usage.model();
      }
    }
    if (model == null) {
      throw new IllegalArgumentException("outcomes: usage가 있는 Chunk가 하나도 없습니다");
    }
    return new Usage(input, output, cacheWrite, cacheRead, model, cost);
  }

  private static List<ExcludedFile> distinctByPath(List<ExcludedFile> excluded) {
    Map<String, ExcludedFile> byPath = new LinkedHashMap<>();
    for (ExcludedFile file : excluded) {
      byPath.putIfAbsent(file.path(), file);
    }
    return List.copyOf(byPath.values());
  }
}
