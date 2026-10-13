package com.prlens.testkit;

import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ChunkIssue;
import com.prlens.model.Demotion;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.IncompleteDetails;
import com.prlens.model.IncompleteReason;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Severity;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.model.Usage;
import java.math.BigDecimal;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/**
 * {@code ReviewResult} 생성기 (design.md "속성 기반 테스트 규칙"의 "ReviewResult(특수 문자 문자열)").
 *
 * <p>생성자가 검사하는 불변식 외에 다음도 지킨다.
 *
 * <ul>
 *   <li>{@code GENERAL} basis의 {@code ref}는 null이고, 그 밖의 basis는 {@code ref}가 있다
 *   <li>{@code rawResponseExcerpt}는 {@code SCHEMA_VIOLATION}일 때만, {@code fileCountGap}은 {@code
 *       FILES_TRUNCATED}일 때만 있다
 *   <li>{@code INLINE_ELIGIBLE}과 {@code OUT_OF_RANGE}는 {@code line}이 있고 {@code LINE_MISSING}은
 *       {@code line}이 null이다. {@code NOT_TARGET_FILE}은 둘 다 나온다
 *   <li>{@code chunkIndex}는 1 이상이다
 * </ul>
 *
 * <p>맞추지 않는 것: {@code demotion}과 basis, {@code stats}의 모드와 Chunk 수, {@code line}과 실제 변경 구간. 경로
 * 필드({@code file}, {@code basis.ref}, {@code ExcludedFile.path}, {@code ChunkIssue.files})는 드물게
 * {@code ""}이고, {@code excludedFiles}에 같은 경로가 두 번 나올 수 있다.
 */
public final class ReviewResultArbitraries {

  private ReviewResultArbitraries() {}

  private record LineJudgement(
      Integer line, LineVerdict verdict, SummaryOnlyReason summaryOnlyReason) {}

  private record Completeness(
      ResultStatus status, List<IncompleteReason> reasons, IncompleteDetails details) {}

  public static Arbitrary<ReviewResult> reviewResults() {
    return Combinators.combine(
            completeness(),
            texts(),
            findings().list().ofMinSize(0).ofMaxSize(4),
            excludedFiles().list().ofMinSize(0).ofMaxSize(3),
            usages(),
            stats())
        .as(
            (completeness, summary, findings, excluded, usage, stats) ->
                new ReviewResult(
                    completeness.status(),
                    summary,
                    findings,
                    excluded.stream().map(ExcludedFile::path).toList(),
                    excluded,
                    usage,
                    completeness.reasons(),
                    completeness.details(),
                    stats));
  }

  public static Arbitrary<Finding> findings() {
    return Combinators.combine(
            RepoPathArbitraries.normalized(),
            Arbitraries.of(Severity.class),
            Arbitraries.of(Category.class),
            texts(),
            texts().injectNull(0.3),
            bases(),
            lineJudgements())
        .as(
            (file, severity, category, message, suggestion, basis, judgement) ->
                new Finding(
                    file,
                    judgement.line(),
                    severity,
                    category,
                    message,
                    suggestion,
                    basis.get1(),
                    basis.get2(),
                    judgement.verdict(),
                    judgement.summaryOnlyReason()));
  }

  /** 한글, 따옴표, 역슬래시, 줄바꿈, 탭, 이모지(보충 문자)가 섞인 문자열. 빈 문자열도 낸다. */
  public static Arbitrary<String> texts() {
    Arbitrary<String> special =
        Arbitraries.of(
            "한글 문장", "리뷰", "\"", "'", "\\", "\n", "\r\n", "\t", "😀", "👍🏽", " ", "é", "</tag>",
            "null", "{}", "`code`");
    Arbitrary<String> plain = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(12);
    return Arbitraries.frequencyOf(Tuple.of(3, special), Tuple.of(1, plain))
        .list()
        .ofMinSize(0)
        .ofMaxSize(6)
        .map(fragments -> String.join("", fragments));
  }

  /** basis와 Demotion_Record. Demotion_Record는 대부분 null이다. */
  private static Arbitrary<Tuple.Tuple2<Basis, Demotion>> bases() {
    Arbitrary<Basis> basis =
        Arbitraries.of(BasisType.class)
            .flatMap(
                type ->
                    type == BasisType.GENERAL
                        ? Arbitraries.just(new Basis(type, null))
                        : RepoPathArbitraries.normalized().map(ref -> new Basis(type, ref)));
    Arbitrary<Demotion> demotion =
        Combinators.combine(
                Arbitraries.of(BasisType.class), RepoPathArbitraries.normalized().injectNull(0.3))
            .as(Demotion::new)
            .injectNull(0.7);
    return Combinators.combine(basis, demotion).as(Tuple::of);
  }

