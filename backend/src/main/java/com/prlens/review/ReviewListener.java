package com.prlens.review;

import com.prlens.model.ReviewStats;
import java.math.BigDecimal;

/** 리뷰 진행 중에 알릴 것. CLI는 stderr, P2는 로그와 DB로 구현한다. */
public interface ReviewListener {
  default void modeDecided(ReviewStats stats) {}

  /** {@code index}는 1부터 시작한다 ("n/N"). */
  default void chunkStarted(int index, int total) {}

  default void costThresholdExceeded(
      BigDecimal cumulative, BigDecimal limit, int remainingChunks) {}
}
