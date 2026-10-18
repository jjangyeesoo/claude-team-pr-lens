package com.prlens.github;

import com.prlens.model.RepoRef;
import com.prlens.support.Attempt;
import com.prlens.support.RetryExecutor;
import com.prlens.support.SecretMasker;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GitHub REST API 어댑터. */
public final class HttpGitHubClient implements GitHubClient {

  /**
   * @param apiBase API 기준 주소. 테스트의 가짜 서버가 바꾼다
   * @param debugLog null이면 디버그 로그를 쓰지 않는다
   */
  public record Options(URI apiBase, Clock clock, SecretMasker masker, Consumer<String> debugLog) {
    public static Options defaults() {
      return new Options(
          URI.create("https://api.github.com"),
          Clock.systemUTC(),
          new SecretMasker(List.of()),
          null);
    }
  }

  private static final String API = "GitHub";
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final Duration FALLBACK_WAIT = Duration.ofSeconds(60);
  private static final long MAX_WAIT_SECONDS = 60;
  private static final Pattern DIGITS = Pattern.compile("[0-9]+");
  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  private final HttpClient http;
  private final GitHubCredentials credentials;
  private final RetryExecutor retry;
  private final Options options;

  public HttpGitHubClient(HttpClient http, GitHubCredentials credentials, RetryExecutor retry) {
    this(http, credentials, retry, Options.defaults());
  }

  public HttpGitHubClient(
      HttpClient http, GitHubCredentials credentials, RetryExecutor retry, Options options) {
    this.http = http;
    this.credentials = credentials;
    this.retry = retry;
    this.options = options;
  }

  @Override
  public PullRequestMeta getPullRequest(RepoRef repo, int number) {
    String path = repoPath(repo) + "/pulls/" + number;
    return get(
        repo,
        path,
        "",
        response -> {
          JsonNode root = parse(response.body());
          return new PullRequestMeta(
              text(root, "title"),
              text(root, "body"),
              text(root.get("base"), "sha"),
              text(root.get("head"), "sha"),
              root.get("changed_files").intValue());
        },
        null);
  }

  @Override
  public FilePage listFiles(RepoRef repo, int number, int page) {
    String path = repoPath(repo) + "/pulls/" + number + "/files";
    return get(
        repo,
        path,
        "?per_page=100&page=" + page,
        response -> {
          List<FilePage.Entry> entries = new ArrayList<>();
          for (JsonNode node : parse(response.body())) {
            entries.add(
                new FilePage.Entry(
                    text(node, "filename"),
                    text(node, "previous_filename"),
                    text(node, "status"),
                    node.get("additions").intValue(),
                    node.get("deletions").intValue(),
                    text(node, "patch")));
          }
          boolean hasNext =
              response.headers().allValues("Link").stream()
                  .anyMatch(link -> link.contains("rel=\"next\""));
          return new FilePage(entries, hasNext);
        },
        null);
  }

  @Override
  public RepoTree getTree(RepoRef repo, String sha) {
    String path = repoPath(repo) + "/git/trees/" + segment(sha);
    return get(
        repo,
        path,
        "?recursive=1",
        response -> {
          JsonNode root = parse(response.body());
          List<RepoTree.Entry> entries = new ArrayList<>();
          for (JsonNode node : root.get("tree")) {
            RepoTree.Kind kind = kindOf(text(node, "type"));
            if (kind != null) {
              entries.add(new RepoTree.Entry(text(node, "path"), kind));
            }
          }
          JsonNode truncated = root.get("truncated");
          return new RepoTree(entries, truncated != null && truncated.asBoolean());
        },
        null);
  }

  @Override
  public FileFetch getFile(RepoRef repo, String path, String sha) {
    StringBuilder encoded = new StringBuilder();
    for (String part : path.split("/", -1)) {
      if (part.isEmpty() || part.equals(".") || part.equals("..")) {
        return new FileFetch.NotFound();
      }
      encoded.append('/').append(segment(part));
    }
    return get(
        repo,
        repoPath(repo) + "/contents" + encoded,
        "?ref=" + segment(sha),
        response -> fileOf(parse(response.body())),
        FileFetch.NotFound::new);
  }

  private static FileFetch fileOf(JsonNode root) {
    if (root.isArray()) {
      return new FileFetch.IsDirectory();
    }
    String type = text(root, "type");
    if ("submodule".equals(type)) {
      return new FileFetch.IsDirectory();
    }
    if (!"file".equals(type)) {
      return new FileFetch.NotFound();
    }
    if (!"base64".equals(text(root, "encoding"))) {
      return new FileFetch.TooLarge();
    }
    byte[] bytes = Base64.getMimeDecoder().decode(text(root, "content"));
    return new FileFetch.Found(new String(bytes, StandardCharsets.UTF_8));
  }

