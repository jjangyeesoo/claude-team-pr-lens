package com.prlens.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReviewRequestStatsTest {

  @Autowired ReviewRequestStats stats;

  @Autowired ReviewRequestStore store;

  @Test
  void stats_counts_finished_requests() throws InterruptedException {
    ReviewRequestStore.Entry entry = store.create("https://github.com/acme/app/pull/10", "tester");
    Thread.sleep(100);
    store.updateStatus(entry.id(), ReviewRequestStore.Status.DONE);
    stats.stats();
  }

  @Test
  void stats_after_failure() {
    ReviewRequestStore.Entry entry = store.create("https://github.com/acme/app/pull/11", "tester");
    store.updateStatus(entry.id(), ReviewRequestStore.Status.FAILED);
    stats.stats();
  }
}
