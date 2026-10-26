package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.codec.ResultCodec;
import com.prlens.llm.LlmClient;
import com.prlens.llm.LlmRequest;
import com.prlens.llm.LlmResponse;
import com.prlens.model.Category;
import com.prlens.model.Configuration;
import com.prlens.model.Finding;
import com.prlens.model.ReviewResult;
import com.prlens.model.Severity;
import com.prlens.review.ReviewEngine;
import com.prlens.testkit.FixtureLoader;
import com.prlens.testkit.FixtureLoader.Fixture;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * 골든 픽스처의 스냅샷과 컨텍스트를 엔진에 넣고 {@link CliPipeline#emit}까지 실행해 stdout 바이트와 종료 코드를 비교한다 (요구사항 14.7,
 * 15.7, 17.9, 17.10, 22.8). {@code PrFetcher}와 {@code ContextCollector}는 거치지 않는다.
 */
class GoldenPipelineTest {

  private static final Set<String> INJECTIONS =
      Set.of("inject-diff-line", "inject-title", "inject-body", "inject-closing-tag");

  private final ResultCodec codec = new ResultCodec();

  /** 한 번 실행한 결과. */
  private record Run(ReviewResult result, byte[] stdout, byte[] stderr, int exitCode) {

    String stdoutText() {
      return new String(stdout, StandardCharsets.UTF_8);
    }
  }

  /** 픽스처의 고정 응답을 호출 순서대로 돌려주고, 호출 수가 다르면 실패한다. */
  private static final class ReplayLlmClient implements LlmClient {
    private final List<LlmResponse> responses;
    private int next;

    ReplayLlmClient(List<LlmResponse> responses) {
      this.responses = new ArrayList<>(responses);
    }

    @Override
    public LlmResponse send(LlmRequest request) {
      if (next >= responses.size()) {
        throw new AssertionError("고정 응답보다 호출이 많습니다: " + (next + 1));
      }
      return responses.get(next++);
    }

    int calls() {
      return next;
    }
  }

  private Run run(String name, OutputFormat format) {
    Fixture fixture = FixtureLoader.load(name);
    Configuration config = Configuration.defaults();
    ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
    ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    StderrReporter reporter =
        new StderrReporter(new PrintStream(errBytes, true, StandardCharsets.UTF_8));
    ReplayLlmClient llm = new ReplayLlmClient(fixture.llmResponses());

    ReviewResult result =
        new ReviewEngine(llm, reporter).review(fixture.snapshot(), fixture.context(), config);
    int exit = CliPipeline.emit(result, fixture.context(), config, format, reporter, out);

    assertThat(llm.calls()).as(name + " 호출 수").isEqualTo(fixture.llmResponses().size());
    return new Run(result, outBytes.toByteArray(), errBytes.toByteArray(), exit);
  }

  @Test
  void markdownStdoutIsByteIdenticalToExpected() {
    for (String name : FixtureLoader.names()) {
      Run run = run(name, OutputFormat.MARKDOWN);

      assertThat(run.stdout())
          .as(name)
          .isEqualTo(FixtureLoader.load(name).expectedMarkdown().getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  void jsonStdoutIsExpectedResultPlusNewline() {
    for (String name : FixtureLoader.names()) {
      Run run = run(name, OutputFormat.JSON);

      assertThat(run.stdout())
          .as(name)
          .isEqualTo(
              (FixtureLoader.load(name).expectedResultJson() + "\n")
                  .getBytes(StandardCharsets.UTF_8));
    }
  }

  @Test
  void jsonStdoutIsReadableByResultCodec() {
    for (String name : FixtureLoader.names()) {
      Run run = run(name, OutputFormat.JSON);
      String text = run.stdoutText();

      ReviewResult read = codec.read(text);

      assertThat(read).as(name).isEqualTo(run.result());
      assertThat(codec.write(read) + "\n").as(name).isEqualTo(text);
    }
  }

  @Test
  void injectionFixturesExitWithOneAndKeepSecurityAndBlocker() {
    for (String name : INJECTIONS) {
      for (OutputFormat format : OutputFormat.values()) {
        Run run = run(name, format);

        assertThat(run.exitCode()).as(name + " " + format).isEqualTo(1);
        assertThat(run.result().findings())
            .as(name)
            .extracting(Finding::category)
            .contains(Category.SECURITY);
        assertThat(run.result().findings())
            .as(name)
            .extracting(Finding::severity)
            .contains(Severity.BLOCKER);
      }
    }
  }

  @Test
  void nonInjectionFixturesExitWithZero() {
    for (String name : FixtureLoader.names()) {
      if (INJECTIONS.contains(name)) {
        continue;
      }
      for (OutputFormat format : OutputFormat.values()) {
        assertThat(run(name, format).exitCode()).as(name + " " + format).isZero();
      }
    }
  }

  @Test
  void stdoutIsUtf8WithoutBomOrCarriageReturn() {
    for (String name : FixtureLoader.names()) {
      for (OutputFormat format : OutputFormat.values()) {
        byte[] stdout = run(name, format).stdout();

        assertThat(stdout).as(name + " " + format).isNotEmpty();
        assertThat(stdout[0]).as(name + " BOM 첫 바이트").isNotEqualTo((byte) 0xEF);
        assertThat(stdout).as(name + " " + format + " CR").doesNotContain((byte) '\r');
      }
    }
  }
}
