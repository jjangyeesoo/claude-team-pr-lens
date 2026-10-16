package com.prlens.diff;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.DiffLine;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiffPrinterTest {

  @Test
  void printsFullCountHeaderWithSectionHeading() {
    Hunk hunk =
        new Hunk(3, 1, 3, 1, " void f()", List.of(new DiffLine(LineKind.CONTEXT, "a", false)));

    assertThat(DiffPrinter.print(List.of(hunk))).startsWith("@@ -3,1 +3,1 @@ void f()\n");
  }

  @Test
  void endsEveryLineWithLf() {
    Hunk hunk =
        new Hunk(
            1,
            1,
            1,
            2,
            "",
            List.of(
                new DiffLine(LineKind.CONTEXT, "a", false),
                new DiffLine(LineKind.ADDED, "b", false)));

    assertThat(DiffPrinter.print(List.of(hunk))).isEqualTo("@@ -1,1 +1,2 @@\n a\n+b\n");
    assertThat(DiffPrinter.print(List.of())).isEmpty();
  }

  @Test
  void writesNoNewlineMarkerAfterFlaggedLine() {
    Hunk hunk = new Hunk(1, 1, 1, 0, "", List.of(new DiffLine(LineKind.REMOVED, "a", true)));

    assertThat(DiffPrinter.print(List.of(hunk)))
        .isEqualTo("@@ -1,1 +1,0 @@\n-a\n\\ No newline at end of file\n");
  }
}
