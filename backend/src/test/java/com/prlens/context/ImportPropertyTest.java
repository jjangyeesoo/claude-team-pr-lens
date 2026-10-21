package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.RepoRef;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import com.prlens.testkit.ImportGraphArbitraries;
import com.prlens.testkit.ImportGraphArbitraries.ImportGraph;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** import 해석의 속성 테스트 (design.md Property 15, Property 16). */
class ImportPropertyTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");
  private static final String BASE = "base-sha";

  // Feature: pr-lens-p1-cli, Property 15: import 도달 가능성 불변
  @Property(tries = 200)
  void importReachabilityInvariant(@ForAll("graphs") ImportGraph graph) {
    FakeRepoClient github = new FakeRepoClient();
    List<Entry> entries = new ArrayList<>();
    graph.files().forEach((path, content) -> github.file(BASE, path, content));
    graph.files().keySet().forEach(path -> entries.add(new Entry(path, Kind.BLOB)));
    for (String folder : graph.folders()) {
      github.directory(BASE, folder);
      entries.add(new Entry(folder, Kind.TREE));
    }
    RepoTree tree = new RepoTree(graph.truncated() ? List.of() : entries, graph.truncated());
    List<Warning> warnings = new ArrayList<>();
    BaseFileReader reader =
        new BaseFileReader(github, REPO, BASE, RepoTreeIndex.of(tree), warnings::add);
    List<ContextFile> collected = new ArrayList<>();
    for (String path : graph.claudeMd()) {
      collected.add(
          new ContextFile(path, graph.files().get(path), ContextSource.CLAUDE_MD, Revision.BASE));
    }
    for (String path : graph.rules()) {
      collected.add(
          new ContextFile(path, graph.files().get(path), ContextSource.RULE, Revision.BASE));
    }

    List<ContextFile> result = new ImportResolver(reader, warnings::add).resolve(collected);

    Set<String> collectedPaths = new HashSet<>();
    collected.forEach(file -> collectedPaths.add(file.path()));
    Set<String> resultPaths = new HashSet<>();
    for (ContextFile file : result) {
      assertThat(file.path()).doesNotStartWith("/");
      for (String segment : file.path().split("/", -1)) {
        assertThat(segment).isNotEmpty().isNotEqualTo(".").isNotEqualTo("..");
      }
      assertThat(graph.files()).containsKey(file.path());
      assertThat(file.content()).isEqualTo(graph.files().get(file.path()));
      assertThat(file.source()).isEqualTo(ContextSource.IMPORT);
      assertThat(file.revision()).isEqualTo(Revision.BASE);
      assertThat(resultPaths.add(file.path())).as("중복 " + file.path()).isTrue();
    }
    assertThat(resultPaths).doesNotContainAnyElementsOf(collectedPaths);
    assertThat(resultPaths).isEqualTo(expectedReachable(graph, collectedPaths));

    List<String> requested = github.fileRequests().stream().map(FileRequest::path).toList();
    assertThat(requested).doesNotHaveDuplicates();
  }

  /** CLAUDE.md를 깊이 0으로 두고, 이미 모은 파일은 거치지 않는 그래프에서 최단 거리 1~4인 기존 파일. */
  private static Set<String> expectedReachable(ImportGraph graph, Set<String> collectedPaths) {
    Map<String, Integer> distance = new HashMap<>();
    Deque<String> queue = new ArrayDeque<>();
    for (String start : graph.claudeMd()) {
      distance.put(start, 0);
      queue.add(start);
    }
    Set<String> reachable = new HashSet<>();
    while (!queue.isEmpty()) {
      String node = queue.poll();
      int next = distance.get(node) + 1;
      if (next > 4) {
        continue;
      }
      for (String target : graph.edges().getOrDefault(node, List.of())) {
        if (!graph.files().containsKey(target)
            || collectedPaths.contains(target)
            || distance.containsKey(target)) {
          continue;
        }
        distance.put(target, next);
        reachable.add(target);
        queue.add(target);
      }
    }
    return reachable;
  }

  @Provide
  Arbitrary<ImportGraph> graphs() {
    return ImportGraphArbitraries.graphs();
  }

  /**
   * 원문과, 토큰 하나를 인식되지 않게 바꾼 변형.
   *
   * @param dropped 변형에서 빠져야 하는 토큰 번호
   */
  record Scenario(String original, String variant, List<String> tokens, int dropped) {}

  // Feature: pr-lens-p1-cli, Property 16: import 인식 메타모픽
  @Property(tries = 200)
  void importRecognitionMetamorphic(@ForAll("scenarios") Scenario scenario) {
    assertThat(ImportResolver.scan(scenario.original())).isEqualTo(scenario.tokens());

    List<String> expected = new ArrayList<>(scenario.tokens());
    expected.remove(scenario.dropped());
    assertThat(ImportResolver.scan(scenario.variant())).isEqualTo(expected);
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    return Arbitraries.randoms().map(ImportPropertyTest::scenario);
  }

  private static final char[] PATH_CHARS = "abcXYZ019_-./".toCharArray();

  private static String word(Random random) {
    int length = 1 + random.nextInt(5);
    StringBuilder word = new StringBuilder();
    for (int i = 0; i < length; i++) {
      word.append((char) ('a' + random.nextInt(26)));
    }
    return word.toString();
  }

  private static String path(Random random) {
    int length = 1 + random.nextInt(10);
    StringBuilder path = new StringBuilder();
    for (int i = 0; i < length; i++) {
      path.append(PATH_CHARS[random.nextInt(PATH_CHARS.length)]);
    }
    return path.toString();
  }

  private static Scenario scenario(Random random) {
    int tokenCount = 1 + random.nextInt(5);
    List<String> tokens = new ArrayList<>();
    List<String> prefixes = new ArrayList<>();
    List<String> suffixes = new ArrayList<>();
    for (int i = 0; i < tokenCount; i++) {
      tokens.add(path(random));
      prefixes.add(random.nextBoolean() ? "" : word(random) + " ");
      suffixes.add(random.nextBoolean() ? "" : " " + word(random));
    }
    int variantKind = random.nextInt(4);
    int dropped = variantKind == 3 ? tokenCount - 1 : random.nextInt(tokenCount);

    List<String> original = new ArrayList<>();
    List<String> variant = new ArrayList<>();
    int fillersBefore = random.nextInt(3);
    for (int i = 0; i < fillersBefore; i++) {
      String filler = word(random) + " " + word(random);
      original.add(filler);
      variant.add(filler);
    }
    for (int i = 0; i < tokenCount; i++) {
      String line = prefixes.get(i) + "@" + tokens.get(i) + suffixes.get(i);
      original.add(line);
      if (i != dropped) {
        variant.add(line);
        continue;
      }
      switch (variantKind) {
        case 0 -> {
          String attached = String.valueOf("x(.".charAt(random.nextInt(3)));
          variant.add(prefixes.get(i) + attached + "@" + tokens.get(i) + suffixes.get(i));
        }
        case 1 -> {
          String ticks = "`".repeat(1 + random.nextInt(2));
          variant.add(prefixes.get(i) + ticks + "@" + tokens.get(i) + ticks + suffixes.get(i));
        }
        case 2 -> {
          variant.add("```");
          variant.add(line);
          variant.add("```");
        }
        default -> {
          variant.add("```");
          variant.add(line);
        }
      }
      if (i + 1 < tokenCount || random.nextBoolean()) {
        String filler = word(random);
        original.add(filler);
        variant.add(filler);
      }
    }
    return new Scenario(
        String.join("\n", original), String.join("\n", variant), List.copyOf(tokens), dropped);
  }
}
