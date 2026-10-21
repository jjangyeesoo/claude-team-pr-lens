package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.diff.LineRanges;
import com.prlens.model.Basis;
import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.ContextFile;
import com.prlens.model.Demotion;
import com.prlens.model.Finding;
import com.prlens.model.Hunk;
import com.prlens.model.LineVerdict;
import com.prlens.model.RepoPaths;
import com.prlens.model.ReviewContext;
import com.prlens.model.Severity;
import com.prlens.model.SummaryOnlyReason;
import com.prlens.testkit.DiffArbitraries;
import com.prlens.testkit.RepoPathArbitraries;
import com.prlens.testkit.ReviewContextArbitraries;
import com.prlens.testkit.ReviewResultArbitraries;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

// 작업 10.5: Property 23(basis 검증 불변), 24(멱등), 25(필드 보존), 26(라인 판정)
class FindingValidatorPropertyTest {

  private record Scenario(
      ReviewContext context, Map<String, List<Hunk>> hunks, List<RawFinding> findings) {

    Map<String, LineRanges> ranges() {
      Map<String, LineRanges> ranges = new LinkedHashMap<>();
      hunks.forEach((path, list) -> ranges.put(path, LineRanges.of(list)));
      return ranges;
    }

    Set<String> contextPaths() {
      return context.files().stream().map(ContextFile::path).collect(Collectors.toSet());
    }

    List<Finding> validate() {
      return FindingValidator.apply(findings, context, ranges());
    }
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<Map<String, List<Hunk>>> targets =
        Combinators.combine(
                RepoPathArbitraries.normalized().filter(path -> !path.isEmpty()),
                DiffArbitraries.validHunks())
            .as(Tuple::of)
            .list()
            .uniqueElements(Tuple.Tuple2::get1)
            .ofMaxSize(3)
            .map(
                pairs -> {
                  Map<String, List<Hunk>> map = new LinkedHashMap<>();
                  pairs.forEach(pair -> map.put(pair.get1(), pair.get2()));
                  return map;
                });
    return Combinators.combine(ReviewContextArbitraries.contexts(), targets)
        .as(Tuple::of)
        .flatMap(
            pair ->
                findings(pair.get1(), pair.get2())
                    .list()
                    .ofMaxSize(8)
                    .map(list -> new Scenario(pair.get1(), pair.get2(), list)));
  }

  private static Arbitrary<RawFinding> findings(
      ReviewContext context, Map<String, List<Hunk>> targets) {
    List<String> contextPaths = context.files().stream().map(ContextFile::path).toList();
    Arbitrary<String> targetFiles =
        targets.isEmpty() ? Arbitraries.just("x") : Arbitraries.of(targets.keySet());
    Arbitrary<String> files =
        Arbitraries.frequencyOf(
            Tuple.of(3, targetFiles), Tuple.of(1, RepoPathArbitraries.normalized()));
    Arbitrary<Integer> lines =
        Arbitraries.frequencyOf(
            Tuple.of(1, Arbitraries.just((Integer) null)),
            Tuple.of(6, Arbitraries.integers().between(0, 400)));
    // 컨텍스트 경로를 ./, /, \ 로 바꿔 쓴 ref (정규화하면 컨텍스트 경로와 같아진다)
    Arbitrary<String> pathRefs =
        contextPaths.isEmpty()
            ? Arbitraries.just("none")
            : Combinators.combine(
                    Arbitraries.of(contextPaths),
                    Arbitraries.of("", "./", "/"),
                    Arbitraries.of(true, false))
                .as(
                    (path, prefix, backslash) ->
                        prefix + (backslash ? path.replace('/', '\\') : path));
    Arbitrary<String> refs =
        Arbitraries.frequencyOf(
            Tuple.of(3, pathRefs),
            Tuple.of(2, RepoPathArbitraries.normalized()),
            Tuple.of(1, Arbitraries.of((String) null, "", " ", "\t")));
    return Combinators.combine(
            files,
            lines,
            Arbitraries.of(Severity.class),
            Arbitraries.of(Category.class),
            ReviewResultArbitraries.texts(),
            Arbitraries.of(BasisType.class),
            refs)
        .as(
            (file, line, severity, category, message, type, ref) ->
                new RawFinding(file, line, severity, category, message, "s", type, ref));
  }

