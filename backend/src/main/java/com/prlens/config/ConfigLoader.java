package com.prlens.config;

import com.prlens.glob.GlobMatcher;
import com.prlens.glob.GlobSyntaxException;
import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
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
 * 설정 파일을 Configuration으로 읽는다 (요구사항 18).
 *
 * <p>YAML을 노드 트리로 읽고 허용 키와 대조하며 문제를 모두 모아 한 번에 보고한다. 숫자는 원문 문자열에서 {@code BigDecimal}로 읽어 정밀도를 잃지
 * 않는다. 문자열 값은 오류 메시지에 싣지 않는다 (토큰을 엉뚱한 항목에 붙여 넣었을 때 새는 것을 막기 위해서다). 예외로 잘못된 제외 패턴은 원문을 함께 보여 준다.
 */
public final class ConfigLoader {

  static final String FILE_NAME = ".prlens.yml";

  private static final Set<String> EFFORTS = Set.of("low", "medium", "high", "xhigh", "max");
  private static final BigDecimal MIN_COST = new BigDecimal("0.01");
  private static final BigDecimal MAX_COST = new BigDecimal("100.00");
  private static final List<String> PRICE_FIELDS =
      List.of("input", "output", "cacheWrite", "cacheRead");

  private final Path workingDir;
  private final Path homeDir;

  public ConfigLoader(Path workingDir, Path homeDir) {
    this.workingDir = workingDir;
    this.homeDir = homeDir;
  }

  /**
   * @param explicit {@code --config} 경로. null이면 현재 작업 폴더, 사용자 홈 순서로 처음 발견한 파일 하나를 읽고 없으면 기본값을 쓴다.
   *     상대 경로는 현재 작업 폴더 기준이다
   */
  public Configuration load(Path explicit) {
    if (explicit != null) {
      Path file = workingDir.resolve(explicit);
      try {
        return parse(read(file));
      } catch (IOException e) {
        throw ConfigException.of(
            "--config", "설정 파일을 읽을 수 없습니다: " + explicit + " (" + cause(e, file) + ")");
      }
    }
    for (Path dir : List.of(workingDir, homeDir)) {
      Path file = dir.resolve(FILE_NAME);
      if (Files.isRegularFile(file)) {
        try {
          return parse(read(file));
        } catch (IOException e) {
          throw ConfigException.of(
              "file", "설정 파일을 읽을 수 없습니다: " + file + " (" + cause(e, file) + ")");
        }
      }
    }
    return Configuration.defaults();
  }

  /**
   * 읽지 못한 원인을 사용자가 알아볼 수 있는 말로 바꾼다 (요구사항 18.9). Windows의 예외 모양을 OS와 무관하게 시험하려고 package-private으로
   * 둔다.
   */
  static String cause(IOException e, Path file) {
    if (e instanceof NoSuchFileException) {
      return "파일이 없습니다";
    }
    // 폴더를 먼저 본다. Windows는 폴더를 읽으려 하면 AccessDeniedException을 던진다
    if (Files.isDirectory(file)) {
      return "파일이 아니라 폴더입니다";
    }
    if (e instanceof AccessDeniedException) {
      return "읽기 권한이 없습니다";
    }
    return e.getClass().getSimpleName();
  }

  private static String read(Path file) throws IOException {
    byte[] bytes = Files.readAllBytes(file);
    String text;
    try {
      text =
          StandardCharsets.UTF_8
              .newDecoder()
              .onMalformedInput(CodingErrorAction.REPORT)
              .onUnmappableCharacter(CodingErrorAction.REPORT)
              .decode(ByteBuffer.wrap(bytes))
              .toString();
    } catch (CharacterCodingException e) {
      throw new ConfigException(
          List.of(new ConfigException.Problem("encoding", "UTF-8이 아닙니다: " + file)));
    }
    return text.startsWith("﻿") ? text.substring(1) : text;
  }

  /** 문제가 있으면 모두 모아 {@link ConfigException}으로 던진다. */
  public Configuration parse(String yaml) {
    Node root = compose(yaml);
    if (root == null || isNull(root)) {
      return Configuration.defaults();
    }
    Reading reading = new Reading();
    return reading.configuration(root);
  }

  private static Node compose(String yaml) {
    LoaderOptions options = new LoaderOptions();
    try {
      return new Yaml(options).compose(new StringReader(yaml));
    } catch (MarkedYAMLException e) {
      Mark mark = e.getProblemMark();
      String where =
          mark == null ? "" : " (줄 " + (mark.getLine() + 1) + ", 열 " + (mark.getColumn() + 1) + ")";
      throw ConfigException.of("syntax", "문법 오류" + where + ": " + e.getProblem());
    } catch (YAMLException e) {
      throw ConfigException.of("syntax", "YAML을 읽을 수 없습니다");
    }
  }

  private static boolean isNull(Node node) {
    return node instanceof ScalarNode && node.getTag().equals(Tag.NULL);
  }

  /** 한 번 읽는 동안 문제를 모은다. */
  private static final class Reading {

