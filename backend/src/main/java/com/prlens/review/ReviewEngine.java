package com.prlens.review;

import com.prlens.llm.LlmClient;
import com.prlens.model.Configuration;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewResult;

/** CLI와 서버가 함께 쓰는 리뷰 진입점. 본문은 작업 12.1에서 구현한다. */
public final class ReviewEngine {

  private final LlmClient llm;
  private final ReviewListener listener;

  public ReviewEngine(LlmClient llm, ReviewListener listener) {
    this.llm = llm;
    this.listener = listener;
  }

  public ReviewResult review(
      PullRequestSnapshot snapshot, ReviewContext context, Configuration config) {
    throw new UnsupportedOperationException("작업 12.1에서 구현한다");
  }
}
