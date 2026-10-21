package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.prlens.github.HttpGitHubClient;
import com.prlens.github.PatCredentials;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ContextFile;
import com.prlens.model.FileStatus;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ReviewContext;
import com.prlens.model.Revision;
import com.prlens.pullrequest.PrFetcher;
import com.prlens.support.NonRetryableApiException;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import com.prlens.support.SecretMasker;
import com.prlens.support.Warning;
import com.prlens.testkit.FakeGitHubServer;
import com.prlens.testkit.FakeGitHubServer.Recorded;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@code PrFetcher}와 컨텍스트 부품을 실제 {@link HttpGitHubClient}로 가짜 서버에 조립한 통합 테스트 (요구사항 1.7, 4.1, 4.9).
 */
class BaseContextServerTest {

  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final String PATCH = "@@ -1 +1 @@\\n-a\\n+b";

  private final FakeGitHubServer server = new FakeGitHubServer();
  private final List<Warning> warnings = new ArrayList<>();

  @AfterEach
  void closeServer() {
    server.close();
  }

  private HttpGitHubClient client() {
    return new HttpGitHubClient(
        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(),
        new PatCredentials("fake-token-value"),
        new RetryExecutor(new RetryPolicy(0), duration -> {}, (a, n, m, s, w) -> {}),
        new HttpGitHubClient.Options(
            server.baseUri(), Clock.systemUTC(), new SecretMasker(List.of()), null));
  }

  private static String meta(String base, String head, int changedFiles) {
    return "{\"title\":\"t\",\"body\":\"b\",\"base\":{\"sha\":\""
        + base
        + "\"},\"head\":{\"sha\":\""
        + head
        + "\"},\"changed_files\":"
        + changedFiles
        + "}";
  }

  private static String fileEntry(String filename) {
    return "{\"filename\":\""
        + filename
        + "\",\"status\":\"modified\",\"additions\":1,\"deletions\":1,\"patch\":\""
        + PATCH
        + "\"}";
  }

  private static String contents(String text) {
    String encoded = Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    return "{\"type\":\"file\",\"encoding\":\"base64\",\"content\":\"" + encoded + "\"}";
  }

  private static String tree(boolean truncated, String... blobAndTreeEntries) {
    return "{\"tree\":["
        + String.join(",", blobAndTreeEntries)
        + "],\"truncated\":"
        + truncated
        + "}";
  }

  private static String blob(String path) {
    return "{\"path\":\"" + path + "\",\"type\":\"blob\"}";
  }

  private static String dir(String path) {
    return "{\"path\":\"" + path + "\",\"type\":\"tree\"}";
  }

  private List<ContextFile> collectClaudeMd(PullRequestSnapshot snapshot) {
    HttpGitHubClient client = client();
    RepoTreeIndex index = RepoTreeIndex.load(client, REPO, snapshot.baseSha());
    BaseFileReader reader =
        new BaseFileReader(client, REPO, snapshot.baseSha(), index, warnings::add);
    return new ClaudeMdCollector(reader).collect(snapshot.files());
  }

  private List<String> contentsAndTreeRequests() {
    return server.requests().stream()
        .map(Recorded::pathAndQuery)
        .filter(path -> path.contains("/contents/") || path.contains("/git/trees/"))
        .toList();
  }

  @Test
  void prFetcherFollowsLinkPagesOverHttp() {
    String next =
        "<" + server.baseUri() + "repos/o/r/pulls/5/files?per_page=100&page=2>; rel=\"next\"";
    server
        .respond("/repos/o/r/pulls/5", 200, meta("base111", "head222", 3))
        .respond(
            "/repos/o/r/pulls/5/files?per_page=100&page=1",
            200,
            "[" + fileEntry("a.txt") + "," + fileEntry("b.txt") + "]",
            "Link",
            next)
        .respond(
            "/repos/o/r/pulls/5/files?per_page=100&page=2", 200, "[" + fileEntry("c.txt") + "]");

    PullRequestSnapshot snapshot = new PrFetcher(client(), warnings::add).fetch(REPO, 5);

    assertThat(snapshot.reportedChangedFiles()).isEqualTo(3);
    assertThat(snapshot.files())
        .extracting(file -> file.path())
        .containsExactly("a.txt", "b.txt", "c.txt");
    assertThat(server.requests())
        .extracting(Recorded::pathAndQuery)
        .contains(
            "/repos/o/r/pulls/5/files?per_page=100&page=1",
            "/repos/o/r/pulls/5/files?per_page=100&page=2");
    assertThat(warnings).isEmpty();
  }

