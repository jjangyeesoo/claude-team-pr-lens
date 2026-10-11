package com.prlens.github;

import java.util.List;
import java.util.Objects;

/**
 * 변경 파일 목록의 한 페이지.
 *
 * @param hasNext 다음 페이지가 있는지
 */
public record FilePage(List<Entry> files, boolean hasNext) {
  public FilePage {
    Objects.requireNonNull(files, "files");
    files = List.copyOf(files);
  }

  /**
   * GitHub가 준 값 그대로의 변경 파일 한 건. 상태 매핑과 patch 해석은 {@code PrFetcher}가 한다.
   *
   * @param previousFilename 이름 변경일 때의 이전 경로. 그 밖에는 null
   * @param status GitHub 상태 문자열 ({@code added}, {@code modified}, {@code renamed} 등)
   * @param patch GitHub가 patch를 주지 않으면 null
   */
  public record Entry(
      String filename,
      String previousFilename,
      String status,
      int additions,
      int deletions,
      String patch) {
    public Entry {
      Objects.requireNonNull(filename, "filename");
      Objects.requireNonNull(status, "status");
    }
  }
}
