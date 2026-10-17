package com.prlens.output;

import static com.prlens.output.ResultFixtures.complete;
import static com.prlens.output.ResultFixtures.incomplete;
import static com.prlens.output.ResultFixtures.inline;
import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExitCodeResolverTest {

  // 15.9
  @Test
  void blockerGivesOneEvenWhenComplete() {
    assertThat(
            ExitCodeResolver.resolve(complete("", List.of(inline("A.java", 1, Severity.BLOCKER)))))
        .isEqualTo(1);
  }

  // 16.8
  @Test
  void blockerGivesOneEvenWhenIncomplete() {
    assertThat(ExitCodeResolver.resolve(incomplete(List.of(inline("A.java", 1, Severity.BLOCKER)))))
        .isEqualTo(1);
  }

  // 16.7
  @Test
  void incompleteWithoutBlockerGivesThree() {
    assertThat(ExitCodeResolver.resolve(incomplete(List.of()))).isEqualTo(3);
    assertThat(ExitCodeResolver.resolve(incomplete(List.of(inline("A.java", 1, Severity.MAJOR)))))
        .isEqualTo(3);
  }

  // 15.10
  @Test
  void completeWithoutBlockerGivesZero() {
    assertThat(ExitCodeResolver.resolve(complete("", List.of()))).isEqualTo(0);
    assertThat(
            ExitCodeResolver.resolve(
                complete(
                    "",
                    List.of(
                        inline("A.java", 1, Severity.MAJOR),
                        inline("A.java", 2, Severity.MINOR),
                        inline("B.java", 3, Severity.NIT)))))
        .isEqualTo(0);
  }
}
