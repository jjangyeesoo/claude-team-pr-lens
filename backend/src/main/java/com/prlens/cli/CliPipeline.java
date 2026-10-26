package com.prlens.cli;

import com.prlens.codec.ResultCodec;
import com.prlens.config.ConfigLoader;
import com.prlens.context.ContextCollector;
import com.prlens.github.GitHubClient;
import com.prlens.github.HttpGitHubClient;
import com.prlens.github.PatCredentials;
import com.prlens.llm.AnthropicLlmClient;
import com.prlens.llm.LlmClient;
import com.prlens.llm.RetryingLlmClient;
import com.prlens.model.Configuration;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewResult;
import com.prlens.output.ExitCodeResolver;
import com.prlens.output.MarkdownFormatter;
import com.prlens.pullrequest.PrFetcher;
import com.prlens.pullrequest.PullRequestUrl;
import com.prlens.review.ReviewEngine;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import com.prlens.support.SecretMasker;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/** 실행 전 검증과 파이프라인 조립. */
final class CliPipeline {

  static final String GITHUB_TOKEN = "GITHUB_TOKEN";
  static final String ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";

  private static final Duration LLM_TIMEOUT = Duration.ofSeconds(180);

  static final String URL_EXAMPLE = "https://github.com/{owner}/{repo}/pull/{number}";

  /** 검증을 통과한 입력. 비밀값이 로그에 새지 않도록 {@code toString}에서 뺀다. */
  record Preflight(
      PullRequestUrl url,
      OutputFormat format,
      Configuration config,
      String githubToken,
      String anthropicApiKey) {

    @Override
    public String toString() {
      return "Preflight[url=" + url + ", format=" + format + "]";
    }
  }

  private CliPipeline() {}

  /**
   * 인자(URL) → 설정 → 환경변수 순서로 검사한다. 실패하면 외부 API를 부르기 전에 예외를 던진다 (요구사항 1.9, 1.10, 18.7, 19.2, 19.3).
   * {@code --format}은 명령이 이미 검증했다.
   */
  static Preflight preflight(
      ReviewArgs args, Map<String, String> env, Path workingDir, Path homeDir) {
    PullRequestUrl url =
        PullRequestUrl.parse(args.url())
            .orElseThrow(
                () ->
                    new UsageException(
                        "PR 주소 형식이 올바르지 않습니다: " + args.url() + "\n올바른 형식: " + URL_EXAMPLE));
    Configuration config = new ConfigLoader(workingDir, homeDir).load(args.config());
    String githubToken = stripped(env, GITHUB_TOKEN);
    String anthropicKey = stripped(env, ANTHROPIC_API_KEY);
    List<String> missing = new ArrayList<>();
    if (githubToken.isEmpty()) {
      missing.add(GITHUB_TOKEN);
    }
    if (anthropicKey.isEmpty()) {
      missing.add(ANTHROPIC_API_KEY);
    }
    if (!missing.isEmpty()) {
      throw new MissingSecretException(missing);
    }
    return new Preflight(url, args.format(), config, githubToken, anthropicKey);
  }

  /**
   * 환경변수 값을 받은 그대로와 다듬은 값 둘 다 등록한다. 요청에는 다듬은 값을 쓰므로, 끝의 공백이나 {@code \r}이 있어도 어느 쪽 형태로 메시지에 나오든 가려진다
   * (요구사항 19.4). 빈 값은 {@link SecretMasker}가 뺀다.
   */
  static SecretMasker secretMasker(Map<String, String> env) {
    List<String> values = new ArrayList<>();
    for (String name : List.of(GITHUB_TOKEN, ANTHROPIC_API_KEY)) {
      String raw = env.get(name);
      if (raw != null) {
        values.add(raw);
        values.add(raw.strip());
      }
    }
    return new SecretMasker(values);
  }

  /** 검증을 통과했을 때만 {@code next}를 부르는 실행기. 실패는 예외로 던진다. */
  static ReviewRunner runner(
      Map<String, String> env, Path workingDir, Path homeDir, ToIntFunction<Preflight> next) {
    return args -> next.applyAsInt(preflight(args, env, workingDir, homeDir));
  }