  @Test
  void claudeMdIsReadAtBaseShaNotHeadSha() {
    server
        .respond("/repos/o/r/pulls/5", 200, meta("base111", "head222", 1))
        .respond(
            "/repos/o/r/pulls/5/files?per_page=100&page=1",
            200,
            "[" + fileEntry("backend/A.java") + "]")
        .respond(
            "/repos/o/r/git/trees/base111?recursive=1",
            200,
            tree(false, blob("CLAUDE.md"), dir("backend"), blob("backend/CLAUDE.md")))
        .respond(
            "/repos/o/r/git/trees/head222?recursive=1",
            200,
            tree(false, blob("CLAUDE.md"), dir("backend"), blob("backend/CLAUDE.md")))
        .respond("/repos/o/r/contents/CLAUDE.md?ref=base111", 200, contents("root base"))
        .respond("/repos/o/r/contents/backend/CLAUDE.md?ref=base111", 200, contents("backend base"))
        .respond("/repos/o/r/contents/CLAUDE.md?ref=head222", 200, contents("root head"))
        .respond(
            "/repos/o/r/contents/backend/CLAUDE.md?ref=head222", 200, contents("backend head"));
    PullRequestSnapshot snapshot = new PrFetcher(client(), warnings::add).fetch(REPO, 5);

    List<ContextFile> files = collectClaudeMd(snapshot);

    assertThat(files)
        .extracting(ContextFile::path, ContextFile::content)
        .containsExactly(
            tuple("CLAUDE.md", "root base"), tuple("backend/CLAUDE.md", "backend base"));
    assertThat(contentsAndTreeRequests())
        .isNotEmpty()
        .allSatisfy(path -> assertThat(path).contains("base111").doesNotContain("head222"));
  }

  @Test
  void absentPathsInTreeAreNotRequested() {
    server
        .respond("/repos/o/r/pulls/5", 200, meta("base111", "head222", 1))
        .respond(
            "/repos/o/r/pulls/5/files?per_page=100&page=1",
            200,
            "[" + fileEntry("backend/src/A.java") + "]")
        .respond("/repos/o/r/git/trees/base111?recursive=1", 200, tree(false, blob("CLAUDE.md")))
        .respond("/repos/o/r/contents/CLAUDE.md?ref=base111", 200, contents("root"));
    PullRequestSnapshot snapshot = new PrFetcher(client(), warnings::add).fetch(REPO, 5);

    List<ContextFile> files = collectClaudeMd(snapshot);

    assertThat(files).extracting(ContextFile::path).containsExactly("CLAUDE.md");
    assertThat(contentsAndTreeRequests())
        .containsExactly(
            "/repos/o/r/git/trees/base111?recursive=1",
            "/repos/o/r/contents/CLAUDE.md?ref=base111");
    assertThat(warnings).isEmpty();
  }

