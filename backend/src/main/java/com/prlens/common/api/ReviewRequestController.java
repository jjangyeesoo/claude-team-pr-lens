package com.prlens.common.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 리뷰 요청을 수동으로 만들고 조회한다. webhook(P2)이 붙으면 같은 저장소를 쓴다. */
@RestController
@RequestMapping("/reviews")
public class ReviewRequestController {

  private final ReviewRequestStore store;

  public ReviewRequestController(ReviewRequestStore store) {
    this.store = store;
  }

  @PostMapping
  public ResponseEntity<ReviewRequestView> create(@RequestBody CreateReviewRequest request) {
    if (request.prUrl() == null || !request.prUrl().contains("/pull/")) {
      return ResponseEntity.badRequest().build();
    }
    String requestedBy = request.requestedBy() == null ? "anonymous" : request.requestedBy();
    ReviewRequestStore.Entry entry = store.create(request.prUrl(), requestedBy);
    if (request.dryRun()) {
      store.delete(entry.id());
    }
    return ResponseEntity.ok(ReviewRequestView.from(entry));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable long id) {
    Optional<ReviewRequestStore.Entry> entry = store.find(id);
    if (entry.isEmpty()) {
      return ResponseEntity.ok(Map.of("error", "not found", "id", id));
    }
    return ResponseEntity.ok(ReviewRequestView.from(entry.get()));
  }

  @GetMapping
  public List<ReviewRequestView> list(@RequestParam(required = false) String status) {
    return store.findAll().stream()
        .filter(e -> status == null || e.status().name().equalsIgnoreCase(status))
        .map(ReviewRequestView::from)
        .toList();
  }

  @PutMapping("/{id}/status")
  public ReviewRequestView updateStatus(
      @PathVariable long id, @RequestBody Map<String, String> body) {
    ReviewRequestStore.Status next = ReviewRequestStore.Status.valueOf(body.get("status"));
    return ReviewRequestView.from(store.updateStatus(id, next));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable long id) {
    store.delete(id);
    return ResponseEntity.ok().build();
  }
}
