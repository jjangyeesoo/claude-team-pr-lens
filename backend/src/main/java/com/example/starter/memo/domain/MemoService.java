package com.example.starter.memo.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/** 샘플용 인메모리 메모 저장소. 실제 프로젝트에서는 Repository 계층으로 교체한다. */
@Service
public class MemoService {

  private final Map<Long, Memo> memos = new ConcurrentHashMap<>();
  private final AtomicLong sequence = new AtomicLong();
  private final Clock clock;

  public MemoService(Clock clock) {
    this.clock = clock;
  }

  public Memo create(String title, String content) {
    long id = sequence.incrementAndGet();
    Memo memo = new Memo(id, title, content, Instant.now(clock));
    memos.put(id, memo);
    return memo;
  }

  public Memo get(long id) {
    Memo memo = memos.get(id);
    if (memo == null) {
      throw new MemoNotFoundException(id);
    }
    return memo;
  }

  public List<Memo> list() {
    return memos.values().stream().sorted(Comparator.comparingLong(Memo::id)).toList();
  }
}
