package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.config.ConfigException;
import com.prlens.support.SecretMasker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliPipelineTest {

  private static final String URL = "https://github.com/o/r/pull/7";
  private static final String GH = "ghp_secretGithubToken";
  private static final String AK = "sk-ant-secretAnthropicKey";

  @TempDir Path work;
  @TempDir Path home;

  private static Map<String, String> env(String gh, String ak) {
    Map<String, String> env = new HashMap<>();
    if (gh != null) {
      env.put("GITHUB_TOKEN", gh);
    }
    if (ak != null) {
      env.put("ANTHROPIC_API_KEY", ak);
    }
    return env;
  }

  private static ReviewArgs args(String url) {
    return new ReviewArgs(url, OutputFormat.MARKDOWN, null);
  }

  private CliPipeline.Preflight preflight(ReviewArgs args, Map<String, String> env) {
    return CliPipeline.preflight(args, env, work, home);
  }

  @Test
  void validPreflightReturnsParsedValues() {
    ReviewArgs args = new ReviewArgs(URL + "/files?x=1", OutputFormat.JSON, null);

    CliPipeline.Preflight p = preflight(args, env(" " + GH + "\r", AK + "\n"));

    assertThat(p.url().repo().owner()).isEqualTo("o");
    assertThat(p.url().number()).isEqualTo(7);
    assertThat(p.format()).isEqualTo(OutputFormat.JSON);
    assertThat(p.config()).isNotNull();
    assertThat(p.githubToken()).isEqualTo(GH);
    assertThat(p.anthropicApiKey()).isEqualTo(AK);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://github.com/o/r/pull/7",
        "https://example.com/o/r/pull/7",
        "https://github.com/o/r/issues/7",
        "https://github.com/o/r/pull/0",
        "https://github.com/o/r/pull/2147483648",
        ""
      })
  void invalidUrlThrowsUsageExampleAndSkipsNext(String url) {
    AtomicInteger calls = new AtomicInteger();
    ReviewRunner runner = CliPipeline.runner(env(GH, AK), work, home, p -> calls.incrementAndGet());

    assertThatThrownBy(() -> runner.run(args(url)))
        .isInstanceOf(UsageException.class)
        .hasMessageContaining("https://github.com/{owner}/{repo}/pull/{number}");
    assertThat(calls).hasValue(0);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", " \t\r\n"})
  void blankGithubTokenThrowsMissingSecret(String blank) {
    assertThatThrownBy(() -> preflight(args(URL), env(blank, AK)))
        .isInstanceOf(MissingSecretException.class)
        .hasMessageContaining("GITHUB_TOKEN")
        .hasMessageNotContaining("ANTHROPIC_API_KEY");
    assertThatThrownBy(() -> preflight(args(URL), env(null, AK)))
        .isInstanceOf(MissingSecretException.class)
        .hasMessageContaining("GITHUB_TOKEN");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", " \t\r\n"})
  void blankAnthropicKeyThrowsMissingSecret(String blank) {
    assertThatThrownBy(() -> preflight(args(URL), env(GH, blank)))
        .isInstanceOf(MissingSecretException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY")
        .hasMessageNotContaining("GITHUB_TOKEN");
    assertThatThrownBy(() -> preflight(args(URL), env(GH, null)))
        .isInstanceOf(MissingSecretException.class)
        .hasMessageContaining("ANTHROPIC_API_KEY");
  }

  @Test
  void bothSecretsMissingReportedInOneException() {
    assertThatThrownBy(() -> preflight(args(URL), env(null, "  ")))
        .isInstanceOf(MissingSecretException.class)
        .hasMessageContaining("GITHUB_TOKEN")
        .hasMessageContaining("ANTHROPIC_API_KEY");
  }

  @Test
  void configErrorBeatsMissingSecret() throws IOException {
    Files.writeString(work.resolve(".prlens.yml"), "nope: 1\n", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> preflight(args(URL), env(null, null)))
        .isInstanceOf(ConfigException.class);
  }

  @Test
  void urlErrorBeatsConfigError() throws IOException {
    Files.writeString(work.resolve(".prlens.yml"), "nope: 1\n", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> preflight(args("not a url"), env(null, null)))
        .isInstanceOf(UsageException.class);
  }

  @Test
  void missingExplicitConfigFileThrowsConfigException() {
    ReviewArgs args = new ReviewArgs(URL, OutputFormat.MARKDOWN, Path.of("absent.yml"));

    assertThatThrownBy(() -> preflight(args, env(GH, AK))).isInstanceOf(ConfigException.class);
  }

  @Test
  void defaultConfigFoundInWorkingDirThenHome() throws IOException {
    Files.writeString(
        home.resolve(".prlens.yml"), "retry:\n  maxRetries: 4\n", StandardCharsets.UTF_8);

    CliPipeline.Preflight fromHome = preflight(args(URL), env(GH, AK));
    assertThat(fromHome.config().maxRetries()).isEqualTo(4);

    Files.writeString(
        work.resolve(".prlens.yml"), "retry:\n  maxRetries: 2\n", StandardCharsets.UTF_8);
    CliPipeline.Preflight fromWork = preflight(args(URL), env(GH, AK));
    assertThat(fromWork.config().maxRetries()).isEqualTo(2);
  }

  @Test
  void preflightToStringHidesSecrets() {
    CliPipeline.Preflight p = preflight(args(URL), env(GH, AK));

    assertThat(p.toString()).doesNotContain(GH).doesNotContain(AK);
  }

  @Test
  void runnerSkipsNextOnAnyPreflightFailure() {
    AtomicInteger calls = new AtomicInteger();

    assertThatThrownBy(
            () ->
                CliPipeline.runner(env(null, null), work, home, p -> calls.incrementAndGet())
                    .run(args(URL)))
        .isInstanceOf(MissingSecretException.class);
    assertThat(calls).hasValue(0);

    int code = CliPipeline.runner(env(GH, AK), work, home, p -> 5).run(args(URL));
    assertThat(code).isEqualTo(5);
  }

  @Test
  void maskerCoversRawAndStrippedEnvValues() {
    SecretMasker masker = CliPipeline.secretMasker(env(GH + "\r", " " + AK));

    assertThat(masker.mask("a " + GH + " b")).isEqualTo("a *** b");
    assertThat(masker.mask("a " + GH + "\r b")).doesNotContain(GH);
    assertThat(masker.mask("k=" + AK)).isEqualTo("k=***");
    assertThat(masker.mask("k= " + AK)).doesNotContain(AK);
  }

  @Test
  void maskerIgnoresBlankValues() {
    SecretMasker masker = CliPipeline.secretMasker(env("  ", ""));

    assertThat(masker.mask("plain  text")).isEqualTo("plain  text");
  }
}
