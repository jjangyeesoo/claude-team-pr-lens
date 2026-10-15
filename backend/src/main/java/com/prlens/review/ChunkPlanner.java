package com.prlens.review;

import com.prlens.model.ChangedFile;
import com.prlens.model.ReviewMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 리뷰 모드를 정하고 분할 모드의 Chunk를 계획한다 (요구사항 13).
 *
 * <p>입력은 제외 파일을 뺀 Review_Target_File이다. 같은 입력 집합에는 입력 순서와 무관하게 같은 계획이 나온다.
 */
final class ChunkPlanner {

  /** head 경로의 코드 포인트 오름차순. UTF-16 순서와 달리 서로게이트 쌍이 BMP 뒤에 온다. */
  private static final Comparator<ChangedFile> BY_PATH =
      (a, b) -> compareCodePoints(a.path(), b.path());

  private ChunkPlanner() {}

  static int changedLineCount(List<ChangedFile> targets) {
    int sum = 0;
    for (ChangedFile file : targets) {
      sum += lines(file);
    }
    return sum;
  }

  /** 대상 0개 NO_TARGET, 줄 수 ≤ L 단일(0줄 포함, G-2), ≤ 3L 분할, 초과 요약 전용. */
  static ReviewMode decideMode(List<ChangedFile> targets, int sizeLimit) {
    if (targets.isEmpty()) {
      return ReviewMode.NO_TARGET;
    }
    long count = changedLineCount(targets);
    if (count <= sizeLimit) {
      return ReviewMode.SINGLE;
    }
    return count <= 3L * sizeLimit ? ReviewMode.SPLIT : ReviewMode.SUMMARY_ONLY;
  }

  /** 경로 순서로 이어 붙이는 결정적 greedy. L을 넘는 파일은 단독 Chunk로 만든다. */
  static List<List<ChangedFile>> plan(List<ChangedFile> targets, int sizeLimit) {
    List<ChangedFile> sorted = new ArrayList<>(targets);
    sorted.sort(BY_PATH);

    List<List<ChangedFile>> chunks = new ArrayList<>();
    List<ChangedFile> current = new ArrayList<>();
    long sum = 0;
    for (ChangedFile file : sorted) {
      int n = lines(file);
      if (n > sizeLimit) {
        if (!current.isEmpty()) {
          chunks.add(current);
          current = new ArrayList<>();
          sum = 0;
        }
        chunks.add(List.of(file));
        continue;
      }
      if (sum + n > sizeLimit) {
        chunks.add(current);
        current = new ArrayList<>();
        sum = 0;
      }
      current.add(file);
      sum += n;
    }
    if (!current.isEmpty()) {
      chunks.add(current);
    }
    return chunks;
  }

  private static int lines(ChangedFile file) {
    return file.additions() + file.deletions();
  }

  private static int compareCodePoints(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }
}
