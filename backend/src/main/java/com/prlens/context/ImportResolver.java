package com.prlens.context;

import com.prlens.context.BaseFileReader.Outcome;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CLAUDE.md와 그 import 파일의 {@code @경로} 선언을 base SHA에서 따라간다(요구사항 6). 깊이 1~4만 가져오고, 이미 담긴 경로와 순환은
 * 건너뛰며, 저장소 밖 경로와 없는 파일, 폴더는 경고 후 건너뛴다.
 */
final class ImportResolver {

  private static final int MAX_DEPTH = 4;
  private static final String NOT_FOUND = "import_not_found";
  private static final String IS_DIRECTORY = "import_is_directory";
  private static final Pattern IMPORT = Pattern.compile("(^|\\s)@(\\S+)");
  private static final Pattern DRIVE = Pattern.compile("^[A-Za-z]:.*");

  /** 가져온 파일(또는 시작 CLAUDE.md)과 그 깊이. */
  private record Node(String path, String content, int depth) {}

  private final BaseFileReader reader;
  private final WarningSink warnings;

  ImportResolver(BaseFileReader reader, WarningSink warnings) {
    this.reader = reader;
    this.warnings = warnings;
  }

  /** 새로 담을 {@code IMPORT} 파일을 BFS 순서로 돌려준다. 시작점은 {@code collected} 가운데 출처 {@code CLAUDE_MD}뿐이다. */
  List<ContextFile> resolve(List<ContextFile> collected) {
    Set<String> taken = new HashSet<>();
    Deque<Node> queue = new ArrayDeque<>();
    for (ContextFile file : collected) {
      taken.add(file.path());
      if (file.source() == ContextSource.CLAUDE_MD) {
        queue.add(new Node(file.path(), file.content(), 0));
      }
    }
    // 요청했다가 실패한 경로. 값이 null이면 이미 경고를 낸 1MB 초과다
    Map<String, String> failures = new HashMap<>();
    List<ContextFile> result = new ArrayList<>();
    while (!queue.isEmpty()) {
      Node node = queue.poll();
      Set<String> seenInFile = new HashSet<>();
      for (String raw : scan(node.content())) {
        Optional<String> resolved = resolvePath(node.path(), raw);
        if (resolved.isEmpty()) {
          warn("import_outside_repo", "저장소 밖을 가리키는 import를 건너뜁니다", node, raw);
          continue;
        }
        String path = resolved.get();
        if (path.isEmpty()) {
          warn(IS_DIRECTORY, "폴더를 가리키는 import를 건너뜁니다", node, raw);
          continue;
        }
        if (!seenInFile.add(path) || taken.contains(path)) {
          continue;
        }
        int depth = node.depth() + 1;
        if (depth > MAX_DEPTH) {
          warn("import_depth_exceeded", "import 깊이가 " + MAX_DEPTH + "를 넘어 건너뜁니다", node, raw);
          continue;
        }
        if (failures.containsKey(path)) {
          warnFailure(failures.get(path), node, raw);
          continue;
        }
        Outcome outcome = reader.fetch(path);
        if (outcome instanceof Outcome.Found found) {
          taken.add(path);
          result.add(new ContextFile(path, found.content(), ContextSource.IMPORT, Revision.BASE));
          queue.add(new Node(path, found.content(), depth));
        } else if (outcome instanceof Outcome.Missing) {
          failures.put(path, NOT_FOUND);
          warnFailure(NOT_FOUND, node, raw);
        } else if (outcome instanceof Outcome.Directory) {
          failures.put(path, IS_DIRECTORY);
          warnFailure(IS_DIRECTORY, node, raw);
        } else {
          failures.put(path, null);
        }
      }
    }
    return result;
  }

  private void warnFailure(String code, Node declaredBy, String raw) {
    if (code == null) {
      return;
    }
    String message =
        code.equals(NOT_FOUND) ? "없는 파일을 가리키는 import를 건너뜁니다" : "폴더를 가리키는 import를 건너뜁니다";
    warn(code, message, declaredBy, raw);
  }

  private void warn(String code, String message, Node declaredBy, String raw) {
    warnings.warn(new Warning(code, message + ": " + declaredBy.path() + "의 @" + raw));
  }

  /** 펜스 블록과 인라인 코드 밖의 import 선언 원문 경로를 본문 순서로 돌려준다. */
  static List<String> scan(String content) {
    String text = content.replace("\r\n", "\n").replace('\r', '\n');
    List<String> found = new ArrayList<>();
    int fence = 0;
    for (String line : text.split("\n", -1)) {
      String stripped = stripLeading(line);
      if (fence > 0) {
        if (isClosingFence(stripped, fence)) {
          fence = 0;
        }
        continue;
      }
      int ticks = backtickRun(stripped, 0);
      if (ticks >= 3) {
        fence = ticks;
        continue;
      }
      boolean[] inCode = codeSpans(line);
      Matcher matcher = IMPORT.matcher(line);
      while (matcher.find()) {
        if (!inCode[matcher.start(2) - 1]) {
          found.add(matcher.group(2));
        }
      }
    }
    return found;
  }

  private static String stripLeading(String line) {
    int i = 0;
    while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
      i++;
    }
    return line.substring(i);
  }

  private static int backtickRun(String s, int from) {
    int i = from;
    while (i < s.length() && s.charAt(i) == '`') {
      i++;
    }
    return i - from;
  }

  private static boolean isClosingFence(String stripped, int fence) {
    int ticks = backtickRun(stripped, 0);
    return ticks >= fence && stripped.substring(ticks).isBlank();
  }

  /** 한 줄 안에서 길이 n의 백틱 연속과 다음의 정확히 길이 n인 백틱 연속 사이(백틱 포함)를 표시한다. 짝이 없으면 글자로 둔다. */
  private static boolean[] codeSpans(String line) {
    boolean[] inCode = new boolean[line.length()];
    int i = 0;
    while (i < line.length()) {
      if (line.charAt(i) != '`') {
        i++;
        continue;
      }
      int n = backtickRun(line, i);
      int k = i + n;
      int close = -1;
      while (k < line.length()) {
        if (line.charAt(k) != '`') {
          k++;
          continue;
        }
        int m = backtickRun(line, k);
        if (m == n) {
          close = k;
          break;
        }
        k += m;
      }
      if (close < 0) {
        i += n;
        continue;
      }
      for (int j = i; j < close + n; j++) {
        inCode[j] = true;
      }
      i = close + n;
    }
    return inCode;
  }

  /**
   * 선언 파일의 폴더 기준으로 해석한 정규화 경로. 저장소 밖(절대 경로, {@code ~/}, 드라이브 문자, 루트 위 {@code ..})이면 비어 있다. 결과가 빈
   * 문자열이면 저장소 루트 폴더다.
   */
  static Optional<String> resolvePath(String declaredBy, String raw) {
    String declared = raw.replace('\\', '/');
    if (declared.startsWith("/")
        || declared.startsWith("~/")
        || DRIVE.matcher(declared).matches()) {
      return Optional.empty();
    }
    List<String> segments = new ArrayList<>();
    int slash = declaredBy.lastIndexOf('/');
    if (slash >= 0) {
      segments.addAll(List.of(declaredBy.substring(0, slash).split("/")));
    }
    for (String segment : declared.split("/")) {
      if (segment.isEmpty() || segment.equals(".")) {
        continue;
      }
      if (segment.equals("..")) {
        if (segments.isEmpty()) {
          return Optional.empty();
        }
        segments.remove(segments.size() - 1);
      } else {
        segments.add(segment);
      }
    }
    return Optional.of(String.join("/", segments));
  }
}