  private static Arbitrary<LineJudgement> lineJudgements() {
    Arbitrary<Integer> line = Arbitraries.integers().between(1, 5_000);
    Arbitrary<LineJudgement> inline =
        line.map(number -> new LineJudgement(number, LineVerdict.INLINE_ELIGIBLE, null));
    Arbitrary<LineJudgement> summaryOnly =
        Arbitraries.of(SummaryOnlyReason.class)
            .flatMap(
                reason -> {
                  Arbitrary<Integer> lineForReason =
                      switch (reason) {
                        case LINE_MISSING -> Arbitraries.just(null);
                        case OUT_OF_RANGE -> line;
                        case NOT_TARGET_FILE -> line.injectNull(0.3);
                      };
                  return lineForReason.map(
                      number -> new LineJudgement(number, LineVerdict.SUMMARY_ONLY, reason));
                });
    return Arbitraries.oneOf(inline, summaryOnly);
  }

  private static Arbitrary<ExcludedFile> excludedFiles() {
    Arbitrary<String> reason =
        Arbitraries.of("binary_or_too_large", "unparseable_patch", "*.lock", "**/generated/**");
    return Combinators.combine(RepoPathArbitraries.normalized(), reason).as(ExcludedFile::new);
  }

  private static Arbitrary<Completeness> completeness() {
    Arbitrary<Completeness> complete =
        Arbitraries.just(
            new Completeness(
                ResultStatus.COMPLETE, List.of(), new IncompleteDetails(List.of(), null)));
    Arbitrary<Completeness> incomplete =
        Arbitraries.of(IncompleteReason.class)
            .list()
            .uniqueElements()
            .ofMinSize(1)
            .ofMaxSize(3)
            .flatMap(
                reasons ->
                    incompleteDetails(reasons)
                        .map(
                            details ->
                                new Completeness(ResultStatus.INCOMPLETE, reasons, details)));
    return Arbitraries.oneOf(complete, incomplete);
  }

  /** Chunk 문제는 {@code FILES_TRUNCATED}가 아닌 사유로만 만든다. */
  private static Arbitrary<IncompleteDetails> incompleteDetails(List<IncompleteReason> reasons) {
    List<IncompleteReason> chunkReasons =
        reasons.stream().filter(reason -> reason != IncompleteReason.FILES_TRUNCATED).toList();
    Arbitrary<List<ChunkIssue>> chunks =
        chunkReasons.isEmpty()
            ? Arbitraries.just(List.of())
            : Arbitraries.of(chunkReasons)
                .flatMap(ReviewResultArbitraries::chunkIssues)
                .list()
                .ofMinSize(1)
                .ofMaxSize(3);
    Arbitrary<FileCountGap> gap =
        reasons.contains(IncompleteReason.FILES_TRUNCATED)
            ? Combinators.combine(
                    Arbitraries.integers().between(1, 5_000),
                    Arbitraries.integers().between(1, 3_000))
                .as((received, missing) -> new FileCountGap(received + missing, received))
            : Arbitraries.just(null);
    return Combinators.combine(chunks, gap).as(IncompleteDetails::new);
  }

  private static Arbitrary<ChunkIssue> chunkIssues(IncompleteReason reason) {
    Arbitrary<String> excerpt =
        reason == IncompleteReason.SCHEMA_VIOLATION ? texts() : Arbitraries.just(null);
    return Combinators.combine(
            Arbitraries.integers().between(1, 10),
            RepoPathArbitraries.normalized().list().ofMinSize(0).ofMaxSize(3),
            Arbitraries.of(400, 429, 500, 529).injectNull(0.5),
            excerpt)
        .as(
            (chunkIndex, files, lastStatusCode, rawResponseExcerpt) ->
                new ChunkIssue(chunkIndex, files, reason, lastStatusCode, rawResponseExcerpt));
  }

  private static Arbitrary<Usage> usages() {
    // int로 읽는 코덱과 double을 거치는 코덱이 드러나도록 int 범위를 넘는 토큰 수와 유효 숫자 17자리 이상의 비용을 섞는다
    Arbitrary<Long> tokens =
        Arbitraries.frequencyOf(
            Tuple.of(3, Arbitraries.longs().between(0, 50_000_000)),
            Tuple.of(1, Arbitraries.longs().between(Integer.MAX_VALUE + 1L, Long.MAX_VALUE)));
    Arbitrary<BigDecimal> cost =
        Arbitraries.frequencyOf(
                Tuple.of(3, Arbitraries.longs().between(0, 5_000_000)),
                Tuple.of(
                    1,
                    Arbitraries.longs()
                        .between(100_000_000_000_000_000L, 999_999_999_999_999_999L)))
            .map(units -> BigDecimal.valueOf(units, 4))
            .injectNull(0.3);
    return Combinators.combine(
            tokens, tokens, tokens, tokens, Arbitraries.of("claude-opus-5-5", "모델 #1"), cost)
        .as(Usage::new);
  }

  private static Arbitrary<ReviewStats> stats() {
    return Combinators.combine(
            Arbitraries.of(ReviewMode.class),
            Arbitraries.integers().between(0, 20_000),
            Arbitraries.integers().between(1, 10_000),
            Arbitraries.integers().between(0, 12))
        .as(ReviewStats::new);
  }
}
