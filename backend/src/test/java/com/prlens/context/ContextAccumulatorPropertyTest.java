package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.RepoPaths;
import com.prlens.model.Revision;
import com.prlens.testkit.RepoPathArbitraries;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

class ContextAccumulatorPropertyTest {

  /** 풀의 기준 경로와, 그 경로를 바꿔 쓴 추가 목록. */
  record Scenario(List<String> pool, List<Added> adds) {}

  record Added(String base, ContextFile file) {}

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<List<String>> pool =
        RepoPathArbitraries.normalized().filter(p -> !p.isEmpty()).list().ofMinSize(1).ofMaxSize(4);
    return pool.flatMap(
        paths -> {
          Arbitrary<Added> added =
              Combinators.combine(
                      Arbitraries.of(paths),
                      Arbitraries.of("./", "/", ""),
                      Arbitraries.of(true, false),
                      Arbitraries.of(ContextSource.values()),
                      Arbitraries.of(Revision.values()),
                      Arbitraries.strings().ofMaxLength(5))
                  .as(
                      (base, prefix, backslash, source, revision, content) -> {
                        String variant = prefix + base;
                        if (backslash) {
                          variant = variant.replace('/', '\\');
                        }
                        return new Added(base, new ContextFile(variant, content, source, revision));
                      });
          return added.list().ofMaxSize(30).map(adds -> new Scenario(paths, adds));
        });
  }

  // Feature: pr-lens-p1-cli, Property 18: 컨텍스트 경로 유일성과 출처 우선순위
  @Property(tries = 200)
  void contextPathUniquenessAndSourcePriority(@ForAll("scenarios") Scenario scenario) {
    ContextAccumulator acc = new ContextAccumulator();
    Map<String, Integer> best = new HashMap<>();
    for (Added added : scenario.adds()) {
      acc.add(added.file());
      best.merge(added.base(), added.file().source().ordinal(), Math::min);
    }

    List<ContextFile> files = acc.toContext().files();
    List<String> paths = new ArrayList<>();
    for (ContextFile file : files) {
      paths.add(file.path());
      assertThat(RepoPaths.normalize(file.path())).isEqualTo(file.path());
      assertThat(file.source().ordinal()).isEqualTo(best.get(file.path()));
    }
    assertThat(new HashSet<>(paths)).hasSameSizeAs(paths);
    Set<String> expected = new HashSet<>(best.keySet());
    assertThat(new HashSet<>(paths)).isEqualTo(expected);
  }
}
