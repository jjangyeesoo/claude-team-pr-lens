package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.codec.ResultCodec;
import com.prlens.github.FileFetch;
import com.prlens.github.FilePage;
import com.prlens.github.GitHubClient;
import com.prlens.github.PullRequestMeta;
import com.prlens.github.RepoTree;
import com.prlens.llm.LlmApiException;
import com.prlens.llm.LlmClient;
import com.prlens.llm.LlmRequest;
import com.prlens.llm.LlmResponse;
import com.prlens.llm.LlmUsage;
import com.prlens.model.Configuration;
import com.prlens.model.RepoRef;
import com.prlens.pullrequest.PullRequestUrl;
import com.prlens.support.Attempt;
import com.prlens.support.RetriesExhaustedException;
import com.prlens.support.RetryExecutor;
import com.prlens.support.SecretMasker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliPipelineAssembleTest {

  private static final String URL = "https://github.com/o/r/pull/7";

  @TempDir Path work;
  @TempDir Path home;

  /** {@code print(String)} 호출 수를 센다. 결과를 한 번에 쓰는지 확인하려는 것이다. */
  private static final class CountingStream extends PrintStream {
    private final ByteArrayOutputStream bytes;
    int printCalls;

    CountingStream(ByteArrayOutputStream bytes) {
      super(bytes, true, StandardCharsets.UTF_8);
      this.bytes = bytes;
    }

    @Override
    public void print(String s) {
      printCalls++;
      super.print(s);
    }

    String text() {
      flush();
      return bytes.toString(StandardCharsets.UTF_8);
    }
  }

  /** 변경 파일이 없는 PR을 돌려준다. {@code reportedChanged}가 0보다 크면 파일 수 부족이 된다. */
  private static final class FakeGitHub implements GitHubClient {
    private final int reportedChanged;
    private final RuntimeException failure;
    private final List<FilePage.Entry> files;

    FakeGitHub(int reportedChanged, RuntimeException failure) {
      this(reportedChanged, failure, List.of());
    }

    FakeGitHub(int reportedChanged, RuntimeException failure, List<FilePage.Entry> files) {
      this.reportedChanged = reportedChanged;
      this.failure = failure;
      this.files = files;
    }

    @Override
    public PullRequestMeta getPullRequest(RepoRef repo, int number) {
      if (failure != null) {
        throw failure;
      }
      return new PullRequestMeta("제목", "본문", "b".repeat(40), "h".repeat(40), reportedChanged);
    }

    @Override
    public FilePage listFiles(RepoRef repo, int number, int page) {
      return new FilePage(files, false);
    }

    @Override
    public RepoTree getTree(RepoRef repo, String sha) {
      return new RepoTree(List.of(), false);
    }

    @Override
    public FileFetch getFile(RepoRef repo, String path, String sha) {
      throw new AssertionError("빈 트리에서는 파일을 읽지 않는다: " + path);
    }
  }

  private static final class CountingLlm implements LlmClient {
    final AtomicInteger calls = new AtomicInteger();
    private final String reply;

    /** {@code reply}가 null이면 호출 자체가 실패다 (리뷰 대상이 없으면 LLM을 부르지 않는다). */
    CountingLlm(String reply) {
      this.reply = reply;
    }

    @Override
    public LlmResponse send(LlmRequest request) {
      calls.incrementAndGet();
      if (reply == null) {
        throw new AssertionError("리뷰 대상이 없으면 LLM을 부르지 않는다");
      }
      return new LlmResponse("end_turn", reply, new LlmUsage(1_000, 100, 0, 0));
    }
  }

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final CountingStream out = new CountingStream(outBytes);
  private final CountingStream err = new CountingStream(errBytes);
  private final CountingLlm llm = new CountingLlm(null);

  private static CliPipeline.Preflight preflight(OutputFormat format) {
    return new CliPipeline.Preflight(
        PullRequestUrl.parse(URL).orElseThrow(), format, Configuration.defaults(), "gh", "ak");
  }

  private CliPipeline.Factories factories(GitHubClient github) {
    return new CliPipeline.Factories(
        (token, retry, masker, debugLog) -> github, (key, masker, debugLog) -> llm);
  }

  private int run(OutputFormat format, GitHubClient github) {
    return CliPipeline.review(
        preflight(format), factories(github), new SecretMasker(List.of()), false, out, err);
  }

  @Test
  void markdownOutputIsWrittenOnceWithoutExtraNewline() {
    int code = run(OutputFormat.MARKDOWN, new FakeGitHub(0, null));

    String text = out.text();
    assertThat(code).isZero();
    assertThat(out.printCalls).isEqualTo(1);
    assertThat(text).contains("## 요약").endsWith("\n").doesNotEndWith("\n\n");
    assertThat(llm.calls).hasValue(0);
  }

  // 재시도 감싸기를 빠뜨리거나 같은 StderrReporter를 RetryListener로 넘기지 않으면 첫 호출 실패가 그대로 오류가 된다
  @Test
  void llmRetriesOverloadedResponseThenSucceedsAndReportsRetry() {
    List<LlmResponse> replies = new ArrayList<>();
    AtomicInteger calls = new AtomicInteger();
    LlmClient flaky =
        request -> {
          if (calls.incrementAndGet() == 1) {
            throw new LlmApiException("overloaded", 529, null, "0", null);
          }
          replies.add(
              new LlmResponse(
                  "end_turn",
                  "{\"summary\":\"재시도 뒤 요약\",\"findings\":[]}",
                  new LlmUsage(1, 1, 0, 0)));
          return replies.get(0);
        };
    FilePage.Entry entry =
        new FilePage.Entry("src/A.java", null, "modified", 1, 0, "@@ -1,0 +1,1 @@\n+int a;");
    CliPipeline.Factories factories =
        new CliPipeline.Factories(
            (token, retry, masker, debugLog) -> new FakeGitHub(1, null, List.of(entry)),
            (key, masker, debugLog) -> flaky);

    int code =
        CliPipeline.review(
            preflight(OutputFormat.MARKDOWN),
            factories,
            new SecretMasker(List.of()),
            false,
            out,
            err);

    assertThat(code).isZero();
    assertThat(calls).hasValue(2);
    assertThat(out.text()).contains("재시도 뒤 요약");
    assertThat(err.text()).contains("재시도: Claude 2/");
  }

  // GitHub 쪽 실행기도 설정의 재시도 횟수로 만들어지고 같은 StderrReporter에 알린다
  @Test
  void githubRetryExecutorUsesConfiguredMaxRetriesAndReports() {
    List<RetryExecutor> executors = new ArrayList<>();
    CliPipeline.Factories factories =
        new CliPipeline.Factories(
            (token, retry, masker, debugLog) -> {
              executors.add(retry);
              return new FakeGitHub(0, null);
            },
            (key, masker, debugLog) -> llm);
    CliPipeline.review(
        preflight(OutputFormat.MARKDOWN), factories, new SecretMasker(List.of()), false, out, err);

    AtomicInteger attempts = new AtomicInteger();
    assertThatThrownBy(
            () ->
                executors
                    .get(0)
                    .execute(
                        "GitHub",
                        () -> {
                          attempts.incrementAndGet();
                          return new Attempt.Retryable<String>("500", "0", null);
                        }))
        .isInstanceOfSatisfying(
            RetriesExhaustedException.class,
            e -> assertThat(e.attempts()).isEqualTo(Configuration.defaults().maxRetries() + 1));

    assertThat(attempts).hasValue(Configuration.defaults().maxRetries() + 1);
    assertThat(err.text()).contains("재시도: GitHub 2/");
  }

  // 조립 버그(LLM 팩토리 결과를 무시함)는 LLM 경로를 지나야 드러난다
  @Test
  void changedFileGoesThroughLlmOnceAndSummaryReachesStdout() {
    CountingLlm replying = new CountingLlm("{\"summary\":\"요약 문장\",\"findings\":[]}");
    FilePage.Entry entry =
        new FilePage.Entry("src/A.java", null, "modified", 1, 0, "@@ -1,0 +1,1 @@\n+int a;");
    CliPipeline.Factories factories =
        new CliPipeline.Factories(
            (token, retry, masker, debugLog) -> new FakeGitHub(1, null, List.of(entry)),
            (key, masker, debugLog) -> replying);

    int code =
        CliPipeline.review(
            preflight(OutputFormat.MARKDOWN),
            factories,
            new SecretMasker(List.of()),
            false,
            out,
            err);

    assertThat(code).isZero();
    assertThat(replying.calls).hasValue(1);
    assertThat(out.text()).contains("요약 문장");
    assertThat(err.text()).doesNotContain("Chunk 1/1");
  }

  @Test
  void jsonOutputAppendsSingleNewlineAndRoundTrips() {
    run(OutputFormat.JSON, new FakeGitHub(0, null));

    String text = out.text();
    assertThat(out.printCalls).isEqualTo(1);
    assertThat(text).endsWith("}\n");
    assertThat(new ResultCodec().read(text).status().name()).isEqualTo("COMPLETE");
  }

  @Test
  void reportResultIsCalledForBothFormats() {
    for (OutputFormat format : OutputFormat.values()) {
      ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
      CountingStream errStream = new CountingStream(errBuffer);
      CliPipeline.review(
          preflight(format),
          factories(new FakeGitHub(3, null)),
          new SecretMasker(List.of()),
          false,
          new CountingStream(new ByteArrayOutputStream()),
          errStream);

      String text = errStream.text();
      assertThat(text.split("경고\\[files_truncated\\]", -1)).as("형식 %s", format).hasSize(2);
    }
  }

  @Test
  void exitCodeFollowsResolver() {
    assertThat(run(OutputFormat.MARKDOWN, new FakeGitHub(0, null))).isZero();
    assertThat(run(OutputFormat.MARKDOWN, new FakeGitHub(3, null))).isEqualTo(3);
  }

  @Test
  void stdoutStaysEmptyWhenFetchFails() {
    RuntimeException failure = new IllegalStateException("boom");

    assertThatThrownBy(() -> run(OutputFormat.MARKDOWN, new FakeGitHub(0, failure)))
        .isSameAs(failure);

    assertThat(out.text()).isEmpty();
    assertThat(llm.calls).hasValue(0);
  }

  @Test
  void factoriesReceiveStrippedCredentials() {
    CliPipeline.Preflight checked =
        CliPipeline.preflight(
            new ReviewArgs(URL, OutputFormat.MARKDOWN, null),
            Map.of("GITHUB_TOKEN", " ghp_x \r\n", "ANTHROPIC_API_KEY", "\tsk-y "),
            work,
            home);
    List<String> seen = new ArrayList<>();
    CliPipeline.Factories factories =
        new CliPipeline.Factories(
            (token, retry, masker, debugLog) -> {
              seen.add(token);
              return new FakeGitHub(0, null);
            },
            (key, masker, debugLog) -> {
              seen.add(key);
              return llm;
            });

    CliPipeline.review(checked, factories, new SecretMasker(List.of()), false, out, err);

    assertThat(seen).containsExactly("ghp_x", "sk-y");
  }

  @Test
  void httpClientDoesNotFollowRedirects() {
    assertThat(CliPipeline.httpClient().followRedirects()).isEqualTo(HttpClient.Redirect.NEVER);
  }

  @Test
  void debugLogUsesSameMaskerAndStderr() {
    SecretMasker masker = new SecretMasker(List.of("ghp_x"));
    List<SecretMasker> maskers = new ArrayList<>();
    List<Consumer<String>> logs = new ArrayList<>();
    CliPipeline.Factories factories =
        new CliPipeline.Factories(
            (token, retry, m, debugLog) -> {
              maskers.add(m);
              logs.add(debugLog);
              return new FakeGitHub(0, null);
            },
            (key, m, debugLog) -> {
              maskers.add(m);
              logs.add(debugLog);
              return llm;
            });

    CliPipeline.review(preflight(OutputFormat.MARKDOWN), factories, masker, true, out, err);

    assertThat(maskers).containsExactly(masker, masker);
    assertThat(logs).doesNotContainNull();
    logs.get(0).accept("GET /pulls/7");
    assertThat(err.text()).endsWith("GET /pulls/7\n");
    assertThat(out.text()).doesNotContain("GET /pulls/7");

    List<Consumer<String>> quiet = new ArrayList<>();
    CliPipeline.review(
        preflight(OutputFormat.MARKDOWN),
        new CliPipeline.Factories(
            (token, retry, m, debugLog) -> {
              quiet.add(debugLog);
              return new FakeGitHub(0, null);
            },
            (key, m, debugLog) -> {
              quiet.add(debugLog);
              return llm;
            }),
        masker,
        false,
        out,
        err);
    assertThat(quiet).containsOnlyNulls();
  }
}
