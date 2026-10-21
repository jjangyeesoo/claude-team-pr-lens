package com.prlens.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.model.RepoRef;
import com.prlens.support.RetriesExhaustedException;
import com.prlens.support.RetryAfterTooLongException;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import com.prlens.support.SecretMasker;
import com.prlens.testkit.FakeGitHubServer;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * {@link HttpGitHubClient}를 가짜 서버에 실제 HTTP로 연결한 통합 테스트 (요구사항 1.8, 1.11, 1.13, 21.2, 21.9~21.11).
 */
class HttpGitHubClientServerTest {

  private static final RepoRef REPO = new RepoRef("o", "r");
  private static final String TOKEN = "fake-token-value";
  private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final String PR_PATH = "/repos/o/r/pulls/5";
  private static final String PR_BODY =
      "{\"title\":\"t\",\"body\":null,\"base\":{\"sha\":\"b1\"},\"head\":{\"sha\":\"h1\"},"
          + "\"changed_files\":1}";

  private final FakeGitHubServer server = new FakeGitHubServer();
  private final List<Duration> sleeps = new ArrayList<>();
  private final List<String> retryStatuses = new ArrayList<>();

  @AfterEach
  void closeServer() {
    server.close();
  }

  private static HttpClient realClient() {
    return HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
  }

  private HttpGitHubClient client(HttpClient http, URI apiBase, int maxRetries) {
    RetryExecutor retry =
        new RetryExecutor(
            new RetryPolicy(maxRetries),
            sleeps::add,
            (api, next, max, status, wait) -> retryStatuses.add(status));
    return new HttpGitHubClient(
        http,
        new PatCredentials(TOKEN),
        retry,
        new HttpGitHubClient.Options(apiBase, CLOCK, new SecretMasker(List.of()), null));
  }

  private HttpGitHubClient client(int maxRetries) {
    return client(realClient(), server.baseUri(), maxRetries);
  }

  @Test
  void everyRequestKindSendsBearerTokenToFakeServer() {
    server
        .respond(PR_PATH, 200, PR_BODY)
        .respond("/repos/o/r/pulls/5/files?per_page=100&page=1", 200, "[]")
        .respond("/repos/o/r/git/trees/b1?recursive=1", 200, "{\"tree\":[],\"truncated\":false}")
        .respond(
            "/repos/o/r/contents/CLAUDE.md?ref=b1",
            200,
            "{\"type\":\"file\",\"encoding\":\"base64\",\"content\":\"aGk=\"}");
    HttpGitHubClient client = client(0);

    client.getPullRequest(REPO, 5);
    client.listFiles(REPO, 5, 1);
    client.getTree(REPO, "b1");
    FileFetch file = client.getFile(REPO, "CLAUDE.md", "b1");

    assertThat(file).isEqualTo(new FileFetch.Found("hi"));
    assertThat(server.requests()).hasSize(4);
    assertThat(server.requests())
        .allSatisfy(
            request -> {
              assertThat(request.header("Authorization")).isEqualTo("Bearer " + TOKEN);
              assertThat(request.pathAndQuery()).startsWith("/repos/o/r/");
            });
  }

