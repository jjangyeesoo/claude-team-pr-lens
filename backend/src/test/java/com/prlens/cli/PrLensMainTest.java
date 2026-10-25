package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.config.ConfigException;
import com.prlens.support.MaskingPrintStream;
import com.prlens.support.SecretMasker;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrLensMainTest {

  private static final String URL = "https://github.com/o/r/pull/1";
  private static final String TOKEN = "ghp_secretGithubToken";

  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final SecretMasker masker = new SecretMasker(List.of(TOKEN, "tail-s"));
  private final MaskingPrintStream out = new MaskingPrintStream(outBytes, masker);
  private final MaskingPrintStream err = new MaskingPrintStream(errBytes, masker);

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void preflightErrorsExitWith2MaskedAndStdoutEmpty() {
    int code =
        PrLensMain.run(
            new String[] {"review", URL},
            out,
            err,
            a -> {
              throw ConfigException.of("llm.model", "값 " + TOKEN + " 은 쓸 수 없습니다");
            });

    assertThat(code).isEqualTo(2);
    assertThat(outBytes.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(stderr()).contains("llm.model").contains("***").doesNotContain(TOKEN);
    assertThat(stderr()).endsWith("\n");
  }

  // 주의(18.1~18.2 리뷰): run을 빠져나간 예외가 없어야 main이 닫은 stderr 때문에 메시지가 사라지지 않는다
  @Test
  void runnerExceptionMapsTo2WithMessage() {
    int code =
        PrLensMain.run(
            new String[] {"review", URL},
            out,
            err,
            a -> {
              throw new IllegalStateException("예상하지 못한 오류");
            });

    assertThat(code).isEqualTo(2);
    assertThat(outBytes.toString(StandardCharsets.UTF_8)).isEmpty();
    assertThat(stderr()).isEqualTo("내부 오류가 발생했습니다 (IllegalStateException)\n");
  }

  @Test
  void missingSecretExitsWith2() {
    int code =
        PrLensMain.run(
            new String[] {"review", URL},
            out,
            err,
            a -> {
              throw new MissingSecretException(List.of("GITHUB_TOKEN"));
            });

    assertThat(code).isEqualTo(2);
    assertThat(stderr()).contains("GITHUB_TOKEN");
  }

  @Test
  void errorEndingWithSecretPrefixIsFullyWritten() {
    PrLensMain.run(
        new String[] {"review", URL},
        out,
        err,
        a -> {
          throw new UsageException("끝이 비밀값 앞부분 t");
        });
    PrLensMain.run(
        new String[] {"review", "x"},
        out,
        err,
        a -> {
          throw ConfigException.of("x", "끝이 s");
        });

    assertThat(stderr()).contains("끝이 비밀값 앞부분 t\n").endsWith("끝이 s\n");
  }
}
