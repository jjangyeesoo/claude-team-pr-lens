package com.prlens.diff;

import com.prlens.model.DiffLine;
import com.prlens.model.Hunk;
import java.util.List;

/** Hunk 목록을 unified diff 텍스트로 출력한다. 줄 수는 생략하지 않고 모든 줄을 LF로 끝맺는다. */
public final class DiffPrinter {

  private static final String NO_NEWLINE_MARKER = "\\ No newline at end of file\n";

  private DiffPrinter() {}

  public static String print(List<Hunk> hunks) {
    StringBuilder out = new StringBuilder();
    for (Hunk hunk : hunks) {
      out.append("@@ -")
          .append(hunk.baseStart())
          .append(',')
          .append(hunk.baseCount())
          .append(" +")
          .append(hunk.headStart())
          .append(',')
          .append(hunk.headCount())
          .append(" @@")
          .append(hunk.sectionHeading())
          .append('\n');
      for (DiffLine line : hunk.lines()) {
        out.append(prefix(line)).append(line.content()).append('\n');
        if (line.noNewlineAtEnd()) {
          out.append(NO_NEWLINE_MARKER);
        }
      }
    }
    return out.toString();
  }

  private static char prefix(DiffLine line) {
    return switch (line.kind()) {
      case ADDED -> '+';
      case REMOVED -> '-';
      case CONTEXT -> ' ';
    };
  }
}
