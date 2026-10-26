package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.support.MaskingPrintStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 인자·설정·환경변수 오류가 종료 코드 2, 빈 stdout, 외부 호출 0으로 끝나는지 {@code PrLensMain.run}부터 확인한다. */
class PrLensMainIntegrationTest {

  private static final String URL = "https://github.com/o/r/pull/7";
  private static final String GH = "ghp_secretGithubToken";
  private static final String AK = "sk-ant-secretAnthropicKey";

  @TempDir Path work;
  @TempDir Path home;

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final AtomicInteger pipelineCalls = new AtomicInteger();

  private int run(Map<String, String> env, String... args) {
    MaskingPrintStream out = new MaskingPrintStream(outBytes, CliPipeline.secretMasker(env));
    MaskingPrintStream err = new MaskingPrintStream(errBytes, CliPipeline.secretMasker(env));
    return PrLensMain.run(
        args,
        out,
        err,
        CliPipeline.runner(
            env,
            work,
            home,
            preflight -> {
              pipelineCalls.incrementAndGet();
              return 0;
            }));
  }

  private static Map<String, String> fullEnv() {
    Map<String, String> env = new HashMap<>();
    env.put("GITHUB_TOKEN", GH);
    env.put("ANTHROPIC_API_KEY", AK);
    return env;
  }

  private String stdout() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  private void assertFailedBeforeAnyCall() {
    assertThat(stdout()).isEmpty();
    assertThat(pipelineCalls).hasValue(0);
  }

  private Path configFile(String content) throws IOException {
    Path file = work.resolve("custom.yml");
    Files.writeString(file, content, StandardCharsets.UTF_8);
    return file;
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://github.com/o/r/pull/7",
        "https://gitlab.com/o/r/pull/7",
        "https://github.com/o/r/issues/7",
        "https://github.com/o/r/pull/2147483648",
        "not a url"
      })
  void invalidUrlExitsTwoWithExampleAndNoCalls(String url) {
    int code = run(fullEnv(), "review", url);

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains(CliPipeline.URL_EXAMPLE);
    assertFailedBeforeAnyCall();
  }

  @ParameterizedTest
  @ValueSource(strings = {"xml", "MARKDOWN", ""})
  void invalidFormatExitsTwoWithAllowedValues(String format) {
    int code = run(fullEnv(), "review", URL, "--format", format);

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains(ReviewCommand.ALLOWED_FORMATS);
    assertFailedBeforeAnyCall();
  }

  @Test
  void formatWithoutValueExitsTwoWithAllowedValues() {
    int code = run(fullEnv(), "review", URL, "--format");

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains(ReviewCommand.ALLOWED_FORMATS);
    assertFailedBeforeAnyCall();
  }

  @Test
  void invalidConfigValueExitsTwoNamingTheItem() throws IOException {
    Path config = configFile("llm:\n  model: 5.5\n");

    int code = run(fullEnv(), "review", URL, "--config", config.toString());

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("llm.model");
    assertFailedBeforeAnyCall();
  }

  @Test
  void secretItemInConfigNamesVariableAndHidesValue() throws IOException {
    Path config = configFile("token: \"" + GH + "\"\n");

    int code = run(fullEnv(), "review", URL, "--config", config.toString());

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("token").contains("GITHUB_TOKEN").doesNotContain(GH);
    assertFailedBeforeAnyCall();
  }

  @Test
  void missingConfigFileExitsTwoWithoutFallingBack() throws IOException {
    // 기본 위치에 올바른 파일이 있어도 대체하지 않는다 (요구사항 18.9)
    Files.writeString(work.resolve(".prlens.yml"), "sizeLimit: 100\n", StandardCharsets.UTF_8);
    Path missing = work.resolve("none.yml");

    int code = run(fullEnv(), "review", URL, "--config", missing.toString());

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains(missing.toString());
    assertFailedBeforeAnyCall();
  }

  @Test
  void missingGithubTokenExitsTwoNamingOnlyThatVariable() {
    Map<String, String> env = fullEnv();
    env.remove("GITHUB_TOKEN");

    int code = run(env, "review", URL);

    assertThat(code).isEqualTo(2);
    assertThat(stderr())
        .contains("GITHUB_TOKEN")
        .doesNotContain("ANTHROPIC_API_KEY")
        .doesNotContain(AK);
    assertFailedBeforeAnyCall();
  }

  @Test
  void missingBothSecretsReportedInOneMessage() {
    int code = run(new HashMap<>(), "review", URL);

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("GITHUB_TOKEN").contains("ANTHROPIC_API_KEY");
    assertFailedBeforeAnyCall();
  }

  @Test
  void validInputsReachThePipelineOnce() {
    int code = run(fullEnv(), "review", URL);

    assertThat(code).isZero();
    assertThat(pipelineCalls).hasValue(1);
    assertThat(stdout()).isEmpty();
  }

  // 주의(3 리뷰): MaskingPrintStream.stdout()/stderr()는 실제 FileDescriptor를 열어 단위 테스트가 없으므로 하위 프로세스로
  // 확인한다
  @Test
  void subprocessWritesErrorToStderrAndNothingToStdout() throws Exception {
    String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
    ProcessBuilder builder =
        new ProcessBuilder(
            java,
            "-Duser.home=" + home,
            "-cp",
            System.getProperty("java.class.path"),
            "com.prlens.cli.PrLensMain",
            "review",
            URL);
    builder.directory(work.toFile());
    builder.environment().remove("GITHUB_TOKEN");
    builder.environment().remove("ANTHROPIC_API_KEY");
    Path outFile = work.resolve("stdout.txt");
    Path errFile = work.resolve("stderr.txt");
    builder.redirectOutput(outFile.toFile());
    builder.redirectError(errFile.toFile());

    Process process = builder.start();
    assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();

    assertThat(process.exitValue()).isEqualTo(2);
    assertThat(Files.readString(outFile, StandardCharsets.UTF_8)).isEmpty();
    assertThat(Files.readString(errFile, StandardCharsets.UTF_8))
        .contains("GITHUB_TOKEN")
        .contains("ANTHROPIC_API_KEY");
  }
}
