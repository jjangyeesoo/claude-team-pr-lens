package com.prlens.pullrequest;

import com.prlens.github.FileFetch;
import com.prlens.github.FilePage;
import com.prlens.github.GitHubClient;
import com.prlens.github.PullRequestMeta;
import com.prlens.github.RepoTree;
import com.prlens.model.RepoRef;
import java.util.ArrayList;
import java.util.List;

/** 메타 한 건과 페이지 목록을 돌려주고 요청된 페이지 번호를 기록하는 테스트용 {@link GitHubClient}. */
final class FakeGitHubClient implements GitHubClient {

  private final PullRequestMeta meta;
  private final List<FilePage> pages;
  private final List<Integer> requestedPages = new ArrayList<>();

  FakeGitHubClient(PullRequestMeta meta, List<FilePage> pages) {
    this.meta = meta;
    this.pages = List.copyOf(pages);
  }

  List<Integer> requestedPages() {
    return List.copyOf(requestedPages);
  }

  @Override
  public PullRequestMeta getPullRequest(RepoRef repo, int number) {
    return meta;
  }

  /** 목록에 없는 페이지는 {@code FilePage(List.of(), false)}다. */
  @Override
  public FilePage listFiles(RepoRef repo, int number, int page) {
    requestedPages.add(page);
    if (page < 1 || page > pages.size()) {
      return new FilePage(List.of(), false);
    }
    return pages.get(page - 1);
  }

  @Override
  public RepoTree getTree(RepoRef repo, String sha) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FileFetch getFile(RepoRef repo, String path, String sha) {
    throw new UnsupportedOperationException();
  }
}
