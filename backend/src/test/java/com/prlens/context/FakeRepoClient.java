package com.prlens.context;

import com.prlens.github.FileFetch;
import com.prlens.github.FilePage;
import com.prlens.github.GitHubClient;
import com.prlens.github.PullRequestMeta;
import com.prlens.github.RepoTree;
import com.prlens.model.RepoRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SHA별 트리와 파일 응답을 돌려주고 요청을 기록하는 테스트용 {@link GitHubClient}. 등록하지 않은 파일은 {@link FileFetch.NotFound},
 * 등록하지 않은 트리는 빈 트리다. PR 조회는 쓰지 않는다.
 */
final class FakeRepoClient implements GitHubClient {

  /** {@link #getFile} 요청 하나. */
  record FileRequest(String path, String sha) {}

  private final Map<String, RepoTree> trees = new HashMap<>();
  private final Map<FileRequest, FileFetch> files = new HashMap<>();
  private final List<String> treeRequests = new ArrayList<>();
  private final List<FileRequest> fileRequests = new ArrayList<>();

  FakeRepoClient tree(String sha, RepoTree tree) {
    trees.put(sha, tree);
    return this;
  }

  FakeRepoClient file(String sha, String path, String content) {
    files.put(new FileRequest(path, sha), new FileFetch.Found(content));
    return this;
  }

  FakeRepoClient tooLarge(String sha, String path) {
    files.put(new FileRequest(path, sha), new FileFetch.TooLarge());
    return this;
  }

  FakeRepoClient directory(String sha, String path) {
    files.put(new FileRequest(path, sha), new FileFetch.IsDirectory());
    return this;
  }

  List<String> treeRequests() {
    return List.copyOf(treeRequests);
  }

  List<FileRequest> fileRequests() {
    return List.copyOf(fileRequests);
  }

  @Override
  public PullRequestMeta getPullRequest(RepoRef repo, int number) {
    throw new UnsupportedOperationException("컨텍스트 수집은 PR을 조회하지 않는다");
  }

  @Override
  public FilePage listFiles(RepoRef repo, int number, int page) {
    throw new UnsupportedOperationException("컨텍스트 수집은 파일 목록을 조회하지 않는다");
  }

  @Override
  public RepoTree getTree(RepoRef repo, String sha) {
    treeRequests.add(sha);
    return trees.getOrDefault(sha, new RepoTree(List.of(), false));
  }

  @Override
  public FileFetch getFile(RepoRef repo, String path, String sha) {
    FileRequest request = new FileRequest(path, sha);
    fileRequests.add(request);
    return files.getOrDefault(request, new FileFetch.NotFound());
  }
}
