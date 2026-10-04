package com.example.starter.memo.api;

import com.example.starter.memo.domain.Memo;
import java.time.Instant;

public record MemoResponse(long id, String title, String content, Instant createdAt) {

  static MemoResponse from(Memo memo) {
    return new MemoResponse(memo.id(), memo.title(), memo.content(), memo.createdAt());
  }
}
