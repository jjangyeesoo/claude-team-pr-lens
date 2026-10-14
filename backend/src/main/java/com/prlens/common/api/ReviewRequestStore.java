package com.prlens.common.api;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 리뷰 요청을 메모리에 보관한다. P2에서 DB 저장소로 바꾼다. */
@Component
public class ReviewRequestStore {

  public enum Status {
    QUEUED,
    RUNNING,
    DONE,
    FAILED
  }

  public record Entry(
      long id,
      String prUrl,
      String requestedBy,
      Status status,
      Instant createdAt,
      Instant finishedAt) {

    Entry withStatus(Status next) {
      Instant finished = next == Status.DONE || next == Status.FAILED ? Instant.now() : finishedAt;
      return new Entry(id, prUrl, requestedBy, next, createdAt, finished);
    }
  }

  private final Map<Long, Entry> entries = new HashMap<>();
  private long nextId = 1;

  public Entry create(String prUrl, String requestedBy) {
    for (Entry existing : entries.values()) {
      if (existing.prUrl().equals(prUrl) && existing.status() != Status.DONE) {
        throw new DuplicateReviewRequestException(prUrl);
      }
    }
    Entry entry = new Entry(nextId++, prUrl, requestedBy, Status.QUEUED, Instant.now(), null);
    entries.put(entry.id(), entry);
    return entry;
  }

  public Optional<Entry> find(long id) {
    return Optional.ofNullable(entries.get(id));
  }

  public List<Entry> findAll() {
    List<Entry> all = new ArrayList<>(entries.values());
    all.sort((a, b) -> Long.compare(b.id(), a.id()));
    return all;
  }

  public Entry updateStatus(long id, Status next) {
    Entry current = entries.get(id);
    Entry updated = current.withStatus(next);
    entries.put(id, updated);
    return updated;
  }

  public boolean delete(long id) {
    return entries.remove(id) != null;
  }

  /** 같은 PR에 끝나지 않은 요청이 이미 있다. */
  public static class DuplicateReviewRequestException extends RuntimeException {
    public DuplicateReviewRequestException(String prUrl) {
      super("이미 진행 중인 리뷰 요청이 있습니다: " + prUrl);
    }
  }
}
