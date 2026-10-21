package com.prlens.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.model.ChangedFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.pullrequest.PrFetcher;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import com.prlens.support.Warning;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 GitHub에 대한 수동 스모크 (작업 7.7). 고정 픽스처 PR을 쓴다(docs/spikes/2026-10-14-fixture-pr.md). {@code
 * GITHUB_TOKEN}과 {@code PRLENS_SMOKE=1}이 모두 있을 때만 돈다. CI에서는 돌지 않는다.
 *
 * <p>실행: {@code PRLENS_SMOKE=1 ./gradlew test --tests com.prlens.github.GitHubSmokeTest}
 */
@EnabledIfEnvironmentVariable(named = "GITHUB_TOKEN", matches = ".*\\S.*")
@EnabledIfEnvironmentVariable(named = "PRLENS_SMOKE", matches = "1")
class GitHubSmokeTest {

  private static final RepoRef REPO = new RepoRef("jjangyeesoo", "claude-team-pr-lens");
  private static final int PR = 15;
  private static final long ONE_MB = 1024L * 1024L;
  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final List<Warning> WARNINGS = new ArrayList<>();

  private static GitHubClient client;
  private static String token;
  private static PullRequestSnapshot snapshot;

  @BeforeAll
  static void connect() {
    token = System.getenv("GITHUB_TOKEN");
    RetryExecutor retry = new RetryExecutor(new RetryPolicy(0), d -> {}, (a, n, m, s, w) -> {});
    client =
        new HttpGitHubClient(
            HTTP, new PatCredentials(token), retry, HttpGitHubClient.Options.defaults());
    snapshot = new PrFetcher(client, WARNINGS::add).fetch(REPO, PR);
  }

  private static ChangedFile file(String path) {
    return snapshot.files().stream()
        .filter(f -> f.path().equals(path))
        .findFirst()
        .orElseThrow(() -> new AssertionError("파일 없음: " + path));
  }

  private static JsonNode rawBaseTree() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "https://api.github.com/repos/jjangyeesoo/claude-team-pr-lens/git/trees/"
                        + snapshot.baseSha()
                        + "?recursive=1"))
            .header("Authorization", "Bearer " + token)
            .header("Accept", "application/vnd.github+json")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as("원시 트리 응답 상태").isEqualTo(200);
    return MAPPER.readTree(response.body()).get("tree");
  }

  private static String firstPath(JsonNode tree, Predicate<JsonNode> match) {
    for (JsonNode entry : tree) {
      if (match.test(entry)) {
        return entry.get("path").asString();
      }
    }
    return null;
  }

  @Test
  void fixturePrSnapshotHasMetaAndAllFiles() {
    assertThat(snapshot.title()).isNotBlank();
    assertThat(snapshot.baseSha()).hasSize(40);
    assertThat(snapshot.headSha()).hasSize(40);
    assertThat(snapshot.reportedChangedFiles()).isEqualTo(12);
    assertThat(snapshot.files()).hasSize(12);
    assertThat(WARNINGS).isEmpty();
  }

  @Test
  void fixturePrFilesHaveExpectedStatusesAndPatches() {
    Set<FileStatus> statuses = EnumSet.noneOf(FileStatus.class);
    snapshot.files().forEach(f -> statuses.add(f.status()));
    assertThat(statuses)
        .containsExactlyInAnyOrder(
            FileStatus.ADDED, FileStatus.MODIFIED, FileStatus.REMOVED, FileStatus.RENAMED);
    assertThat(snapshot.files()).noneMatch(f -> f.patch() instanceof PatchContent.Unparseable);

    ChangedFile renamed = file("docs/spec-review/06-p3-web-requirements.md");
    assertThat(renamed.status()).isEqualTo(FileStatus.RENAMED);
    assertThat(renamed.previousPath()).isEqualTo("docs/spec-review/06-p3-requirements.md");

    List<String> absent =
        snapshot.files().stream()
            .filter(f -> f.patch() instanceof PatchContent.Absent)
            .map(ChangedFile::path)
            .toList();
    assertThat(absent)
        .containsExactlyInAnyOrder(
            "docs/images/review-request-flow.png", "docs/spec-review/06-p3-web-requirements.md");

    assertThat(snapshot.files().stream().mapToInt(ChangedFile::additions).sum()).isEqualTo(326);
    assertThat(snapshot.files().stream().mapToInt(ChangedFile::deletions).sum()).isEqualTo(77);
  }

  @Test
  void baseTreeIsRecursiveAndUntruncated() {
    RepoTree tree = client.getTree(REPO, snapshot.baseSha());
    assertThat(tree.truncated()).isFalse();
    assertThat(tree.entries())
        .contains(
            new RepoTree.Entry("CLAUDE.md", RepoTree.Kind.BLOB),
            new RepoTree.Entry(".claude/rules/backend/testing.md", RepoTree.Kind.BLOB),
            new RepoTree.Entry("backend", RepoTree.Kind.TREE));
  }

  @Test
  void rawTreeEntriesAlwaysHavePathAndKnownType() throws Exception {
    JsonNode tree = rawBaseTree();
    assertThat(tree.size()).isPositive();
    for (JsonNode entry : tree) {
      assertThat(entry.hasNonNull("path")).as("path 없음: %s", entry).isTrue();
      assertThat(entry.path("type").asString())
          .as("알 수 없는 type (path=%s)", entry.path("path").asString())
          .isIn("blob", "tree", "commit");
    }
  }

  @Test
  void contentsAtBaseShaReturnFileDirectoryAndNotFound() {
    String sha = snapshot.baseSha();
    FileFetch claudeMd = client.getFile(REPO, "CLAUDE.md", sha);
    assertThat(claudeMd).isInstanceOf(FileFetch.Found.class);
    assertThat(((FileFetch.Found) claudeMd).content()).isNotBlank();
    assertThat(client.getFile(REPO, "backend", sha)).isInstanceOf(FileFetch.IsDirectory.class);
    assertThat(client.getFile(REPO, "no/such/path-for-smoke.md", sha))
        .isInstanceOf(FileFetch.NotFound.class);
  }

  @Test
  void treeOfUnknownShaThrowsNotFound() {
    assertThatThrownBy(() -> client.getTree(REPO, "0".repeat(40)))
        .isInstanceOfSatisfying(
            GitHubApiException.class, e -> assertThat(e.status()).isEqualTo(404));
  }

  @Test
  void largeFileMapsToTooLarge() throws Exception {
    String path =
        firstPath(
            rawBaseTree(),
            e -> "blob".equals(e.path("type").asString()) && e.path("size").asLong() > ONE_MB);
    Assumptions.assumeTrue(path != null, "base 트리에 1MB 초과 blob 없음");
    assertThat(client.getFile(REPO, path, snapshot.baseSha()))
        .isInstanceOf(FileFetch.TooLarge.class);
  }

  @Test
  void symlinkMapsToNotFound() throws Exception {
    String path = firstPath(rawBaseTree(), e -> "120000".equals(e.path("mode").asString()));
    Assumptions.assumeTrue(path != null, "base 트리에 symlink 없음");
    assertThat(client.getFile(REPO, path, snapshot.baseSha()))
        .isInstanceOf(FileFetch.NotFound.class);
  }

  @Test
  void submoduleMapsToIsDirectory() throws Exception {
    String path = firstPath(rawBaseTree(), e -> "commit".equals(e.path("type").asString()));
    Assumptions.assumeTrue(path != null, "base 트리에 submodule 없음");
    assertThat(client.getFile(REPO, path, snapshot.baseSha()))
        .isInstanceOf(FileFetch.IsDirectory.class);
  }
}
