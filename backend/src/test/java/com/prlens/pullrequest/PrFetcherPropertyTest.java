package com.prlens.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.prlens.github.FilePage;
import com.prlens.github.FilePage.Entry;
import com.prlens.github.PullRequestMeta;
import com.prlens.model.ChangedFile;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** {@link PrFetcher#fetch}의 파일 목록 조회 속성 테스트 (design.md Property 2). */
class PrFetcherPropertyTest {

  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final int PAGE_SIZE = 100;

  /**
   * @param emptyPageSlots 빈 페이지를 끼워 넣을 자리(0부터 센 페이지 앞). 첫 페이지 앞과 마지막 페이지 뒤에는 넣지 않는다
   */
  record Scenario(List<String> names, List<Integer> emptyPageSlots, int changedFiles) {
    List<FilePage> pages() {
      List<Entry> entries =
          IntStream.range(0, names.size())
              .mapToObj(i -> new Entry(names.get(i), null, "modified", i, 0, null))
              .toList();
      List<List<Entry>> chunks = new ArrayList<>();
      for (int from = 0; from < entries.size(); from += PAGE_SIZE) {
        chunks.add(entries.subList(from, Math.min(from + PAGE_SIZE, entries.size())));
      }
      if (chunks.isEmpty()) {
        return List.of(new FilePage(List.of(), false));
      }
      List<FilePage> pages = new ArrayList<>();
      for (int i = 0; i < chunks.size(); i++) {
        if (i > 0 && emptyPageSlots.contains(i)) {
          pages.add(new FilePage(List.of(), true));
        }
        pages.add(new FilePage(chunks.get(i), i < chunks.size() - 1));
      }
      return pages;
    }
  }

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<List<String>> names =
        Arbitraries.integers().between(0, 39).map(i -> "f" + i).list().ofMinSize(0).ofMaxSize(250);
    Arbitrary<List<Integer>> slots =
        Arbitraries.oneOf(
            Arbitraries.just(List.of()), Arbitraries.integers().between(1, 2).list().ofMaxSize(2));
    Arbitrary<Integer> changedFiles = Arbitraries.integers().between(0, 300);
    return Combinators.combine(names, slots, changedFiles).as(Scenario::new);
  }

  // Feature: pr-lens-p1-cli, Property 2: 파일 목록 조회의 순서·중복·개수
  @Property(tries = 200)
  void fileListKeepsOrderDropsDuplicatesAndReportedCount(@ForAll("scenarios") Scenario scenario) {
    List<FilePage> pages = scenario.pages();
    FakeGitHubClient github =
        new FakeGitHubClient(
            new PullRequestMeta("t", null, "b", "h", scenario.changedFiles()), pages);

    PullRequestSnapshot snapshot = new PrFetcher(github, warning -> {}).fetch(REPO, 1);

    List<Integer> requested = github.requestedPages();
    // (4) 요청 페이지는 1부터 연속이고, 중단 조건은 마지막 요청 페이지에서 처음 성립한다
    assertThat(requested).isNotEmpty();
    assertThat(requested)
        .containsExactlyElementsOf(IntStream.rangeClosed(1, requested.size()).boxed().toList());
    Map<String, Entry> firstByPath = new LinkedHashMap<>();
    for (int page : requested) {
      FilePage filePage =
          page <= pages.size() ? pages.get(page - 1) : new FilePage(List.of(), false);
      for (Entry entry : filePage.files()) {
        firstByPath.putIfAbsent(entry.filename(), entry);
      }
      boolean stop =
          firstByPath.size() >= scenario.changedFiles()
              || filePage.files().isEmpty()
              || !filePage.hasNext();
      assertThat(stop).as("page %d stop condition", page).isEqualTo(page == requested.size());
    }

    // (1) 요청된 페이지 항목을 이은 목록에서 경로별 첫 항목만 남긴 것과 순서까지 같다
    assertThat(snapshot.files())
        .extracting(ChangedFile::path, ChangedFile::additions)
        .containsExactlyElementsOf(
            firstByPath.values().stream().map(e -> tuple(e.filename(), e.additions())).toList());
    // (2) 경로 중복 없음
    List<String> paths = snapshot.files().stream().map(ChangedFile::path).toList();
    assertThat(paths).doesNotHaveDuplicates();
    // (3) 메타 changedFiles를 그대로 담는다
    assertThat(snapshot.reportedChangedFiles()).isEqualTo(scenario.changedFiles());
  }
}
