package com.prlens.testkit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;

/** import 그래프 생성기 (design.md "속성 기반 테스트 규칙"의 "import 그래프"). */
public final class ImportGraphArbitraries {

  private static final List<String> FILE_POOL =
      List.of(
          "CLAUDE.md",
          ".claude/CLAUDE.md",
          "docs/CLAUDE.md",
          "a.md",
          "docs/b.md",
          "docs/x/c.md",
          "docs/x/d.md",
          "docs/y/e.md",
          "f.md",
          ".claude/rules/r.md");
  private static final List<String> CLAUDE_MD_POOL =
      List.of("CLAUDE.md", ".claude/CLAUDE.md", "docs/CLAUDE.md");
  private static final List<String> RULE_POOL = List.of(".claude/rules/r.md");
  private static final List<String> FOLDER_POOL =
      List.of("docs", "docs/x", "docs/y", ".claude", ".claude/rules");
  private static final List<String> MISSING_POOL =
      List.of("none.md", "docs/none.md", "docs/y/n.md");
  private static final List<String> OUTSIDE_POOL =
      List.of("../../../x.md", "/x.md", "~/x.md", "C:/x.md", "\\x.md", "c:x.md");

  private ImportGraphArbitraries() {}

  /**
   * 생성한 저장소와 import 선언.
   *
   * @param files 저장소에 있는 파일의 경로와 내용(내용은 선언을 한 줄씩 쓴 것)
   * @param folders 저장소에 있는 폴더
   * @param claudeMd 이미 모은 CLAUDE.md 경로(출처 CLAUDE_MD, {@code files}의 부분집합)
   * @param rules 이미 모은 rule 경로(출처 RULE, {@code files}의 부분집합)
   * @param truncated 트리가 잘렸는지
   * @param edges 선언 파일 경로 → 그 파일 선언이 가리키는, 저장소 안의 정규화된 대상 경로(파일, 폴더, 없는 경로). 루트 밖 선언은 없다. 모델 계산용이며
   *     {@code ImportResolver}와 별개로 생성 시점에 정해진다
   */
  public record ImportGraph(
      Map<String, String> files,
      Set<String> folders,
      List<String> claudeMd,
      List<String> rules,
      boolean truncated,
      Map<String, List<String>> edges) {}

  public static Arbitrary<ImportGraph> graphs() {
    return Arbitraries.randoms().map(ImportGraphArbitraries::build);
  }

  private static ImportGraph build(Random random) {
    List<String> existing = new ArrayList<>();
    for (String path : FILE_POOL) {
      if (random.nextInt(4) != 0) {
        existing.add(path);
      }
    }
    List<String> claudeMd = new ArrayList<>();
    for (String path : CLAUDE_MD_POOL) {
      if (existing.contains(path)) {
        claudeMd.add(path);
      }
    }
    List<String> rules = new ArrayList<>();
    for (String path : RULE_POOL) {
      if (existing.contains(path)) {
        rules.add(path);
      }
    }
    List<String> chainOrder = new ArrayList<>(FILE_POOL);
    Collections.shuffle(chainOrder, random);

    Map<String, String> files = new LinkedHashMap<>();
    Map<String, List<String>> edges = new LinkedHashMap<>();
    for (String declarer : existing) {
      List<String> lines = new ArrayList<>();
      List<String> targets = new ArrayList<>();
      int count = random.nextInt(5);
      for (int i = 0; i < count; i++) {
        String target = pickTarget(random, declarer, chainOrder, i == 0);
        if (target == null) {
          String raw = OUTSIDE_POOL.get(random.nextInt(OUTSIDE_POOL.size()));
          lines.add("@" + raw);
        } else {
          lines.add("@" + spell(random, declarer, target));
          targets.add(target);
        }
      }
      files.put(declarer, String.join("\n", lines) + (lines.isEmpty() ? "내용" : "\n"));
      edges.put(declarer, targets);
    }
    return new ImportGraph(
        files, new LinkedHashSet<>(FOLDER_POOL), claudeMd, rules, random.nextInt(3) == 0, edges);
  }

  /** 저장소 안의 대상 경로. {@code null}이면 루트 밖 선언이다. */
  private static String pickTarget(
      Random random, String declarer, List<String> chainOrder, boolean first) {
    if (first && random.nextInt(5) < 2) {
      int next = (chainOrder.indexOf(declarer) + 1) % chainOrder.size();
      return chainOrder.get(next);
    }
    int roll = random.nextInt(20);
    if (roll < 11) {
      return FILE_POOL.get(random.nextInt(FILE_POOL.size()));
    }
    if (roll < 13) {
      return FOLDER_POOL.get(random.nextInt(FOLDER_POOL.size()));
    }
    if (roll < 15) {
      return MISSING_POOL.get(random.nextInt(MISSING_POOL.size()));
    }
    return null;
  }

  /** 선언 파일 폴더 기준 상대 경로로 쓰고, 표기를 바꾼다. 해석기와 무관하게 구간을 직접 비교해 만든다. */
  private static String spell(Random random, String declarer, String target) {
    String[] folder = parent(declarer);
    String[] segments = target.split("/");
    int common = 0;
    while (common < folder.length
        && common < segments.length - 1
        && folder[common].equals(segments[common])) {
      common++;
    }
    StringBuilder base = new StringBuilder();
    for (int i = common; i < folder.length; i++) {
      base.append("../");
    }
    base.append(String.join("/", List.of(segments).subList(common, segments.length)));
    String plain = base.toString();
    return switch (random.nextInt(5)) {
      case 0 -> "./" + plain;
      case 1 -> plain.replaceFirst("/", "//");
      case 2 -> plain.replace('/', '\\');
      case 3 -> "zz/../" + plain;
      default -> plain;
    };
  }

  private static String[] parent(String path) {
    int slash = path.lastIndexOf('/');
    return slash < 0 ? new String[0] : path.substring(0, slash).split("/");
  }
}
