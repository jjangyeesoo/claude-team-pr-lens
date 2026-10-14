package com.prlens.common.api;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 리뷰 요청 생성 본문. webhook이 붙기 전까지 수동으로 리뷰를 걸 때 쓴다. */
public record CreateReviewRequest(
    @JsonProperty("pr_url") String prUrl,
    @JsonProperty("requested_by") String requestedBy,
    @JsonProperty("dry_run") boolean dryRun) {}