  // Feature: pr-lens-p1-cli, Property 23: basis 검증 불변
  @Property(tries = 200)
  void basisAfterValidationIsInContextOrDemoted(@ForAll("scenarios") Scenario scenario) {
    Set<String> paths = scenario.contextPaths();
    List<Finding> out = scenario.validate();

    for (int i = 0; i < out.size(); i++) {
      RawFinding raw = scenario.findings().get(i);
      Finding finding = out.get(i);
      if (finding.basis().type() != BasisType.GENERAL) {
        assertThat(paths).contains(RepoPaths.normalize(finding.basis().ref()));
        assertThat(finding.demotion()).isNull();
        assertThat(finding.basis()).isEqualTo(new Basis(raw.basisType(), raw.basisRef()));
      } else if (raw.basisType() == BasisType.GENERAL) {
        assertThat(finding.basis()).isEqualTo(new Basis(BasisType.GENERAL, raw.basisRef()));
        assertThat(finding.demotion()).isNull();
      } else {
        assertThat(finding.demotion()).isEqualTo(new Demotion(raw.basisType(), raw.basisRef()));
        assertThat(finding.basis().ref()).isNull();
      }
    }
  }

  // Feature: pr-lens-p1-cli, Property 24: FindingValidator 멱등
  @Property(tries = 200)
  void applyingTwiceChangesNothingAndAddsNoDemotion(@ForAll("scenarios") Scenario scenario) {
    List<Finding> first = scenario.validate();
    List<RawFinding> again = new ArrayList<>();
    for (Finding f : first) {
      again.add(
          new RawFinding(
              f.file(),
              f.line(),
              f.severity(),
              f.category(),
              f.message(),
              f.suggestion(),
              f.basis().type(),
              f.basis().ref()));
    }

    List<Finding> second = FindingValidator.apply(again, scenario.context(), scenario.ranges());

    assertThat(second).hasSameSizeAs(first);
    for (int i = 0; i < first.size(); i++) {
      assertThat(second.get(i).basis()).isEqualTo(first.get(i).basis());
      assertThat(second.get(i).verdict()).isEqualTo(first.get(i).verdict());
      assertThat(second.get(i).summaryOnlyReason()).isEqualTo(first.get(i).summaryOnlyReason());
      assertThat(second.get(i).demotion()).isNull();
    }
  }

  // Feature: pr-lens-p1-cli, Property 25: FindingValidator 필드 보존
  @Property(tries = 200)
  void countOrderAndOtherFieldsArePreserved(@ForAll("scenarios") Scenario scenario) {
    List<Finding> out = scenario.validate();

    assertThat(out).hasSameSizeAs(scenario.findings());
    for (int i = 0; i < out.size(); i++) {
      RawFinding raw = scenario.findings().get(i);
      Finding f = out.get(i);
      assertThat(f.file()).isEqualTo(raw.file());
      assertThat(f.line()).isEqualTo(raw.line());
      assertThat(f.severity()).isEqualTo(raw.severity());
      assertThat(f.category()).isEqualTo(raw.category());
      assertThat(f.message()).isEqualTo(raw.message());
      assertThat(f.suggestion()).isEqualTo(raw.suggestion());
    }
  }

  // Feature: pr-lens-p1-cli, Property 26: 라인 판정
  @Property(tries = 300)
  void verdictFollowsHeadRangesAndReasonOrder(@ForAll("scenarios") Scenario scenario) {
    List<Finding> out = scenario.validate();

    for (int i = 0; i < out.size(); i++) {
      RawFinding raw = scenario.findings().get(i);
      Finding f = out.get(i);
      List<Hunk> hunks = scenario.hunks().get(raw.file());
      Set<Integer> headLines = new HashSet<>();
      if (hunks != null) {
        for (Hunk h : hunks) {
          for (int n = 0; n < h.headCount(); n++) {
            headLines.add(h.headStart() + n);
          }
        }
      }
      if (hunks == null) {
        assertThat(f.summaryOnlyReason()).isEqualTo(SummaryOnlyReason.NOT_TARGET_FILE);
      } else if (raw.line() == null) {
        assertThat(f.summaryOnlyReason()).isEqualTo(SummaryOnlyReason.LINE_MISSING);
      } else if (!headLines.contains(raw.line())) {
        assertThat(f.summaryOnlyReason()).isEqualTo(SummaryOnlyReason.OUT_OF_RANGE);
      } else {
        assertThat(f.verdict()).isEqualTo(LineVerdict.INLINE_ELIGIBLE);
        assertThat(f.summaryOnlyReason()).isNull();
      }
      assertThat(f.verdict() == LineVerdict.SUMMARY_ONLY).isEqualTo(f.summaryOnlyReason() != null);
    }
  }
}
