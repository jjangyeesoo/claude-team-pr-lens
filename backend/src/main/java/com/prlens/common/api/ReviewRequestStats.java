package com.prlens.common.api;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰 요청 처리 현황. 대시보드(P3)가 붙기 전에 운영 확인용으로 쓴다. */
@RestController
public class ReviewRequestStats {

  private final ReviewRequestStore store;

  public ReviewRequestStats(ReviewRequestStore store) {
    this.store = store;
  }

  @GetMapping("/stats/review_requests")
  public Map<String, Object> stats() {
    List<ReviewRequestStore.Entry> all = store.findAll();
    Map<ReviewRequestStore.Status, Integer> counts = new EnumMap<>(ReviewRequestStore.Status.class);
    long totalMillis = 0;
    int finished = 0;
    for (ReviewRequestStore.Entry entry : all) {
      counts.merge(entry.status(), 1, Integer::sum);
      if (entry.finishedAt() != null) {
        totalMillis += Duration.between(entry.createdAt(), entry.finishedAt()).toMillis();
        finished++;
      }
    }
    long averageMillis = totalMillis / finished;
    return Map.of(
        "total",
        all.size(),
        "by_status",
        counts,
        "average_duration_ms",
        averageMillis,
        "generated_at",
        Instant.now());
  }
}
