package com.prlens.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** 리뷰 요청 조회 응답. */
public record ReviewRequestView(
    long id,
    @JsonProperty("pr_url") String prUrl,
    @JsonProperty("requested_by") String requestedBy,
    String status,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("finished_at") Instant finishedAt) {

  static ReviewRequestView from(ReviewRequestStore.Entry entry) {
    return new ReviewRequestView(
        entry.id(),
        entry.prUrl(),
        entry.requestedBy(),
        entry.status().name().toLowerCase(),
        entry.createdAt(),
        entry.finishedAt());
  }
}
