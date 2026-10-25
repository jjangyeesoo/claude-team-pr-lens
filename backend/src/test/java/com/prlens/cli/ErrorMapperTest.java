package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.config.ConfigException;
import com.prlens.github.GitHubApiException;
import com.prlens.github.RateLimitResetException;
import com.prlens.support.MaskingPrintStream;
import com.prlens.support.NonRetryableApiException;
import com.prlens.support.PrLensException;
import com.prlens.support.RetriesExhaustedException;
import com.prlens.support.RetryAfterTooLongException;
import com.prlens.support.SecretMasker;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

class ErrorMapperTest {

  private static final String SECRET = "sk-ant-secretKey";

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, false, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, false, StandardCharsets.UTF_8);

  private int map(Throwable error) {
    return ErrorMapper.toExitCode(error, out, err, false);
  }

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  private String stdout() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  // 요구사항 1.9
  @Test
  void mapsUsageConfigAndMissingSecretTo2() {
    UsageException usage = new UsageException("URL 형식이 올바르지 않습니다");
    ConfigException config = ConfigException.of("llm.model", "값이 비었습니다");
    MissingSecretException missing = new MissingSecretException(List.of("GITHUB_TOKEN"));

    assertThat(map(usage)).isEqualTo(2);
    assertThat(map(config)).isEqualTo(2);
    assertThat(map(missing)).isEqualTo(2);
    assertThat(stderr())
        .isEqualTo(
            usage.getMessage() + "\n" + config.getMessage() + "\n" + missing.getMessage() + "\n");
  }

  // 요구사항 1.11
  @Test
  void printsGitHubApiMessage() {
    GitHubApiException error = new GitHubApiException(404, "GitHub API가 404를 반환했습니다");

    assertThat(map(error)).isEqualTo(2);
    assertThat(stderr()).isEqualTo("GitHub API가 404를 반환했습니다\n");
  }

  // 요구사항 21.5
  @Test
  void rateLimitResetKeepsResetTime() {
    RateLimitResetException error =
        new RateLimitResetException(300, Instant.parse("2026-10-25T12:00:00Z"));

    assertThat(map(error)).isEqualTo(2);
    assertThat(stderr()).isEqualTo(error.getMessage() + "\n").contains("2026-10-25T12:00:00Z");
  }

  // 요구사항 21.6
  @Test
  void retriesExhaustedPrintsApiStatusAttempts() {
    RetriesExhaustedException error = new RetriesExhaustedException("Anthropic", "529", 4);

    assertThat(map(error)).isEqualTo(2);
    assertThat(stderr()).isEqualTo("Anthropic 요청이 4번 시도 뒤에도 실패했습니다 (마지막 응답: 529)\n");
  }

  // 요구사항 21.5, 21.8
  @Test
  void nonRetryableAndRetryAfterTooLongMapTo2() {
    assertThat(map(new NonRetryableApiException("GitHub", 422))).isEqualTo(2);
    assertThat(map(new RetryAfterTooLongException("Anthropic", 120))).isEqualTo(2);
    assertThat(stderr())
        .isEqualTo(
            "GitHub 요청이 실패했습니다 (상태 코드 422)\n" + "Anthropic 요청의 대기 시간이 120초로 60초를 넘어 재시도하지 않습니다\n");
  }

  // 요구사항 4.11, 13.8: 모든 Chunk 실패 같은 T1의 예외도 같은 기반 타입이면 2다
  @Test
  void anyPrLensExceptionMapsTo2() {
    PrLensException error = new PrLensException("모든 Chunk가 실패했습니다 (3개)") {};

    assertThat(map(error)).isEqualTo(2);
    assertThat(stderr()).isEqualTo("모든 Chunk가 실패했습니다 (3개)\n");
  }

  // 요구사항 15.11
  @Test
  void stdoutStaysEmptyOnError() {
    map(new GitHubApiException(401, "인증 실패"));
    map(new IllegalStateException("예상하지 못한 오류"));

    assertThat(stdout()).isEmpty();
  }

  @Test
  void multilineMessageKeptAsIs() {
    ConfigException error =
        new ConfigException(
            List.of(
                new ConfigException.Problem("llm.model", "값이 비었습니다"),
                new ConfigException.Problem("llm.effort", "허용 값이 아닙니다")));

    map(error);

    assertThat(stderr()).isEqualTo(error.getMessage() + "\n");
    assertThat(stderr().lines().count()).isGreaterThan(1);
  }

  @Test
  void unexpectedExceptionPrintsInternalErrorWithoutStack() {
    int code = map(new IllegalStateException("민감한 내용 " + SECRET));

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).isEqualTo("내부 오류가 발생했습니다 (IllegalStateException)\n");
  }

  @Test
  void unexpectedExceptionPrintsStackInDebug() {
    int code = ErrorMapper.toExitCode(new IllegalStateException("깨졌습니다"), out, err, true);

    assertThat(code).isEqualTo(2);
    assertThat(stderr())
        .startsWith("내부 오류가 발생했습니다 (IllegalStateException)\n")
        .contains("java.lang.IllegalStateException: 깨졌습니다")
        .contains("at com.prlens.cli.ErrorMapperTest")
        .endsWith("\n");
  }

  @Test
  void debugStackIsMasked() {
    ByteArrayOutputStream target = new ByteArrayOutputStream();
    PrintStream masked = new MaskingPrintStream(target, new SecretMasker(List.of(SECRET)));

    ErrorMapper.toExitCode(new IllegalStateException("키 " + SECRET), out, masked, true);

    String text = target.toString(StandardCharsets.UTF_8);
    assertThat(text).contains("***").doesNotContain(SECRET);
  }

  @Test
  void errorIsMappedToInternalError() {
    int code = map(new StackOverflowError());

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).isEqualTo("내부 오류가 발생했습니다 (StackOverflowError)\n");
  }

  @Test
  void nullMessageFallsBackToClassName() {
    int code = map(new PrLensException(null) {});

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).endsWith("\n").isNotEqualTo("null\n").isNotEqualTo("\n");
  }

  // 주의(18.1~18.2 리뷰): picocli가 실행 중 예외를 감싼 것은 풀어서 매핑한다
  @Test
  void executionExceptionIsUnwrapped() {
    CommandLine.ExecutionException wrapped =
        new CommandLine.ExecutionException(
            PrLensMain.commandLine(out, err, a -> 0),
            "실행 실패",
            new GitHubApiException(403, "권한 부족"));

    assertThat(map(wrapped)).isEqualTo(2);
    assertThat(stderr()).isEqualTo("권한 부족\n");
  }

  // 주의(3 리뷰): 메시지를 \n으로 끝내고 stdout과 stderr를 모두 flush한다
  @Test
  void messageEndsWithNewlineAndFlushesBothStreams() {
    AtomicInteger outFlushes = new AtomicInteger();
    AtomicInteger errFlushes = new AtomicInteger();
    ByteArrayOutputStream errTarget = countingFlushes(errFlushes);
    PrintStream countingOut =
        new PrintStream(countingFlushes(outFlushes), false, StandardCharsets.UTF_8);
    PrintStream countingErr = new PrintStream(errTarget, false, StandardCharsets.UTF_8);

    ErrorMapper.toExitCode(new UsageException("잘못된 입력 s"), countingOut, countingErr, false);

    assertThat(errTarget.toString(StandardCharsets.UTF_8)).isEqualTo("잘못된 입력 s\n");
    assertThat(errFlushes.get()).isPositive();
    assertThat(outFlushes.get()).isPositive();
  }

  private static ByteArrayOutputStream countingFlushes(AtomicInteger counter) {
    return new ByteArrayOutputStream() {
      @Override
      public void flush() {
        counter.incrementAndGet();
      }
    };
  }
}
