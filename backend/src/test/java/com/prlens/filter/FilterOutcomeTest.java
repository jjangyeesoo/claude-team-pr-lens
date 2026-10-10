package com.prlens.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.model.ChangedFile;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterOutcomeTest {

  private static final ChangedFile TARGET =
      new ChangedFile("A.java", null, FileStatus.MODIFIED, 1, 0, new PatchContent.Absent());
  private static final ExcludedFile EXCLUDED = new ExcludedFile("logo.png", "**/*.png");
  private static final Warning WARNING = new Warning("secret_exclude_kept", "해제 요청을 무시했습니다");

  @Test
  void listsAreCopiedAndUnmodifiable() {
    List<ChangedFile> targets = new ArrayList<>(List.of(TARGET));
    List<ExcludedFile> excluded = new ArrayList<>(List.of(EXCLUDED));
    List<Warning> warnings = new ArrayList<>(List.of(WARNING));
    FilterOutcome outcome = new FilterOutcome(targets, excluded, warnings);

    targets.clear();
    excluded.clear();
    warnings.clear();

    assertThat(outcome.targets()).containsExactly(TARGET);
    assertThat(outcome.excluded()).containsExactly(EXCLUDED);
    assertThat(outcome.warnings()).containsExactly(WARNING);
    assertThatThrownBy(() -> outcome.targets().add(TARGET))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> outcome.excluded().add(EXCLUDED))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> outcome.warnings().add(WARNING))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullListIsRejectedWithFieldName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new FilterOutcome(null, List.of(), List.of()))
        .withMessage("targets");
    assertThatNullPointerException()
        .isThrownBy(() -> new FilterOutcome(List.of(), null, List.of()))
        .withMessage("excluded");
    assertThatNullPointerException()
        .isThrownBy(() -> new FilterOutcome(List.of(), List.of(), null))
        .withMessage("warnings");
  }
}
