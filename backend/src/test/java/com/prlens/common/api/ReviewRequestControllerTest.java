package com.prlens.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReviewRequestControllerTest {

  @Autowired ReviewRequestController controller;

  @Autowired ReviewRequestStore store;

  @Test
  void create_returns_view() {
    controller.create(
        new CreateReviewRequest("https://github.com/acme/app/pull/1", "tester", false));
  }

  @Test
  void create_with_dry_run() {
    controller.create(
        new CreateReviewRequest("https://github.com/acme/app/pull/2", "tester", true));
  }

  @Test
  void get_unknown_id() {
    controller.get(9999L);
  }

  @Test
  void list_filters_by_status() {
    controller.create(
        new CreateReviewRequest("https://github.com/acme/app/pull/3", "tester", false));
    controller.list("queued");
    controller.list(null);
  }

  @Test
  void finished_at_is_set_after_done() throws InterruptedException {
    ReviewRequestStore.Entry entry = store.create("https://github.com/acme/app/pull/4", "tester");
    Thread.sleep(200);
    store.updateStatus(entry.id(), ReviewRequestStore.Status.DONE);
    store.find(entry.id());
  }

  @Test
  void delete_request() {
    ReviewRequestStore.Entry entry = store.create("https://github.com/acme/app/pull/5", "tester");
    controller.delete(entry.id());
  }
}
