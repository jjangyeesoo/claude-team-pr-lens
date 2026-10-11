package com.prlens.filter;

import com.prlens.model.ChangedFile;
import com.prlens.model.ExcludedFile;
import com.prlens.support.Warning;
import java.util.List;
import java.util.Objects;

/**
 * @param warnings 호출자가 {@code WarningSink}로 넘길 경고
 */
public record FilterOutcome(
    List<ChangedFile> targets, List<ExcludedFile> excluded, List<Warning> warnings) {
  public FilterOutcome {
    Objects.requireNonNull(targets, "targets");
    Objects.requireNonNull(excluded, "excluded");
    Objects.requireNonNull(warnings, "warnings");
    targets = List.copyOf(targets);
    excluded = List.copyOf(excluded);
    warnings = List.copyOf(warnings);
  }
}