    private final List<ConfigException.Problem> problems = new ArrayList<>();

    Configuration configuration(Node root) {
      Configuration defaults = Configuration.defaults();
      List<String> add = defaults.additionalExcludes();
      List<String> remove = defaults.removedExcludes();
      int sizeLimit = defaults.sizeLimit();
      String model = defaults.model();
      String effort = defaults.effort();
      int maxOutputTokens = defaults.maxOutputTokens();
      int maxRetries = defaults.maxRetries();
      BigDecimal maxCost = defaults.maxCostUsdPerReview();
      SortedMap<String, ModelPricing> pricing = new TreeMap<>(defaults.pricing());

      Map<String, Node> top =
          entries(root, "", Set.of("exclude", "review", "llm", "retry", "cost"), true);

      Map<String, Node> exclude =
          entries(top.get("exclude"), "exclude", Set.of("add", "remove"), true);
      add = patterns(exclude.get("add"), "exclude.add", add);
      remove = patterns(exclude.get("remove"), "exclude.remove", remove);

      Map<String, Node> review = entries(top.get("review"), "review", Set.of("sizeLimit"), true);
      sizeLimit = integer(review.get("sizeLimit"), "review.sizeLimit", 1, 10_000, sizeLimit);

      Map<String, Node> llm =
          entries(top.get("llm"), "llm", Set.of("model", "effort", "maxOutputTokens"), true);
      model = model(llm.get("model"), model);
      effort = effort(llm.get("effort"), effort);
      maxOutputTokens =
          integer(llm.get("maxOutputTokens"), "llm.maxOutputTokens", 1, 20_000, maxOutputTokens);

      Map<String, Node> retry = entries(top.get("retry"), "retry", Set.of("maxRetries"), true);
      maxRetries = integer(retry.get("maxRetries"), "retry.maxRetries", 0, 10, maxRetries);

      Map<String, Node> cost =
          entries(top.get("cost"), "cost", Set.of("maxUsdPerReview", "pricing"), true);
      maxCost = cost(cost.get("maxUsdPerReview"), maxCost);
      pricing(cost.get("pricing"), pricing);

      if (!problems.isEmpty()) {
        throw new ConfigException(problems);
      }
      return new Configuration(
          add, remove, sizeLimit, model, effort, maxOutputTokens, maxRetries, maxCost, pricing);
    }

    private void problem(String item, String message) {
      problems.add(new ConfigException.Problem(item, message));
    }

    private static String join(String path, String key) {
      return path.isEmpty() ? key : path + "." + key;
    }

    /**
     * 맵 노드의 항목을 키 이름으로 모은다. 허용하지 않는 키, 중복 키, 비밀 항목은 문제로 보고하고 담지 않는다. 노드가 없거나 비어 있으면(값이 없는 항목) 빈
     * 맵이다.
     *
     * @param restricted false이면 키를 가리지 않는다(모델 이름 같은 사용자 정의 키)
     */
    private Map<String, Node> entries(
        Node node, String path, Set<String> allowed, boolean restricted) {
      Map<String, Node> found = new LinkedHashMap<>();
      if (node == null || isNull(node)) {
        return found;
      }
      String where = path.isEmpty() ? "$" : path;
      if (!(node instanceof MappingNode mapping)) {
        problem(where, "항목 목록(맵)이어야 합니다");
        return found;
      }
      Set<String> seen = new HashSet<>();
      for (NodeTuple tuple : mapping.getValue()) {
        if (!(tuple.getKeyNode() instanceof ScalarNode key)) {
          problem(where, "항목 이름은 문자열이어야 합니다");
          continue;
        }
        String name = key.getValue();
        String item = join(path, name);
        if (!seen.add(name)) {
          problem(item, "같은 항목이 두 번 있습니다");
        } else if (restricted && secretVariable(name) != null) {
          problem(item, "비밀 값은 설정 파일에 쓸 수 없습니다. 환경변수 " + secretVariable(name) + "를 사용하세요");
        } else if (restricted && !allowed.contains(name)) {
          problem(
              item,
              "알 수 없는 항목입니다. 허용 항목: " + String.join(", ", allowed.stream().sorted().toList()));
        } else {
          found.put(name, tuple.getValueNode());
        }
      }
      return found;
    }

    /** 비밀 항목이면 대신 쓸 환경변수 이름, 아니면 null. 대소문자와 {@code _}, {@code -}를 무시한다. */
    private static String secretVariable(String name) {
      String normalized = name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
      return switch (normalized) {
        case "token", "githubtoken" -> "GITHUB_TOKEN";
        case "apikey", "anthropicapikey" -> "ANTHROPIC_API_KEY";
        default -> null;
      };
    }