  @ParameterizedTest
  @CsvSource({"401, 토큰이 잘못되었거나 만료", "403, 토큰 권한 부족", "404, 저장소 접근 불가 또는 PR 번호 없음"})
  void unauthorizedForbiddenNotFoundFailOnceWithCauseMessage(int status, String cause) {
    server.respond(PR_PATH, status, "{\"message\":\"x\"}");

    assertThatThrownBy(() -> client(3).getPullRequest(REPO, 5))
        .isInstanceOfSatisfying(
            GitHubApiException.class,
            e -> {
              assertThat(e.status()).isEqualTo(status);
              assertThat(e.getMessage()).contains(cause);
            });
    assertThat(server.requests()).hasSize(1);
    assertThat(sleeps).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(ints = {403, 429})
  void retryAfterRateLimitWaitsHeaderSecondsThenSucceeds(int status) {
    server.respond(PR_PATH, status, "{}", "retry-after", "7").respond(PR_PATH, 200, PR_BODY);

    PullRequestMeta meta = client(3).getPullRequest(REPO, 5);

    assertThat(meta.title()).isEqualTo("t");
    assertThat(sleeps).containsExactly(Duration.ofSeconds(7));
    assertThat(retryStatuses).containsExactly(String.valueOf(status));
    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void retryAfterOverSixtyStopsAfterOneRequest() {
    server.respond(PR_PATH, 429, "{}", "retry-after", "61");

    assertThatThrownBy(() -> client(3).getPullRequest(REPO, 5))
        .isInstanceOf(RetryAfterTooLongException.class);
    assertThat(server.requests()).hasSize(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void remainingZeroWaitsUntilResetThenSucceeds() {
    server
        .respond(
            PR_PATH,
            403,
            "{}",
            "x-ratelimit-remaining",
            "0",
            "x-ratelimit-reset",
            String.valueOf(NOW.getEpochSecond() + 30))
        .respond(PR_PATH, 200, PR_BODY);

    client(3).getPullRequest(REPO, 5);

    assertThat(sleeps).containsExactly(Duration.ofSeconds(30));
    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void resetBeyondSixtyStopsWithUtcResetTime() {
    long reset = NOW.getEpochSecond() + 3600;
    server.respond(
        PR_PATH,
        403,
        "{}",
        "x-ratelimit-remaining",
        "0",
        "x-ratelimit-reset",
        String.valueOf(reset));

    assertThatThrownBy(() -> client(3).getPullRequest(REPO, 5))
        .isInstanceOf(RateLimitResetException.class)
        .isInstanceOf(RetryAfterTooLongException.class)
        .hasMessageContaining(Instant.ofEpochSecond(reset).toString());
    assertThat(server.requests()).hasSize(1);
    assertThat(sleeps).isEmpty();
  }

  @Test
  void secondaryRateLimitBodyWaitsSixtySeconds() {
    server
        .respond(
            PR_PATH,
            403,
            "{\"message\":\"You have exceeded a secondary rate limit. Please wait a few minutes"
                + " before you try again.\"}")
        .respond(PR_PATH, 200, PR_BODY);

    PullRequestMeta meta = client(3).getPullRequest(REPO, 5);

    assertThat(meta.headSha()).isEqualTo("h1");
    assertThat(sleeps).containsExactly(Duration.ofSeconds(60));
    assertThat(server.requests()).hasSize(2);
  }

  @Test
  void everyRequestAsksThirtySecondTimeout() {
    server
        .respond(PR_PATH, 200, PR_BODY)
        .respond("/repos/o/r/pulls/5/files?per_page=100&page=1", 200, "[]")
        .respond("/repos/o/r/git/trees/b1?recursive=1", 200, "{\"tree\":[]}")
        .respond("/repos/o/r/contents/CLAUDE.md?ref=b1", 404, "{}");
    ShortTimeoutHttpClient http = new ShortTimeoutHttpClient(realClient(), Duration.ofSeconds(5));
    HttpGitHubClient client = client(http, server.baseUri(), 0);

    client.getPullRequest(REPO, 5);
    client.listFiles(REPO, 5, 1);
    client.getTree(REPO, "b1");
    client.getFile(REPO, "CLAUDE.md", "b1");

    assertThat(http.originalTimeouts())
        .hasSize(4)
        .allSatisfy(timeout -> assertThat(timeout).isEqualTo(Optional.of(Duration.ofSeconds(30))));
  }

  @Test
  void slowResponseTimesOutAndIsRetriedAsTimeout() {
    server.respondSlowly(PR_PATH).respondSlowly(PR_PATH);
    HttpClient http = new ShortTimeoutHttpClient(realClient(), Duration.ofMillis(200));

    assertThatThrownBy(() -> client(http, server.baseUri(), 1).getPullRequest(REPO, 5))
        .isInstanceOfSatisfying(
            RetriesExhaustedException.class,
            e -> {
              assertThat(e.lastStatus()).isEqualTo("timeout");
              assertThat(e.attempts()).isEqualTo(2);
            });
    assertThat(server.requests()).hasSize(2);
    assertThat(retryStatuses).containsExactly("timeout");
  }

  @Test
  void slowFirstResponseThenSuccessReturnsResult() {
    server.respondSlowly(PR_PATH).respond(PR_PATH, 200, PR_BODY);
    HttpClient http = new ShortTimeoutHttpClient(realClient(), Duration.ofMillis(200));

    PullRequestMeta meta = client(http, server.baseUri(), 1).getPullRequest(REPO, 5);

    assertThat(meta.title()).isEqualTo("t");
    assertThat(sleeps).containsExactly(Duration.ofSeconds(1));
  }

  @Test
  void closedPortIsRetriedAsConnectionFailure() throws IOException {
    int port;
    try (ServerSocket socket = new ServerSocket(0)) {
      port = socket.getLocalPort();
    }
    URI closed = URI.create("http://127.0.0.1:" + port + "/");

    assertThatThrownBy(() -> client(realClient(), closed, 1).getPullRequest(REPO, 5))
        .isInstanceOfSatisfying(
            RetriesExhaustedException.class,
            e -> assertThat(e.lastStatus()).isEqualTo("connection_failed"));
    assertThat(retryStatuses).containsExactly("connection_failed");
  }
}
