package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.diff.LineRanges;
import com.prlens.llm.AnthropicLlmClient;
import com.prlens.llm.LlmRequest;
import com.prlens.llm.LlmResponse;
import com.prlens.llm.RetryingLlmClient;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.DiffLine;
import com.prlens.model.FileStatus;
import com.prlens.model.Finding;
import com.prlens.model.Hunk;
import com.prlens.model.LineKind;
import com.prlens.model.PatchContent;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewMode;
import com.prlens.model.Revision;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * 실제 Claude API에 대한 수동 스모크 (작업 13.4). {@code ANTHROPIC_API_KEY}와 {@code PRLENS_SMOKE=1}이 모두 있을 때만
 * 돈다. CI에서는 돌지 않는다(요구사항 22.7). 요청 1건을 실제로 보내므로 비용이 든다.
 *
 * <p>{@code ResponseParser}가 패키지 안에서만 보이므로 이 패키지에 둔다. 키는 환경변수에서만 읽고 출력하지 않는다.
 *
 * <p>실행(PowerShell, 키는 이 프로세스에서만): {@code $env:PRLENS_SMOKE='1'; .\gradlew.bat test --tests
 * com.prlens.review.AnthropicSmokeTest --no-daemon -i}
 */
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".*\\S.*")
@EnabledIfEnvironmentVariable(named = "PRLENS_SMOKE", matches = "1")
class AnthropicSmokeTest {

  private static final String RULE_PATH = ".claude/rules/backend/logging.md";

  private static ChangedFile controller() {
    List<DiffLine> lines =
        List.of(
            new DiffLine(LineKind.ADDED, "package com.example;", false),
            new DiffLine(LineKind.ADDED, "", false),
            new DiffLine(LineKind.ADDED, "public class UserController {", false),
            new DiffLine(LineKind.ADDED, "  public String find(String id) {", false),
            new DiffLine(LineKind.ADDED, "    System.out.println(\"find user \" + id);", false),
            new DiffLine(LineKind.ADDED, "    return id;", false),
            new DiffLine(LineKind.ADDED, "  }", false),
            new DiffLine(LineKind.ADDED, "}", false));
    return new ChangedFile(
        "src/main/java/com/example/UserController.java",
        null,
        FileStatus.ADDED,
        lines.size(),
        0,
        new PatchContent.Parsed(List.of(new Hunk(0, 0, 1, lines.size(), "", lines))));
  }

  @Test
  void realCallReturnsSchemaConformingResponse() {
    String key = System.getenv("ANTHROPIC_API_KEY");
    Configuration config = Configuration.defaults();
    ChangedFile file = controller();
    PullRequestSnapshot snapshot =
        new PullRequestSnapshot(
            new RepoRef("o", "r"),
            1,
            "사용자 조회 추가",
            "UserController를 추가한다",
            "b".repeat(40),
            "h".repeat(40),
            1,
            List.of(file));
    ReviewContext context =
        new ReviewContext(
            List.of(
                new ContextFile(
                    RULE_PATH,
                    "# 로깅 규칙\n- 운영 코드에서 System.out.println을 쓰지 않는다. SLF4J 로거를 쓴다\n",
                    ContextSource.RULE,
                    Revision.BASE),
                new ContextFile(
                    "CLAUDE.md",
                    "# Project\nJava 17 Spring Boot 서비스다.\n",
                    ContextSource.CLAUDE_MD,
                    Revision.BASE)));
    LlmRequest request =
        PromptBuilder.build(ReviewMode.SINGLE, List.of(file), snapshot, context, config);

    RetryExecutor executor =
        new RetryExecutor(
            new RetryPolicy(config.maxRetries()),
            wait -> {
              try {
                Thread.sleep(wait.toMillis());
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            },
            (api, next, max, status, wait) ->
                System.out.println("[smoke] 재시도 " + api + " " + next + "/" + max + " " + status));
    RetryingLlmClient llm = new RetryingLlmClient(new AnthropicLlmClient(key), executor);

    long started = System.nanoTime();
    LlmResponse response = llm.send(request);
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    ParsedResponse parsed = new ResponseParser().parse(response);
    List<Finding> findings =
        FindingValidator.apply(
            parsed.findings(), context, Map.of(file.path(), LineRanges.of(hunks(file))));

    System.out.println("[smoke] 응답 시간 " + elapsedMillis + " ms");
    System.out.println("[smoke] stop_reason=" + response.stopReason());
    System.out.println("[smoke] usage=" + response.usage());
    System.out.println(
        "[smoke] 추정 비용(USD)="
            + CostCalculator.estimate(response.usage(), config.pricing().get(config.model())));
    System.out.println("[smoke] summary=" + parsed.summary());
    for (Finding f : findings) {
      System.out.println(
          "[smoke] finding "
              + f.severity()
              + " "
              + f.file()
              + ":"
              + f.line()
              + " basis="
              + f.basis().type()
              + "/"
              + f.basis().ref()
              + " verdict="
              + f.verdict()
              + (f.demotion() == null ? "" : " demoted-from=" + f.demotion().originalRef()));
    }

    assertThat(response.stopReason()).isEqualTo("end_turn");
    assertThat(parsed.incompleteReason()).isNull();
    assertThat(response.usage().inputTokens()).isPositive();
    assertThat(response.usage().outputTokens()).isPositive();
  }

  private static List<Hunk> hunks(ChangedFile file) {
    return ((PatchContent.Parsed) file.patch()).hunks();
  }
}
