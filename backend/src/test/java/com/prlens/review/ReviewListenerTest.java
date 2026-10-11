package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewStats;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReviewListenerTest {

  // 구현체는 필요한 알림만 재정의한다 (CLI는 stderr, P2는 로그와 DB)
  @Test
  void everyNotificationHasNoOpDefault() {
    ReviewListener listener = new ReviewListener() {};

    assertThatCode(
            () -> {
              listener.modeDecided(new ReviewStats(ReviewMode.SPLIT, 812, 400, 3));
              listener.chunkStarted(1, 3);
              listener.costThresholdExceeded(new BigDecimal("0.61"), new BigDecimal("0.50"), 2);
            })
        .doesNotThrowAnyException();
  }
}
