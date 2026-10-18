package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReviewCommandTest {

  private static final String URL = "https://github.com/o/r/pull/1";

  private final List<ReviewArgs> received = new ArrayList<>();
  private final ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
  private final ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
  private final PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
  private final PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8);

  private int run(String... args) {
    return PrLensMain.run(
        args,
        out,
        err,
        a -> {
          received.add(a);
          return 0;
        });
  }

  private void parse(String... args) {
    PrLensMain.execute(args, out, err, a -> 0);
  }

  private String stdout() {
    return outBytes.toString(StandardCharsets.UTF_8);
  }

  private String stderr() {
    return errBytes.toString(StandardCharsets.UTF_8);
  }

  @Test
  void formatDefaultsToMarkdown() {
    assertThat(run("review", URL)).isZero();

    assertThat(received).hasSize(1);
    assertThat(received.get(0).url()).isEqualTo(URL);
    assertThat(received.get(0).format()).isEqualTo(OutputFormat.MARKDOWN);
    assertThat(received.get(0).config()).isNull();
  }

  @Test
  void formatMarkdownAccepted() {
    run("review", URL, "--format", "markdown");

    assertThat(received.get(0).format()).isEqualTo(OutputFormat.MARKDOWN);
  }

  @Test
  void formatJsonAccepted() {
    run("review", URL, "--format", "json");
    run("review", URL, "--format=json");

    assertThat(received)
        .extracting(ReviewArgs::format)
        .containsExactly(OutputFormat.JSON, OutputFormat.JSON);
  }

  @Test
  void invalidFormatThrowsUsageException() {
    assertThatThrownBy(() -> parse("review", URL, "--format", "xml"))
        .isInstanceOf(UsageException.class)
        .hasMessageContaining("markdown")
        .hasMessageContaining("json")
        .hasMessageContaining("xml");
    assertThatThrownBy(() -> parse("review", URL, "--format", ""))
        .isInstanceOf(UsageException.class)
        .hasMessageContaining("markdown");
  }

  @Test
  void missingFormatValueThrowsUsageException() {
    assertThatThrownBy(() -> parse("review", URL, "--format"))
        .isInstanceOf(UsageException.class)
        .hasMessageContaining("markdown")
        .hasMessageContaining("json");
  }

  @Test
  void missingUrlThrowsUsageException() {
    assertThatThrownBy(() -> parse("review")).isInstanceOf(UsageException.class);
  }

  @Test
  void unknownOptionThrowsUsageException() {
    assertThatThrownBy(() -> parse("review", URL, "--nope")).isInstanceOf(UsageException.class);
  }

  @Test
  void configOptionKeepsOsPathAsGiven() {
    run("review", URL, "--config", "conf/my.yml");

    assertThat(received.get(0).config()).isEqualTo(Path.of("conf", "my.yml"));
  }

  @Test
  void runReturnsExitCode2OnUsageErrorWithEmptyStdout() {
    int code = run("review", URL, "--format", "xml");

    assertThat(code).isEqualTo(2);
    assertThat(stdout()).isEmpty();
    assertThat(stderr()).contains("markdown", "json").endsWith("\n");
    assertThat(received).isEmpty();
  }

  @Test
  void runReturnsRunnerExitCode() {
    int code = PrLensMain.run(new String[] {"review", URL}, out, err, a -> 3);

    assertThat(code).isEqualTo(3);
  }

  @Test
  void usageGoesThroughSuppliedUtf8Writers() {
    int code = run("review", "--help");

    assertThat(code).isZero();
    assertThat(stderr()).isEmpty();
    // 한글이 대체 문자(U+FFFD)로 바뀌지 않고 넘겨 준 UTF-8 writer로 나간다
    assertThat(stdout()).contains("리뷰").doesNotContain("�");
    assertThat(received).isEmpty();
  }
}
