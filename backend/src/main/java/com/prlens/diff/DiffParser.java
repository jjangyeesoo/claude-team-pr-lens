package com.prlens.diff;

import com.prlens.diff.DiffParseException.Kind;
import com.prlens.model.DiffLine;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** unified diff patch를 Hunk 목록으로 해석한다. 오류가 있으면 부분 결과 없이 예외를 던진다. */
public final class DiffParser {

  private static final Pattern HEADER =
      Pattern.compile("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$", Pattern.DOTALL);

  private DiffParser() {}

  /**
   * 오류 종류는 줄이 놓인 상태로 정한다. Hunk가 열려 있으면 {@code @@}로 시작하는 줄은 줄 수 불일치, 접두어가 없거나 ` `, {@code +}, {@code
   * -}, {@code \}가 아니면 잘못된 접두어다. Hunk가 끝났거나 아직 없을 때는 {@code @@}로 시작하는 줄만 헤더 문법 오류이고, 그 밖의 줄은 첫 헤더
   * 앞이면 앞 내용, 아니면 초과 줄(줄 수 불일치)이다.
   *
   * @param path 오류 메시지에 담을 파일 경로
   * @param patch GitHub가 돌려준 patch 텍스트. 빈 문자열이면 빈 목록
   * @throws DiffParseException patch가 유효한 unified diff가 아닐 때
   */
  public static List<Hunk> parse(String path, String patch) {
    String normalized = patch.replace("\r\n", "\n").replace('\r', '\n');
    if (normalized.endsWith("\n")) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    if (normalized.isEmpty()) {
      return List.of();
    }
    String[] lines = normalized.split("\n", -1);

    List<Hunk> hunks = new ArrayList<>();
    Builder current = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      int lineNo = i + 1;

      if (current != null && line.startsWith("\\")) {
        if (!current.markNoNewline()) {
          throw error(path, lineNo, hunks.size(), Kind.INVALID_PREFIX);
        }
        continue;
      }
      if (current != null && current.isComplete()) {
        hunks.add(current.build());
        current = null;
      }

      if (current == null) {
        current = startHunk(path, line, lineNo, hunks);
        continue;
      }
      if (line.startsWith("@@")) {
        // 줄 수가 남았는데 다음 헤더가 나왔다
        throw error(path, lineNo, hunks.size(), Kind.LINE_COUNT_MISMATCH);
      }
      if (line.isEmpty()) {
        throw error(path, lineNo, hunks.size(), Kind.INVALID_PREFIX);
      }
      switch (line.charAt(0)) {
        case ' ' -> current.add(path, lineNo, hunks.size(), LineKind.CONTEXT, line);
        case '-' -> current.add(path, lineNo, hunks.size(), LineKind.REMOVED, line);
        case '+' -> current.add(path, lineNo, hunks.size(), LineKind.ADDED, line);
        default -> throw error(path, lineNo, hunks.size(), Kind.INVALID_PREFIX);
      }
    }
    if (current != null) {
      if (!current.isComplete()) {
        throw error(path, lines.length, hunks.size(), Kind.LINE_COUNT_MISMATCH);
      }
      hunks.add(current.build());
    }
    return List.copyOf(hunks);
  }

  /** Hunk가 없는 상태(첫 헤더 앞이거나 직전 Hunk가 끝난 뒤)에서 한 줄을 헤더로 읽는다. */
  private static Builder startHunk(String path, String line, int lineNo, List<Hunk> hunks) {
    Matcher m = HEADER.matcher(line);
    if (!m.matches()) {
      if (line.startsWith("@@")) {
        throw error(path, lineNo, hunks.size(), Kind.HEADER_SYNTAX);
      }
      if (hunks.isEmpty()) {
        throw error(path, lineNo, 0, Kind.CONTENT_BEFORE_HEADER);
      }
      throw error(path, lineNo, hunks.size() - 1, Kind.LINE_COUNT_MISMATCH);
    }
    int baseStart = toInt(path, lineNo, hunks.size(), m.group(1));
    int baseCount = m.group(2) == null ? 1 : toInt(path, lineNo, hunks.size(), m.group(2));
    int headStart = toInt(path, lineNo, hunks.size(), m.group(3));
    int headCount = m.group(4) == null ? 1 : toInt(path, lineNo, hunks.size(), m.group(4));
    if (!hunks.isEmpty()) {
      Hunk previous = hunks.get(hunks.size() - 1);
      long previousEnd = (long) previous.headStart() + previous.headCount() - 1;
      // head 줄 수 0인 Hunk는 구간 끝이 시작보다 작으므로 시작 줄 비교를 따로 한다
      if (previousEnd >= headStart || previous.headStart() >= headStart) {
        throw error(path, lineNo, hunks.size(), Kind.HUNK_OVERLAP);
      }
    }
    return new Builder(baseStart, baseCount, headStart, headCount, m.group(5));
  }

  private static int toInt(String path, int lineNo, int hunkIndex, String digits) {
    try {
      return Integer.parseInt(digits);
    } catch (NumberFormatException e) {
      throw error(path, lineNo, hunkIndex, Kind.HEADER_SYNTAX);
    }
  }

  private static DiffParseException error(String path, int lineNo, int hunkIndex, Kind kind) {
    return new DiffParseException(path, lineNo, hunkIndex, kind);
  }

  private static final class Builder {
    private final int baseStart;
    private final int baseCount;
    private final int headStart;
    private final int headCount;
    private final String sectionHeading;
    private final List<DiffLine> lines = new ArrayList<>();
    private int baseLeft;
    private int headLeft;

    Builder(int baseStart, int baseCount, int headStart, int headCount, String sectionHeading) {
      this.baseStart = baseStart;
      this.baseCount = baseCount;
      this.headStart = headStart;
      this.headCount = headCount;
      this.sectionHeading = sectionHeading;
      this.baseLeft = baseCount;
      this.headLeft = headCount;
    }

    boolean isComplete() {
      return baseLeft == 0 && headLeft == 0;
    }

    void add(String path, int lineNo, int hunkIndex, LineKind kind, String line) {
      boolean usesBase = kind != LineKind.ADDED;
      boolean usesHead = kind != LineKind.REMOVED;
      if ((usesBase && baseLeft == 0) || (usesHead && headLeft == 0)) {
        throw error(path, lineNo, hunkIndex, Kind.LINE_COUNT_MISMATCH);
      }
      if (usesBase) {
        baseLeft--;
      }
      if (usesHead) {
        headLeft--;
      }
      lines.add(new DiffLine(kind, line.substring(1), false));
    }

    /** 직전 줄에 표시를 붙인다. 붙일 줄이 없거나 이미 붙었으면 false. */
    boolean markNoNewline() {
      int last = lines.size() - 1;
      if (last < 0 || lines.get(last).noNewlineAtEnd()) {
        return false;
      }
      DiffLine line = lines.get(last);
      lines.set(last, new DiffLine(line.kind(), line.content(), true));
      return true;
    }

    Hunk build() {
      return new Hunk(baseStart, baseCount, headStart, headCount, sectionHeading, lines);
    }
  }
}
