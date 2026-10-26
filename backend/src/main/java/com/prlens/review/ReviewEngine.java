package com.prlens.review;

import com.prlens.diff.LineRanges;
import com.prlens.filter.DiffFilter;
import com.prlens.filter.FilterOutcome;
import com.prlens.llm.LlmClient;
import com.prlens.llm.LlmRequest;
import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.ModelPricing;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Usage;
import com.prlens.support.RetriesExhaustedException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** CLI와 서버가 함께 쓰는 리뷰 진입점. 필터, 모드 결정, Chunk별 요청, 검증, 비용 계산, 병합을 잇는다. */
public final class ReviewEngine {

  private static final String NO_TARGET_SUMMARY = "리뷰 대상 파일이 없습니다.";

  private final LlmClient llm;
  private final ReviewListener listener;
  private final ResponseParser parser = new ResponseParser();

  public ReviewEngine(LlmClient llm, ReviewListener listener) {
    this.llm = llm;
    this.listener = listener;
  }

  public ReviewResult review(
      PullRequestSnapshot snapshot, ReviewContext context, Configuration config) {
    // DiffFilter의 경고는 컨텍스트 수집기가 이미 알렸으므로 버린다
    FilterOutcome filtered = DiffFilter.apply(snapshot.files(), config);
    List<ChangedFile> targets = filtered.targets();
    int sizeLimit = config.sizeLimit();

    ReviewMode mode = ChunkPlanner.decideMode(targets, sizeLimit);
    FileCountGap gap =
        snapshot.reportedChangedFiles() > snapshot.files().size()
            ? new FileCountGap(snapshot.reportedChangedFiles(), snapshot.files().size())
            : null;
    List<List<ChangedFile>> chunks = chunks(mode, targets, sizeLimit);
    ReviewStats stats =
        new ReviewStats(mode, ChunkPlanner.changedLineCount(targets), sizeLimit, chunks.size());
    listener.modeDecided(stats);

    if (mode == ReviewMode.NO_TARGET) {
      return noTarget(filtered.excluded(), gap, stats, config);
    }

    Map<String, LineRanges> ranges = lineRanges(targets);
    List<ChunkOutcome> outcomes = new ArrayList<>();
    BigDecimal cumulative = BigDecimal.ZERO;
    boolean exceededNotified = false;
    RetriesExhaustedException lastFailure = null;
    for (int i = 0; i < chunks.size(); i++) {
      int index = i + 1;
      listener.chunkStarted(index, chunks.size());
      List<ChangedFile> chunk = chunks.get(i);
      ChunkOutcome outcome;
      try {
        outcome = reviewChunk(mode, index, chunk, snapshot, context, config, ranges);
      } catch (RetriesExhaustedException e) {
        if (mode != ReviewMode.SPLIT) {
          throw e;
        }
        lastFailure = e;
        outcome = ChunkOutcome.failed(index, paths(chunk), numericStatus(e.lastStatus()));
      }
      outcomes.add(outcome);

      // 누적 비용은 분할 모드에서만 알린다. 단가가 없어 비용을 모르면 누적도 멈춘다
      if (mode == ReviewMode.SPLIT && cumulative != null && !outcome.isFailed()) {
        BigDecimal cost = outcome.usage().estimatedCostUsd();
        cumulative = cost == null ? null : cumulative.add(cost);
        if (!exceededNotified
            && cumulative != null
            && cumulative.compareTo(config.maxCostUsdPerReview()) > 0) {
          exceededNotified = true;
          listener.costThresholdExceeded(
              cumulative, config.maxCostUsdPerReview(), chunks.size() - index);
        }
      }
    }

    if (outcomes.stream().allMatch(ChunkOutcome::isFailed)) {
      throw new AllChunksFailedException(outcomes.size(), lastFailure);
    }
    return ResultMerger.merge(outcomes, filtered.excluded(), gap, stats);
  }

  /** 재시도를 모두 쓴 실패는 {@link RetriesExhaustedException}으로 던진다. 흡수할지는 호출자가 모드로 정한다. */
  private ChunkOutcome reviewChunk(
      ReviewMode mode,
      int index,
      List<ChangedFile> chunk,
      PullRequestSnapshot snapshot,
      ReviewContext context,
      Configuration config,
      Map<String, LineRanges> ranges) {
    LlmRequest request = PromptBuilder.build(mode, chunk, snapshot, context, config);
    LlmResponse response = llm.send(request);

    ParsedResponse parsed = parser.parse(response);
    // 요약 전용 모드도 응답 형식(스키마)은 같으므로 findings는 엔진이 비운다
    List<Finding> findings =
        mode == ReviewMode.SUMMARY_ONLY
            ? List.of()
            : FindingValidator.apply(parsed.findings(), context, ranges);
    return new ChunkOutcome(
        index,
        paths(chunk),
        parsed.summary(),
        findings,
        parsed.incompleteReason(),
        parsed.rawResponseExcerpt(),
        usage(parsed.usage(), config),
        null);
  }

  /** SINGLE과 SUMMARY_ONLY는 대상 전체를 입력 순서대로 Chunk 하나로 쓴다. 경로순 정렬은 SPLIT에서만 한다. */
  private static List<List<ChangedFile>> chunks(
      ReviewMode mode, List<ChangedFile> targets, int sizeLimit) {
    return switch (mode) {
      case NO_TARGET -> List.of();
      case SPLIT -> ChunkPlanner.plan(targets, sizeLimit);
      case SINGLE, SUMMARY_ONLY -> List.of(targets);
    };
  }

  /** 전체 대상 파일의 head 경로 → 변경 줄 구간. 삭제된 파일은 빈 구간이다 (요구사항 12.3, 12.6). */
  private static Map<String, LineRanges> lineRanges(List<ChangedFile> targets) {
    Map<String, LineRanges> ranges = new HashMap<>();
    for (ChangedFile file : targets) {
      ranges.put(
          file.path(),
          file.patch() instanceof PatchContent.Parsed parsed
              ? LineRanges.of(parsed.hunks())
              : LineRanges.of(List.of()));
    }
    return ranges;
  }

  private static ReviewResult noTarget(
      List<ExcludedFile> excluded, FileCountGap gap, ReviewStats stats, Configuration config) {
    List<IncompleteReason> reasons =
        gap == null ? List.of() : List.of(IncompleteReason.FILES_TRUNCATED);
    return new ReviewResult(
        reasons.isEmpty() ? ResultStatus.COMPLETE : ResultStatus.INCOMPLETE,
        NO_TARGET_SUMMARY,
        List.of(),
        excluded.stream().map(ExcludedFile::path).toList(),
        excluded,
        new Usage(0, 0, 0, 0, config.model(), BigDecimal.ZERO),
        reasons,
        new IncompleteDetails(List.of(), gap),
        stats);
  }

  private static Usage usage(LlmUsage usage, Configuration config) {
    ModelPricing pricing = config.pricing().get(config.model());
    return new Usage(
        usage.inputTokens(),
        usage.outputTokens(),
        usage.cacheWriteTokens(),
        usage.cacheReadTokens(),
        config.model(),
        pricing == null ? null : CostCalculator.estimate(usage, pricing));
  }

  private static List<String> paths(List<ChangedFile> chunk) {
    return chunk.stream().map(ChangedFile::path).toList();
  }

  /** 마지막 응답이 HTTP 상태 코드일 때만 숫자로 바꾼다. 네트워크 오류 종류(문자열)면 null이다. */
  private static Integer numericStatus(String lastStatus) {
    return lastStatus != null && lastStatus.matches("[0-9]{1,9}")
        ? Integer.valueOf(lastStatus)
        : null;
  }
}
