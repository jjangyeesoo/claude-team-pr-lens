package com.prlens.context;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.MarkedYAMLException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * rule 파일의 YAML 프런트매터에서 {@code paths}를 읽는다(요구사항 5.2, 5.3, 5.9, 5.10, 22.4).
 *
 * <p>줄바꿈을 {@code \n}으로 바꾼 뒤 첫 줄이 정확히 {@code ---}일 때만 다음 {@code ---} 줄까지를 프런트매터로 본다(앞뒤 공백, BOM 허용 안
 * 함). YAML은 노드만 만들고 객체를 생성하지 않는다. 문제가 있으면 {@code paths}는 비운다(호출자는 rule을 포함한다).
 */
final class FrontmatterParser {

  private static final String DELIMITER = "---";

  enum Kind {
    YAML_ERROR,
    UNCLOSED,
    INVALID_PATHS
  }

  /**
   * @param detail 한 줄 설명
   */
  record Problem(Kind kind, String detail) {}

  /**
   * @param paths {@code paths} 패턴. 프런트매터나 {@code paths}가 없거나 문제가 있으면 비어 있다
   */
  record Frontmatter(Optional<List<String>> paths, Optional<Problem> problem) {
    static final Frontmatter NONE = new Frontmatter(Optional.empty(), Optional.empty());

    static Frontmatter of(List<String> paths) {
      return new Frontmatter(Optional.of(List.copyOf(paths)), Optional.empty());
    }

    static Frontmatter problem(Kind kind, String detail) {
      return new Frontmatter(Optional.empty(), Optional.of(new Problem(kind, detail)));
    }
  }

  private FrontmatterParser() {}

  static Frontmatter parse(String content) {
    String[] lines = content.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
    if (!lines[0].equals(DELIMITER)) {
      return Frontmatter.NONE;
    }
    int close = -1;
    for (int i = 1; i < lines.length; i++) {
      if (lines[i].equals(DELIMITER)) {
        close = i;
        break;
      }
    }
    if (close < 0) {
      return Frontmatter.problem(Kind.UNCLOSED, "프런트매터를 닫는 --- 줄이 없습니다");
    }
    StringBuilder yaml = new StringBuilder();
    for (int i = 1; i < close; i++) {
      yaml.append(lines[i]).append('\n');
    }
    Node root;
    try {
      root = new Yaml(new LoaderOptions()).compose(new StringReader(yaml.toString()));
    } catch (MarkedYAMLException e) {
      Mark mark = e.getProblemMark();
      // 여는 --- 줄을 더해 파일 기준 줄 번호로 알린다.
      String where =
          mark == null ? "" : " (줄 " + (mark.getLine() + 2) + ", 열 " + (mark.getColumn() + 1) + ")";
      return Frontmatter.problem(Kind.YAML_ERROR, oneLine("문법 오류" + where + ": " + e.getProblem()));
    } catch (YAMLException e) {
      return Frontmatter.problem(Kind.YAML_ERROR, "YAML을 읽을 수 없습니다");
    }
    if (root == null || isNull(root)) {
      return Frontmatter.NONE;
    }
    if (!(root instanceof MappingNode mapping)) {
      return Frontmatter.problem(Kind.YAML_ERROR, "프런트매터가 매핑이 아닙니다");
    }
    Node paths = null;
    for (NodeTuple tuple : mapping.getValue()) {
      if (tuple.getKeyNode() instanceof ScalarNode key && key.getValue().equals("paths")) {
        paths = tuple.getValueNode();
      }
    }
    if (paths == null) {
      return Frontmatter.NONE;
    }
    return readPaths(paths);
  }

  /** 문자열 하나 또는 문자열만으로 된 비지 않은 목록만 받는다. */
  private static Frontmatter readPaths(Node node) {
    if (isString(node)) {
      return Frontmatter.of(List.of(((ScalarNode) node).getValue()));
    }
    if (node instanceof SequenceNode sequence && !sequence.getValue().isEmpty()) {
      List<String> patterns = new ArrayList<>();
      for (Node item : sequence.getValue()) {
        if (!isString(item)) {
          return invalidPaths();
        }
        patterns.add(((ScalarNode) item).getValue());
      }
      return Frontmatter.of(patterns);
    }
    return invalidPaths();
  }

  private static Frontmatter invalidPaths() {
    return Frontmatter.problem(Kind.INVALID_PATHS, "paths는 문자열이나 비지 않은 문자열 목록이어야 합니다");
  }

  private static boolean isString(Node node) {
    return node instanceof ScalarNode && node.getTag().equals(Tag.STR);
  }

  private static boolean isNull(Node node) {
    return node instanceof ScalarNode && node.getTag().equals(Tag.NULL);
  }

  private static String oneLine(String text) {
    return text.replaceAll("\\s*[\\r\\n]+\\s*", " ").strip();
  }
}