  @Test
  void truncatedTreeFallsBackToPerPathContentsAndSkipsRules() {
    server
        .respond("/repos/o/r/pulls/5", 200, meta("base111", "head222", 1))
        .respond(
            "/repos/o/r/pulls/5/files?per_page=100&page=1",
            200,
            "[" + fileEntry("backend/src/A.java") + "]")
        .respond("/repos/o/r/git/trees/base111?recursive=1", 200, tree(true))
        .respond("/repos/o/r/contents/CLAUDE.md?ref=base111", 200, contents("root"))
        .respond("/repos/o/r/contents/backend/CLAUDE.md?ref=base111", 200, contents("backend"));
    PullRequestSnapshot snapshot = new PrFetcher(client(), warnings::add).fetch(REPO, 5);
    HttpGitHubClient client = client();
    RepoTreeIndex index = RepoTreeIndex.load(client, REPO, snapshot.baseSha());
    BaseFileReader reader =
        new BaseFileReader(client, REPO, snapshot.baseSha(), index, warnings::add);

    List<ContextFile> claudeMd = new ClaudeMdCollector(reader).collect(snapshot.files());
    List<ContextFile> rules =
        new RuleCollector(index, reader, warnings::add).collect(snapshot.files());

    assertThat(claudeMd)
        .extracting(ContextFile::path)
        .containsExactly("CLAUDE.md", "backend/CLAUDE.md");
    assertThat(rules).isEmpty();
    assertThat(contentsAndTreeRequests())
        .contains(
            "/repos/o/r/contents/CLAUDE.md?ref=base111",
            "/repos/o/r/contents/.claude/CLAUDE.md?ref=base111",
            "/repos/o/r/contents/backend/CLAUDE.md?ref=base111",
            "/repos/o/r/contents/backend/src/CLAUDE.md?ref=base111")
        .noneMatch(path -> path.contains("/contents/.claude/rules"));
    assertThat(warnings).extracting(Warning::code).containsExactly("rules_skipped_tree_truncated");
  }

  private ReviewContext collectContext(String body) {
    PullRequestSnapshot snapshot =
        new PullRequestSnapshot(
            REPO,
            5,
            "t",
            body,
            "base111",
            "head222",
            1,
            List.of(
                new ChangedFile(
                    "backend/A.java",
                    null,
                    FileStatus.MODIFIED,
                    1,
                    1,
                    new PatchContent.Parsed(List.of()))));
    return new ContextCollector(client(), warnings::add)
        .collect(snapshot, Configuration.defaults());
  }

  @Test
  void contextCollectorReadsAtBaseShaAndFallsBackToHeadOnlyForSpec() {
    server
        .respond("/repos/o/r/git/trees/base111?recursive=1", 200, tree(false, blob("CLAUDE.md")))
        .respond("/repos/o/r/contents/CLAUDE.md?ref=base111", 200, contents("root base"))
        .respond("/repos/o/r/contents/docs/specs/new.md?ref=head222", 200, contents("head spec"));

    ReviewContext context = collectContext("docs/specs/new.md");

    assertThat(contentsAndTreeRequests())
        .filteredOn(path -> !path.contains("docs/specs/new.md"))
        .isNotEmpty()
        .allSatisfy(path -> assertThat(path).contains("base111").doesNotContain("head222"));
    assertThat(contentsAndTreeRequests())
        .contains("/repos/o/r/contents/docs/specs/new.md?ref=head222");
    assertThat(context.files())
        .anySatisfy(
            file -> {
              assertThat(file.path()).isEqualTo("docs/specs/new.md");
              assertThat(file.revision()).isEqualTo(Revision.HEAD);
            });
  }

  @Test
  void contextCollectorPropagatesForbiddenFromContents() {
    server
        .respond("/repos/o/r/git/trees/base111?recursive=1", 200, tree(false, blob("CLAUDE.md")))
        .respond("/repos/o/r/contents/CLAUDE.md?ref=base111", 403, "{\"message\":\"forbidden\"}");

    assertThatThrownBy(() -> collectContext("")).isInstanceOf(NonRetryableApiException.class);
  }

  @Test
  void contextCollectorPropagatesUnauthorizedFromTree() {
    server.respond("/repos/o/r/git/trees/base111?recursive=1", 401, "{\"message\":\"bad\"}");

    assertThatThrownBy(() -> collectContext("")).isInstanceOf(NonRetryableApiException.class);
  }
}
