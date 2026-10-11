package com.prlens.model;

import java.util.List;
import java.util.Objects;

/**
 * 리뷰 결과 (T1 출력).
 *
 * <p>불변식: {@code status == COMPLETE}와 {@code incompleteReasons}가 비어 있음은 같은 뜻이고, {@code
 * excludedFiles}는 {@code excludedFileDetails}의 path 목록과 순서까지 같다.
 */
public record ReviewResult(
    ResultStatus status,
    String summary,
    List<Finding> findings,
    List<String> excludedFiles,
    List<ExcludedFile> excludedFileDetails,
    Usage usage,
    List<IncompleteReason> incompleteReasons,
    IncompleteDetails incompleteDetails,
    ReviewStats stats) {
  public ReviewResult {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(summary, "summary");
    Objects.requireNonNull(findings, "findings");
    Objects.requireNonNull(excludedFiles, "excludedFiles");
    Objects.requireNonNull(excludedFileDetails, "excludedFileDetails");
    Objects.requireNonNull(usage, "usage");
    Objects.requireNonNull(incompleteReasons, "incompleteReasons");
    Objects.requireNonNull(incompleteDetails, "incompleteDetails");
    Objects.requireNonNull(stats, "stats");
    findings = List.copyOf(findings);
    excludedFiles = List.copyOf(excludedFiles);
    excludedFileDetails = List.copyOf(excludedFileDetails);
    incompleteReasons = List.copyOf(incompleteReasons);
    if ((status == ResultStatus.COMPLETE) != incompleteReasons.isEmpty()) {
      throw new IllegalArgumentException(
          "status와 incompleteReasons가 맞지 않습니다: " + status + ", " + incompleteReasons);
    }
    if (!excludedFiles.equals(excludedFileDetails.stream().map(ExcludedFile::path).toList())) {
      throw new IllegalArgumentException("excludedFiles가 excludedFileDetails의 path 목록과 다릅니다");
    }
  }
}
