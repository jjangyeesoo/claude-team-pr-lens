package com.prlens.glob;

import com.prlens.model.RepoPaths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 저장소 경로용 glob. {@code java.nio.file.PathMatcher}를 쓰지 않고 {@code /} 구간 단위로 직접 맞춘다.
 *
 * <p>{@code **}가 구간 전체이면 0개 이상의 구간, {@code *}는 {@code /}를 뺀 0개 이상의 문자, {@code ?}는 {@code /}를 뺀 한
 * 문자, {@code {a,b}}는 (중첩 가능한) 중괄호 확장이다. 중괄호를 모두 펼친 패턴마다 앞의 {@code ./}·{@code /} 제거와 구간 컴파일을 따로 한다.
 */
public final class GlobMatcher {

  private static final int MAX_EXPANSIONS = 256;
  private static final int MAX_CACHED = 1_000;

  /** 컴파일한 패턴 캐시. 유효한 패턴만 담고, 가득 차면 비운다. */
  private static final Map<String, List<List<Pattern>>> CACHE = new ConcurrentHashMap<>();

  private GlobMatcher() {}

  /**
   * @throws GlobSyntaxException 패턴 문법이 유효하지 않을 때
   */
  public static boolean matches(String pattern, String path) {
    List<List<Pattern>> alternatives = compile(pattern);
    String[] pathSegments = RepoPaths.normalize(path).split("/", -1);
    for (List<Pattern> segments : alternatives) {
      if (matchSegments(segments, pathSegments)) {
        return true;
      }
    }
    return false;
  }

  /**
   * @throws GlobSyntaxException 패턴 문법이 유효하지 않을 때
   */
  public static void validate(String pattern) throws GlobSyntaxException {
    compile(pattern);
  }

  /** 패턴을 중괄호가 없는 패턴들로 펼친 뒤 각각을 구간 목록으로 컴파일한다. 구간이 {@code null}이면 {@code **}이다. */
  private static List<List<Pattern>> compile(String pattern) {
    List<List<Pattern>> cached = CACHE.get(pattern);
    if (cached != null) {
      return cached;
    }
    if (pattern.indexOf('\\') >= 0) {
      throw new GlobSyntaxException(pattern, "백슬래시(\\)는 쓸 수 없습니다");
    }
    checkBraces(pattern);
    List<List<Pattern>> result = new ArrayList<>();
    for (String expanded : expand(pattern)) {
      String body = RepoPaths.normalize(expanded);
      if (body.isEmpty()) {
        throw new GlobSyntaxException(pattern, "패턴이 비어 있습니다");
      }
      List<Pattern> segments = new ArrayList<>();
      for (String segment : body.split("/", -1)) {
        segments.add(compileSegment(pattern, segment));
      }
      result.add(Collections.unmodifiableList(segments));
    }
    if (CACHE.size() >= MAX_CACHED) {
      CACHE.clear();
    }
    CACHE.put(pattern, result);
    return result;
  }

  private static void checkBraces(String pattern) {
    int depth = 0;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth < 0) {
        throw new GlobSyntaxException(pattern, "짝이 맞지 않는 중괄호입니다");
      }
    }
    if (depth != 0) {
      throw new GlobSyntaxException(pattern, "짝이 맞지 않는 중괄호입니다");
    }
    if (countExpansions(pattern) > MAX_EXPANSIONS) {
      throw new GlobSyntaxException(pattern, "중괄호를 펼친 개수가 " + MAX_EXPANSIONS + "개를 넘습니다");
    }
  }

  /** 중괄호를 모두 펼쳤을 때의 개수. 상한을 넘으면 더 키우지 않는다. */
  private static long countExpansions(String s) {
    long total = 1;
    int i = 0;
    while (i < s.length()) {
      if (s.charAt(i) == '{') {
        int close = matchingBrace(s, i);
        long group = 0;
        for (String alternative : splitTopLevel(s.substring(i + 1, close), ',')) {
          group += countExpansions(alternative);
        }
        total = Math.min(total * group, MAX_EXPANSIONS + 1L);
        i = close + 1;
      } else {
        i++;
      }
    }
    return total;
  }

  private static int matchingBrace(String s, int open) {
    int depth = 0;
    for (int i = open; i < s.length(); i++) {
      if (s.charAt(i) == '{') {
        depth++;
      } else if (s.charAt(i) == '}' && --depth == 0) {
        return i;
      }
    }
    throw new IllegalStateException("checkBraces가 먼저 짝을 확인한다");
  }

  /** 중괄호를 모두 펼친다. 앞에서부터 바깥 중괄호 하나씩 풀고, 대안마다 나머지를 다시 펼친다. */
  private static List<String> expand(String s) {
    int open = s.indexOf('{');
    if (open < 0) {
      return List.of(s);
    }
    int close = matchingBrace(s, open);
    List<String> out = new ArrayList<>();
    for (String alternative : splitTopLevel(s.substring(open + 1, close), ',')) {
      out.addAll(expand(s.substring(0, open) + alternative + s.substring(close + 1)));
    }
    return out;
  }

  private static List<String> splitTopLevel(String s, char separator) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
      } else if (c == separator && depth == 0) {
        parts.add(s.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(s.substring(start));
    return parts;
  }

  private static Pattern compileSegment(String pattern, String segment) {
    if (segment.equals("**")) {
      return null;
    }
    if (segment.contains("**")) {
      throw new GlobSyntaxException(pattern, "**는 경로 구간 전체여야 합니다: " + segment);
    }
    StringBuilder regex = new StringBuilder();
    for (int i = 0; i < segment.length(); i++) {
      char c = segment.charAt(i);
      switch (c) {
        case '*' -> regex.append("[^/]*");
        case '?' -> regex.append("[^/]");
        default -> regex.append(Pattern.quote(String.valueOf(c)));
      }
    }
    return Pattern.compile(regex.toString());
  }

  private static boolean matchSegments(List<Pattern> segments, String[] path) {
    Boolean[][] memo = new Boolean[segments.size() + 1][path.length + 1];
    return match(segments, path, 0, 0, memo);
  }

  private static boolean match(
      List<Pattern> segments, String[] path, int i, int j, Boolean[][] memo) {
    if (memo[i][j] != null) {
      return memo[i][j];
    }
    boolean result;
    if (i == segments.size()) {
      result = j == path.length;
    } else if (segments.get(i) == null) {
      result =
          match(segments, path, i + 1, j, memo)
              || (j < path.length && match(segments, path, i, j + 1, memo));
    } else {
      result =
          j < path.length
              && segments.get(i).matcher(path[j]).matches()
              && match(segments, path, i + 1, j + 1, memo);
    }
    memo[i][j] = result;
    return result;
  }
}
