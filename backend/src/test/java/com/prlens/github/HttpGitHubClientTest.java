package com.prlens.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.model.RepoRef;
import com.prlens.support.NonRetryableApiException;
import com.prlens.support.RetriesExhaustedException;
import com.prlens.support.RetryAfterTooLongException;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import com.prlens.support.SecretMasker;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class HttpGitHubClientTest {

  private static final Instant NOW = Instant.parse("2026-10-18T05:00:00Z");
  private static final String TOKEN = "ghp_fakeToken123";
  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final String PR_JSON =
      """
      {"title":"제목","body":null,"base":{"sha":"b1"},"head":{"sha":"h1"},"changed_files":3}
      """;

  private final FakeHttpClient http = new FakeHttpClient();
  private final List<Duration> sleeps = new ArrayList<>();
  private final List<String> retryStatuses = new ArrayList<>();
  private final List<String> retryApis = new ArrayList<>();
  private final List<String> logLines = new ArrayList<>();

  private HttpGitHubClient client() {
    return client(http, repo -> "Bearer " + TOKEN, logLines::add);
  }

  private HttpGitHubClient client(
      FakeHttpClient fake, GitHubCredentials credentials, Consumer<String> log) {
    RetryExecutor retry =
        new RetryExecutor(
            new RetryPolicy(3),
            sleeps::add,
            (api, next, max, status, wait) -> {
              retryApis.add(api);
              retryStatuses.add(status);
            });
    return new HttpGitHubClient(
        fake,
        credentials,
        retry,
        new HttpGitHubClient.Options(
            URI.create("https://api.example.test"),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SecretMasker(List.of(TOKEN)),
            log));
  }

  private PullRequestMeta fetchMeta(HttpGitHubClient client) {
    return client.getPullRequest(REPO, 7);
  }

  private static String file(String content) {
    return "{\"type\":\"file\",\"encoding\":\"base64\",\"content\":\""
        + Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8))
        + "\"}";
  }

  @Test
  void everyRequestCarriesAuthorizationFromCredentials() {
    http.respond(200, PR_JSON)
        .respond(200, "[]")
        .respond(200, "{\"tree\":[],\"truncated\":false}")
        .respond(200, file("x"));
    HttpGitHubClient client = client();

    client.getPullRequest(REPO, 7);
    client.listFiles(REPO, 7, 1);
    client.getTree(REPO, "sha");
    client.getFile(REPO, "a.txt", "sha");

    assertThat(http.requests()).hasSize(4);
    assertThat(http.requests())
        .allSatisfy(
            r -> {
              assertThat(r.headers().firstValue("Authorization")).hasValue("Bearer " + TOKEN);
              assertThat(r.headers().firstValue("Accept")).hasValue("application/vnd.github+json");
              assertThat(r.headers().firstValue("X-GitHub-Api-Version")).hasValue("2022-11-28");
              assertThat(r.headers().firstValue("User-Agent")).hasValue("prlens");
            });
  }

  @Test
  void requestsGoOnlyToApiBaseWithCredentialsForThatRepo() {
    List<RepoRef> asked = new ArrayList<>();
    http.respond(200, PR_JSON);
    HttpGitHubClient client =
        client(
            http,
            repo -> {
              asked.add(repo);
              return "Bearer x";
            },
            null);

    client.getPullRequest(REPO, 7);

    assertThat(http.requests()).hasSize(1);
    assertThat(http.requests().get(0).uri().getHost()).isEqualTo("api.example.test");
    assertThat(asked).isNotEmpty().containsOnly(REPO);
  }

  @Test
  void everyRequestHasThirtySecondTimeout() {
    http.respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(http.requests()).hasSize(1);
    assertThat(http.requests().get(0).timeout()).hasValue(Duration.ofSeconds(30));
  }

  @Test
  void unauthorizedForbiddenNotFoundThrowGitHubApiExceptionWithoutRetry() {
    String[][] cases = {
      {"401", "토큰이 잘못되었거나 만료"}, {"403", "토큰 권한 부족"}, {"404", "저장소 접근 불가 또는 PR 번호 없음"}
    };
    for (String[] c : cases) {
      int status = Integer.parseInt(c[0]);
      FakeHttpClient local = new FakeHttpClient().respond(status, "{\"message\":\"x\"}");
      HttpGitHubClient client = client(local, repo -> "Bearer " + TOKEN, null);

      assertThatThrownBy(() -> client.getPullRequest(REPO, 7))
          .isInstanceOfSatisfying(
              GitHubApiException.class,
              e -> {
                assertThat(e.status()).isEqualTo(status);
                assertThat(e.getMessage()).contains(c[0]).contains(c[1]);
              });
      assertThat(local.requests()).hasSize(1);
    }
    assertThat(sleeps).isEmpty();
  }

  @Test
  void getTreeNotFoundThrowsButGetFileNotFoundReturnsNotFound() {
    http.respond(404, "{}").respond(404, "{}");
    HttpGitHubClient client = client();

    assertThatThrownBy(() -> client.getTree(REPO, "sha"))
        .isInstanceOfSatisfying(
            GitHubApiException.class, e -> assertThat(e.status()).isEqualTo(404));
    assertThat(client.getFile(REPO, "a.txt", "sha")).isInstanceOf(FileFetch.NotFound.class);
  }

  @Test
  void otherClientErrorBecomesNonRetryableApiException() {
    http.respond(422, "{}");

    assertThatThrownBy(() -> fetchMeta(client()))
        .isInstanceOfSatisfying(
            NonRetryableApiException.class,
            e -> {
              assertThat(e.api()).isEqualTo("GitHub");
              assertThat(e.status()).isEqualTo(422);
            });
    assertThat(http.requests()).hasSize(1);
  }

  @Test
  void dotSegmentRepoIsRejectedAsNotFoundWithoutRequest() {
    assertThatThrownBy(() -> client().getPullRequest(new RepoRef("o", ".."), 7))
        .isInstanceOfSatisfying(
            GitHubApiException.class, e -> assertThat(e.status()).isEqualTo(404));
    assertThat(http.requests()).isEmpty();
  }

  @Test
  void serverErrorIsRetriedWithStatusString() {
    http.respond(503, "").respond(200, PR_JSON);

    PullRequestMeta meta = fetchMeta(client());

    assertThat(meta.title()).isEqualTo("제목");
    assertThat(retryStatuses).containsExactly("503");
    assertThat(retryApis).containsExactly("GitHub");
  }

  @Test
  void timeoutAndConnectionFailureAreRetriedWithErrorKind() {
    http.fail(new HttpTimeoutException("t"))
        .respond(200, PR_JSON)
        .fail(new ConnectException("c"))
        .respond(200, PR_JSON)
        .fail(new IOException("io"))
        .respond(200, PR_JSON);
    HttpGitHubClient client = client();

    fetchMeta(client);
    fetchMeta(client);
    fetchMeta(client);

    assertThat(retryStatuses).containsExactly("timeout", "connection_failed", "network_error");
  }

  @Test
  void exhaustedNetworkRetriesThrowRetriesExhausted() {
    for (int i = 0; i < 4; i++) {
      http.fail(new HttpTimeoutException("t"));
    }

    assertThatThrownBy(() -> fetchMeta(client()))
        .isInstanceOfSatisfying(
            RetriesExhaustedException.class,
            e -> {
              assertThat(e.api()).isEqualTo("GitHub");
              assertThat(e.lastStatus()).isEqualTo("timeout");
              assertThat(e.attempts()).isEqualTo(4);
            });
  }

  @Test
  void debugLogMasksAuthorizationHeader() {
    http.respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(logLines).isNotEmpty();
    assertThat(logLines)
        .anyMatch(l -> l.startsWith("> GET https://api.example.test/repos/o/r/pulls/7"));
    assertThat(logLines).anyMatch(l -> l.toLowerCase().contains("authorization: ***"));
    assertThat(logLines).noneMatch(l -> l.contains(TOKEN));
    assertThat(logLines).contains("< 200");
  }

  @Test
  void debugLogIsOffByDefault() {
    http.respond(200, PR_JSON);
    HttpGitHubClient client =
        new HttpGitHubClient(
            http,
            repo -> "Bearer x",
            new RetryExecutor(new RetryPolicy(0), d -> {}, (a, n, m, s, w) -> {}));

    assertThat(client.getPullRequest(REPO, 7)).isNotNull();
    assertThat(HttpGitHubClient.Options.defaults().debugLog()).isNull();
  }

  @Test
  void rateLimitWithRetryAfterWaitsHeaderSeconds() {
    http.respond(403, "", "retry-after", "5")
        .respond(200, PR_JSON)
        .respond(429, "", "retry-after", "5")
        .respond(200, PR_JSON);
    HttpGitHubClient client = client();

    fetchMeta(client);
    fetchMeta(client);

    assertThat(sleeps).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(5));
    assertThat(retryStatuses).containsExactly("403", "429");
  }

  @Test
  void retryAfterOverSixtySecondsStopsImmediately() {
    http.respond(429, "", "retry-after", "61");

    assertThatThrownBy(() -> fetchMeta(client()))
        .isInstanceOfSatisfying(
            RetryAfterTooLongException.class, e -> assertThat(e.seconds()).isEqualTo(61));
    assertThat(http.requests()).hasSize(1);
  }

  @Test
  void retryAfterTakesPrecedenceOverRateLimitReset() {
    long reset = NOW.getEpochSecond() + 30;
    http.respond(
            403,
            "",
            "retry-after",
            "3",
            "x-ratelimit-remaining",
            "0",
            "x-ratelimit-reset",
            String.valueOf(reset))
        .respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(sleeps).containsExactly(Duration.ofSeconds(3));
  }

  @Test
  void nonNumericRetryAfterWaitsSixtySeconds() {
    http.respond(403, "", "retry-after", "Wed, 21 Oct 2026 07:28:00 GMT").respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
  }

  @Test
  void remainingZeroWaitsUntilReset() {
    http.respond(
            403,
            "",
            "x-ratelimit-remaining",
            "0",
            "x-ratelimit-reset",
            String.valueOf(NOW.getEpochSecond() + 30))
        .respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(sleeps).containsExactly(Duration.ofSeconds(30));
  }

  @Test
  void pastResetRetriesWithoutWaiting() {
    http.respond(
            429,
            "",
            "x-ratelimit-remaining",
            "0",
            "x-ratelimit-reset",
            String.valueOf(NOW.getEpochSecond() - 10))
        .respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(retryStatuses).hasSize(1);
    assertThat(sleeps).containsExactly(Duration.ZERO);
  }

  @Test
  void resetBeyondSixtySecondsThrowsWithResetTime() {
    Instant resetAt = NOW.plusSeconds(3600);
    http.respond(
        403,
        "",
        "x-ratelimit-remaining",
        "0",
        "x-ratelimit-reset",
        String.valueOf(resetAt.getEpochSecond()));

    assertThatThrownBy(() -> fetchMeta(client()))
        .isInstanceOf(RetryAfterTooLongException.class)
        .isInstanceOfSatisfying(
            RateLimitResetException.class,
            e -> {
              assertThat(e.resetAt()).isEqualTo(resetAt);
              assertThat(e.getMessage()).contains("2026-10-18T06:00:00Z");
            });
    assertThat(http.requests()).hasSize(1);
  }

  @Test
  void secondaryRateLimitBodyWaitsSixtySeconds() {
    http.respond(403, "{\"message\":\"You have exceeded a secondary rate limit\"}")
        .respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
  }

  @Test
  void plainTooManyRequestsUsesExponentialBackoff() {
    http.respond(429, "{}").respond(200, PR_JSON);

    fetchMeta(client());

    assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
  }

  @Test
  void getPullRequestParsesMetaWithNullBody() {
    http.respond(200, PR_JSON);

    PullRequestMeta meta = fetchMeta(client());

    assertThat(meta).isEqualTo(new PullRequestMeta("제목", null, "b1", "h1", 3));
    assertThat(http.requests().get(0).uri().getPath()).isEqualTo("/repos/o/r/pulls/7");
  }

  @Test
  void listFilesParsesEntriesAndNextLink() {
    String json =
        """
        [{"filename":"b.txt","previous_filename":"a.txt","status":"renamed","additions":1,"deletions":2},
         {"filename":"c.txt","status":"modified","additions":0,"deletions":0,"patch":"@@ -1 +1 @@"}]
        """;
    http.respond(
            200,
            json,
            "Link",
            "<https://api.example.test/x?page=3>; rel=\"next\", <https://api.example.test/x?page=9>;"
                + " rel=\"last\"")
        .respond(200, "[]");
    HttpGitHubClient client = client();

    FilePage page = client.listFiles(REPO, 7, 2);
    FilePage last = client.listFiles(REPO, 7, 3);

    HttpRequest first = http.requests().get(0);
    assertThat(first.uri().getPath()).isEqualTo("/repos/o/r/pulls/7/files");
    assertThat(first.uri().getQuery()).isEqualTo("per_page=100&page=2");
    assertThat(page.hasNext()).isTrue();
    assertThat(page.files())
        .containsExactly(
            new FilePage.Entry("b.txt", "a.txt", "renamed", 1, 2, null),
            new FilePage.Entry("c.txt", null, "modified", 0, 0, "@@ -1 +1 @@"));
    assertThat(last.hasNext()).isFalse();
  }

  @Test
  void getTreeParsesKindsTruncatedAndSkipsUnknownType() {
    http.respond(
        200,
        """
        {"truncated":true,"tree":[
         {"path":"a.txt","type":"blob"},{"path":"d","type":"tree"},
         {"path":"m","type":"commit"},{"path":"z","type":"weird"}]}
        """);

    RepoTree tree = client().getTree(REPO, "abc");

    assertThat(http.requests().get(0).uri().getPath()).isEqualTo("/repos/o/r/git/trees/abc");
    assertThat(http.requests().get(0).uri().getQuery()).isEqualTo("recursive=1");
    assertThat(tree.truncated()).isTrue();
    assertThat(tree.entries())
        .containsExactly(
            new RepoTree.Entry("a.txt", RepoTree.Kind.BLOB),
            new RepoTree.Entry("d", RepoTree.Kind.TREE),
            new RepoTree.Entry("m", RepoTree.Kind.COMMIT));
  }

  @Test
  void getFileDecodesMultilineBase64AsUtf8() {
    String text = "안녕하세요 hello\n".repeat(10);
    String b64 =
        Base64.getMimeEncoder(20, "\n".getBytes(StandardCharsets.UTF_8))
            .encodeToString(text.getBytes(StandardCharsets.UTF_8));
    String json =
        "{\"type\":\"file\",\"encoding\":\"base64\",\"content\":\""
            + b64.replace("\n", "\\n")
            + "\"}";
    http.respond(200, json);

    FileFetch result = client().getFile(REPO, "dir/한 글.txt", "abc");

    assertThat(result).isEqualTo(new FileFetch.Found(text));
    URI uri = http.requests().get(0).uri();
    assertThat(uri.getRawPath()).isEqualTo("/repos/o/r/contents/dir/%ED%95%9C%20%EA%B8%80.txt");
    assertThat(uri.getRawQuery()).isEqualTo("ref=abc");
  }

  @Test
  void getFileMapsDirectoryTooLargeSymlinkAndSubmodule() {
    http.respond(200, "[{\"name\":\"a\"}]")
        .respond(200, "{\"type\":\"file\",\"encoding\":\"none\",\"content\":\"\"}")
        .respond(200, "{\"type\":\"symlink\"}")
        .respond(200, "{\"type\":\"submodule\"}");
    HttpGitHubClient client = client();

    assertThat(client.getFile(REPO, "d", "s")).isInstanceOf(FileFetch.IsDirectory.class);
    assertThat(client.getFile(REPO, "big", "s")).isInstanceOf(FileFetch.TooLarge.class);
    assertThat(client.getFile(REPO, "link", "s")).isInstanceOf(FileFetch.NotFound.class);
    assertThat(client.getFile(REPO, "sub", "s")).isInstanceOf(FileFetch.IsDirectory.class);
  }

  @Test
  void getFileWithDotSegmentPathReturnsNotFoundWithoutRequest() {
    http.respond(200, file("ok"));
    HttpGitHubClient client = client();

    assertThat(client.getFile(REPO, "a/../b", "s")).isInstanceOf(FileFetch.NotFound.class);
    assertThat(http.requests()).isEmpty();
    assertThat(client.getFile(REPO, "a/b", "s")).isEqualTo(new FileFetch.Found("ok"));
    assertThat(http.requests()).hasSize(1);
  }
}