    private List<String> patterns(Node node, String path, List<String> fallback) {
      if (node == null) {
        return fallback;
      }
      if (isNull(node)) {
        return List.of();
      }
      if (!(node instanceof SequenceNode sequence)) {
        problem(path, "패턴 목록이어야 합니다");
        return fallback;
      }
      List<String> patterns = new ArrayList<>();
      int index = 0;
      for (Node element : sequence.getValue()) {
        String item = path + "[" + index++ + "]";
        String pattern = string(element, item);
        if (pattern == null) {
          continue;
        }
        try {
          GlobMatcher.validate(pattern);
          patterns.add(pattern);
        } catch (GlobSyntaxException e) {
          problem(item, "잘못된 패턴 '" + pattern + "': " + e.reason());
        }
      }
      return patterns;
    }

    /** 따옴표로 감싼 문자열 값. 숫자나 true처럼 읽히는 값은 따옴표가 필요하다. */
    private String string(Node node, String item) {
      if (node instanceof ScalarNode scalar && scalar.getTag().equals(Tag.STR)) {
        return scalar.getValue();
      }
      problem(item, "문자열이어야 합니다 (숫자나 true처럼 읽히는 값은 큰따옴표로 감쌉니다)");
      return null;
    }

    private String model(Node node, String fallback) {
      if (node == null) {
        return fallback;
      }
      String value = string(node, "llm.model");
      if (value == null) {
        return fallback;
      }
      if (value.isBlank()) {
        problem("llm.model", "빈 문자열일 수 없습니다");
        return fallback;
      }
      return value;
    }

    private String effort(Node node, String fallback) {
      if (node == null) {
        return fallback;
      }
      String value = string(node, "llm.effort");
      if (value == null) {
        return fallback;
      }
      if (!EFFORTS.contains(value)) {
        problem("llm.effort", "허용 값: low, medium, high, xhigh, max");
        return fallback;
      }
      return value;
    }

    private int integer(Node node, String item, int min, int max, int fallback) {
      if (node == null) {
        return fallback;
      }
      BigDecimal value = number(node, item, true);
      if (value == null) {
        return fallback;
      }
      if (value.compareTo(BigDecimal.valueOf(min)) < 0
          || value.compareTo(BigDecimal.valueOf(max)) > 0) {
        problem(item, min + "~" + max + " 정수여야 합니다 (값: " + value.toPlainString() + ")");
        return fallback;
      }
      return value.intValueExact();
    }

    /** 범위는 소수 다섯째 자리 이하를 반올림하기 전의 원본 값으로 검사한다. */
    private BigDecimal cost(Node node, BigDecimal fallback) {
      if (node == null) {
        return fallback;
      }
      BigDecimal value = number(node, "cost.maxUsdPerReview", false);
      if (value == null) {
        return fallback;
      }
      if (value.compareTo(MIN_COST) < 0 || value.compareTo(MAX_COST) > 0) {
        problem("cost.maxUsdPerReview", "0.01~100.00 USD여야 합니다 (값: " + value.toPlainString() + ")");
        return fallback;
      }
      return value;
    }

    private void pricing(Node node, SortedMap<String, ModelPricing> pricing) {
      Map<String, Node> models = entries(node, "cost.pricing", Set.of(), false);
      for (Map.Entry<String, Node> model : models.entrySet()) {
        String base = "cost.pricing." + model.getKey();
        if (model.getKey().isBlank()) {
          problem("cost.pricing", "모델 이름은 빈 문자열일 수 없습니다");
          continue;
        }
        Map<String, Node> fields = entries(model.getValue(), base, Set.copyOf(PRICE_FIELDS), true);
        Map<String, BigDecimal> prices = new LinkedHashMap<>();
        for (String field : PRICE_FIELDS) {
          String item = base + "." + field;
          Node value = fields.get(field);
          if (value == null) {
            if (model.getValue() instanceof MappingNode || isNull(model.getValue())) {
              problem(item, "네 단가(input, output, cacheWrite, cacheRead)를 모두 적어야 합니다");
            }
            continue;
          }
          BigDecimal price = number(value, item, false);
          if (price != null && price.signum() < 0) {
            problem(item, "0 이상이어야 합니다 (값: " + price.toPlainString() + ")");
          } else if (price != null) {
            prices.put(field, price);
          }
        }
        if (prices.size() == PRICE_FIELDS.size()) {
          pricing.put(
              model.getKey(),
              new ModelPricing(
                  prices.get("input"),
                  prices.get("output"),
                  prices.get("cacheWrite"),
                  prices.get("cacheRead")));
        }
      }
    }

    /** 원문 문자열에서 읽은 수. 정수만 허용하면 소수 표기를 거부한다. 문제가 있으면 null이다. */
    private BigDecimal number(Node node, String item, boolean integerOnly) {
      if (node instanceof ScalarNode scalar) {
        boolean isInt = scalar.getTag().equals(Tag.INT);
        boolean isFloat = scalar.getTag().equals(Tag.FLOAT);
        if (isInt || (isFloat && !integerOnly)) {
          try {
            return new BigDecimal(scalar.getValue().replace("_", ""));
          } catch (NumberFormatException e) {
            problem(item, "10진수로 읽을 수 없습니다");
            return null;
          }
        }
      }
      problem(item, integerOnly ? "정수여야 합니다" : "숫자여야 합니다");
      return null;
    }
  }
}