  private static RepoTree.Kind kindOf(String type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case "blob" -> RepoTree.Kind.BLOB;
      case "tree" -> RepoTree.Kind.TREE;
      case "commit" -> RepoTree.Kind.COMMIT;
      default -> null;
    };
  }

  /**
   * @param notFound 404를 결과 값으로 돌려줄 때의 생성자. null이면 404도 예외다
   */
  private <T> T get(
      RepoRef repo,
      String path,
      String query,
      Function<HttpResponse<String>, T> onSuccess,
      Supplier<T> notFound) {
    HttpRequest request = buildRequest(repo, path, query);
    return retry.execute(
        API,
        () -> {
          HttpResponse<String> response;
          try {
            response = send(request);
          } catch (HttpTimeoutException e) {
            return new Attempt.Retryable<>("timeout", null, null);
          } catch (ConnectException e) {
            return new Attempt.Retryable<>("connection_failed", null, null);
          } catch (IOException e) {
            return new Attempt.Retryable<>("network_error", null, null);
          }
          return classify(response, path, onSuccess, notFound);
        });
  }

  private <T> Attempt<T> classify(
      HttpResponse<String> response,
      String path,
      Function<HttpResponse<String>, T> onSuccess,
      Supplier<T> notFound) {
    int status = response.statusCode();
    if (status >= 200 && status < 300) {
      return new Attempt.Success<>(onSuccess.apply(response));
    }
    if (status == 403 || status == 429) {
      Attempt<T> limited = rateLimited(response);
      if (limited != null) {
        return limited;
      }
      if (status == 429) {
        return new Attempt.Retryable<>("429", null, null);
      }
    }
    if (status == 404 && notFound != null) {
      return new Attempt.Success<>(notFound.get());
    }
    if (status == 401 || status == 403 || status == 404) {
      throw apiException(status, path);
    }
    if (status >= 500) {
      return new Attempt.Retryable<>(
          String.valueOf(status), response.headers().firstValue("retry-after").orElse(null), null);
    }
    return new Attempt.Fatal<>(API, status);
  }

  /** rate limit 응답이면 재시도 판단을, 아니면 null을 돌려준다. */
  private <T> Attempt<T> rateLimited(HttpResponse<String> response) {
    String status = String.valueOf(response.statusCode());
    String retryAfter = response.headers().firstValue("retry-after").orElse(null);
    if (retryAfter != null && DIGITS.matcher(retryAfter).matches()) {
      return new Attempt.Retryable<>(status, retryAfter, secondsOrMax(retryAfter));
    }
    String remaining = response.headers().firstValue("x-ratelimit-remaining").orElse(null);
    if ("0".equals(remaining)) {
      return new Attempt.Retryable<>(status, null, untilReset(response));
    }
    String body = response.body();
    if (body != null && body.toLowerCase(Locale.ROOT).contains("secondary rate limit")) {
      return new Attempt.Retryable<>(status, null, FALLBACK_WAIT);
    }
    if (retryAfter != null) {
      return new Attempt.Retryable<>(status, null, FALLBACK_WAIT);
    }
    return null;
  }

  private Duration untilReset(HttpResponse<String> response) {
    String reset = response.headers().firstValue("x-ratelimit-reset").orElse(null);
    if (reset == null || !DIGITS.matcher(reset).matches() || reset.length() > 15) {
      return FALLBACK_WAIT;
    }
    long resetEpoch = Long.parseLong(reset);
    long seconds = resetEpoch - options.clock().instant().getEpochSecond();
    if (seconds > MAX_WAIT_SECONDS) {
      throw new RateLimitResetException(seconds, Instant.ofEpochSecond(resetEpoch));
    }
    return Duration.ofSeconds(seconds);
  }

  private static Duration secondsOrMax(String digits) {
    try {
      return Duration.ofSeconds(Long.parseLong(digits));
    } catch (NumberFormatException tooLarge) {
      return Duration.ofSeconds(Long.MAX_VALUE);
    }
  }

  private static GitHubApiException apiException(int status, String path) {
    String cause =
        switch (status) {
          case 401 -> "토큰이 잘못되었거나 만료되었을 수 있습니다";
          case 403 -> "토큰 권한 부족일 수 있습니다";
          default -> "저장소 접근 불가 또는 PR 번호 없음일 수 있습니다";
        };
    return new GitHubApiException(
        status, "GitHub API가 " + status + "를 반환했습니다 (GET " + path + "): " + cause);
  }

  private HttpRequest buildRequest(RepoRef repo, String path, String query) {
    return HttpRequest.newBuilder(URI.create(base() + path + query))
        .timeout(TIMEOUT)
        .header("Authorization", credentials.authorizationHeader(repo))
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2022-11-28")
        .header("User-Agent", "prlens")
        .GET()
        .build();
  }

  private HttpResponse<String> send(HttpRequest request) throws IOException {
    Consumer<String> log = options.debugLog();
    if (log != null) {
      log.accept("> GET " + request.uri());
      request
          .headers()
          .map()
          .forEach(
              (name, values) ->
                  values.forEach(
                      value ->
                          log.accept(
                              "> " + name + ": " + options.masker().maskHeader(name, value))));
    }
    try {
      HttpResponse<String> response =
          http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (log != null) {
        log.accept("< " + response.statusCode());
      }
      return response;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("GitHub 요청 중 인터럽트되었습니다", e);
    }
  }

  private String base() {
    String base = options.apiBase().toString();
    return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
  }

  private static String repoPath(RepoRef repo) {
    for (String part : List.of(repo.owner(), repo.name())) {
      if (part.equals(".") || part.equals("..")) {
        throw apiException(404, "/repos/" + repo.owner() + "/" + repo.name());
      }
    }
    return "/repos/" + segment(repo.owner()) + "/" + segment(repo.name());
  }

  /** 경로 구간 하나를 퍼센트 인코딩한다. 공백은 {@code %20}이다. */
  private static String segment(String value) {
    StringBuilder out = new StringBuilder();
    for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
      int c = b & 0xff;
      boolean unreserved =
          (c >= 'A' && c <= 'Z')
              || (c >= 'a' && c <= 'z')
              || (c >= '0' && c <= '9')
              || c == '-'
              || c == '.'
              || c == '_'
              || c == '~';
      if (unreserved) {
        out.append((char) c);
      } else {
        out.append('%').append(String.format("%02X", c));
      }
    }
    return out.toString();
  }

  private static JsonNode parse(String body) {
    return MAPPER.readTree(body);
  }

  private static String text(JsonNode node, String key) {
    JsonNode value = node == null ? null : node.get(key);
    return value == null || value.isNull() ? null : value.stringValue();
  }
}