  /** 리뷰 대상 저장소와 통신하는 클라이언트를 만든다. 통합 테스트는 호출 수를 세는 가짜를 넘긴다. */
  @FunctionalInterface
  interface GitHubFactory {
    GitHubClient create(
        String token, RetryExecutor retry, SecretMasker masker, Consumer<String> debugLog);
  }

  /** 재시도는 조립 함수가 {@link RetryingLlmClient}로 감싸므로 여기서는 재시도 없는 클라이언트를 만든다. */
  @FunctionalInterface
  interface LlmFactory {
    LlmClient create(String apiKey, SecretMasker masker, Consumer<String> debugLog);
  }

  record Factories(GitHubFactory github, LlmFactory llm) {

    static Factories real() {
      return new Factories(
          (token, retry, masker, debugLog) ->
              new HttpGitHubClient(
                  httpClient(),
                  new PatCredentials(token),
                  retry,
                  new HttpGitHubClient.Options(
                      URI.create("https://api.github.com"), Clock.systemUTC(), masker, debugLog)),
          (apiKey, masker, debugLog) ->
              new AnthropicLlmClient(
                  apiKey, new AnthropicLlmClient.Options(null, LLM_TIMEOUT, masker, debugLog)));
    }
  }

  /**
   * redirect를 따르지 않는 기본 설정이다 (결정 18.3). 따라가면 {@code Authorization}이 다른 호스트로 갈 수 있어 요구사항 19.6을 지키기
   * 어렵다.
   */
  static HttpClient httpClient() {
    return HttpClient.newHttpClient();
  }

  /**
   * 검증을 통과한 입력으로 PR을 가져와 리뷰하고 결과를 stdout에 한 번 쓴 뒤 종료 코드를 돌려준다. 결과를 받기 전에 실패하면 예외를 던지고 stdout은 비어
   * 있다.
   *
   * @param debug true면 HTTP 디버그 로그를 {@code err}로 보낸다 (오류 스택과 같은 조건)
   */
  static int review(
      Preflight preflight,
      Factories factories,
      SecretMasker masker,
      boolean debug,
      PrintStream out,
      PrintStream err) {
    Configuration config = preflight.config();
    StderrReporter reporter = new StderrReporter(err);
    Consumer<String> debugLog = debug ? message -> err.print(message + "\n") : null;
    RetryExecutor retry =
        new RetryExecutor(new RetryPolicy(config.maxRetries()), CliPipeline::sleep, reporter);
    GitHubClient github =
        factories.github().create(preflight.githubToken(), retry, masker, debugLog);
    LlmClient llm =
        new RetryingLlmClient(
            factories.llm().create(preflight.anthropicApiKey(), masker, debugLog), retry);

    PullRequestSnapshot snapshot =
        new PrFetcher(github, reporter).fetch(preflight.url().repo(), preflight.url().number());
    ReviewContext context = new ContextCollector(github, reporter).collect(snapshot, config);
    ReviewResult result = new ReviewEngine(llm, reporter).review(snapshot, context, config);

    return emit(result, context, config, preflight.format(), reporter, out);
  }

  /**
   * 결과에서 유도하는 경고를 stderr로, 결과를 stdout으로 한 번 쓰고 종료 코드를 돌려준다. Markdown은 포매터 출력이 이미 {@code \n}으로 끝나므로
   * 그대로 쓰고, JSON은 {@code ResultCodec.write} 결과 뒤에 {@code \n}을 붙인다.
   */
  static int emit(
      ReviewResult result,
      ReviewContext context,
      Configuration config,
      OutputFormat format,
      StderrReporter reporter,
      PrintStream out) {
    reporter.reportResult(result, config);
    String text =
        format == OutputFormat.JSON
            ? new ResultCodec().write(result) + "\n"
            : new MarkdownFormatter().format(result, context);
    out.print(text);
    out.flush();
    return ExitCodeResolver.resolve(result);
  }

  private static void sleep(Duration wait) {
    try {
      Thread.sleep(wait.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("재시도 대기 중 중단되었습니다", e);
    }
  }

  private static String stripped(Map<String, String> env, String name) {
    String value = env.get(name);
    return value == null ? "" : value.strip();
  }
}
