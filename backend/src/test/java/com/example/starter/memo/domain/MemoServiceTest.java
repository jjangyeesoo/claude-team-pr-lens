package com.example.starter.memo.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MemoServiceTest {

  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
  private final MemoService memoService = new MemoService(fixedClock);

  @Test
  void createAssignsSequentialIdsAndTimestamp() {
    Memo first = memoService.create("first", "a");
    Memo second = memoService.create("second", "b");

    assertThat(first.id()).isEqualTo(1L);
    assertThat(second.id()).isEqualTo(2L);
    assertThat(first.createdAt()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void getThrowsWhenMemoDoesNotExist() {
    assertThatThrownBy(() -> memoService.get(99L)).isInstanceOf(MemoNotFoundException.class);
  }

  @Test
  void listReturnsMemosInIdOrder() {
    memoService.create("first", null);
    memoService.create("second", null);

    assertThat(memoService.list()).extracting(Memo::title).containsExactly("first", "second");
  }
}
