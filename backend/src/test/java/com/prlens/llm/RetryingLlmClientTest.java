package com.prlens.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.support.NonRetryableApiException;
import com.prlens.support.RetriesExhaustedException;
import com.prlens.support.RetryAfterTooLongException;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryPolicy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

// 작업 13.2: 가짜 LlmClient와 실제 RetryExecutor. 대기는 기록만 하고 실제로 기다리지 않는다
class RetryingLlmClientTest {

  private static final LlmResponse OK = new LlmResponse("end_turn", "{}", new LlmUsage(1, 2, 3, 4));
  private static final LlmRequest REQUEST = new LlmRequest("m", 1, "medium", "s", "c", "d", "{}");

  private static final class ScriptedLlm implements LlmClient {
    final Queue<Supplier<LlmResponse>> script = new ArrayDeque<>();
    int calls;

    ScriptedLlm fail(LlmApiException e) {
      script.add(
          () -> {
            throw e;
          });
      return this;
    }

    ScriptedLlm succeed() {
      script.add(() -> OK);
      return this;
    }

    @Override
    public LlmResponse send(LlmRequest request) {
      calls++;
      return script.remove().get();
    }
  }

  private final List<Duration> waits = new ArrayList<>();
  private final List<String> retried = new ArrayList<>();

  private RetryingLlmClient client(ScriptedLlm llm, int maxRetries) {
    RetryExecutor executor =
        new RetryExecutor(
            new RetryPolicy(maxRetries),
            waits::add,
            (api, next, max, status, wait) -> retried.add(api + ":" + status));
    return new RetryingLlmClient(llm, executor);
  }

  private static LlmApiException http(int status, String retryAfter) {
    return new LlmApiException("http " + status, status, String.valueOf(status), retryAfter, null);
  }

  private static LlmApiException network(String kind) {
    return new LlmApiException(kind, null, kind, null, null);
  }

  // 요구사항 21.1, 21.7
  @Test
  void retriesOn429With503AndTimeoutThenSucceeds() {
    ScriptedLlm llm =
        new ScriptedLlm()
            .fail(http(429, null))
            .fail(http(503, null))
            .fail(network("timeout"))
            .succeed();

    LlmResponse response = client(llm, 3).send(REQUEST);

    assertThat(response).isEqualTo(OK);
    assertThat(llm.calls).isEqualTo(4);
    assertThat(waits)
        .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
    assertThat(retried).containsExactly("Claude:429", "Claude:503", "Claude:timeout");
  }

  @Test
  void connectionErrorIsRetried() {
    ScriptedLlm llm = new ScriptedLlm().fail(network("connection_error")).succeed();

    assertThat(client(llm, 3).send(REQUEST)).isEqualTo(OK);
    assertThat(retried).containsExactly("Claude:connection_error");
  }

  // 요구사항 21.4
  @Test
  void passesRetryAfterRawAndWaitsExactlyThatLong() {
    ScriptedLlm llm = new ScriptedLlm().fail(http(429, "7")).succeed();

    client(llm, 3).send(REQUEST);

    assertThat(waits).containsExactly(Duration.ofSeconds(7));
  }

  // 주의(3 리뷰): 실행기는 ASCII 숫자만 유효로 본다. 가공하지 않고 넘기므로 지수 백오프로 간다
  @Test
  void nonNumericOrPaddedRetryAfterFallsBackToBackoff() {
    ScriptedLlm llm =
        new ScriptedLlm()
            .fail(http(429, " 7"))
            .fail(http(429, "1.5"))
            .fail(http(429, "+5"))
            .succeed();

    client(llm, 3).send(REQUEST);

    assertThat(waits)
        .containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2), Duration.ofSeconds(4));
  }

  // 요구사항 21.8
  @Test
  void other4xxFailsImmediatelyAsNonRetryableWithApiNameClaude() {
    ScriptedLlm llm = new ScriptedLlm().fail(http(400, null)).succeed();

    assertThatThrownBy(() -> client(llm, 3).send(REQUEST))
        .isInstanceOfSatisfying(
            NonRetryableApiException.class,
            e -> {
              assertThat(e.api()).isEqualTo("Claude");
              assertThat(e.status()).isEqualTo(400);
            });
    assertThat(llm.calls).isEqualTo(1);
    assertThat(waits).isEmpty();
  }

  // 요구사항 21.5
  @Test
  void retryAfterOver60Throws() {
    ScriptedLlm llm = new ScriptedLlm().fail(http(429, "61")).succeed();

    assertThatThrownBy(() -> client(llm, 3).send(REQUEST))
        .isInstanceOfSatisfying(
            RetryAfterTooLongException.class,
            e -> {
              assertThat(e.api()).isEqualTo("Claude");
              assertThat(e.seconds()).isEqualTo(61);
            });
    assertThat(llm.calls).isEqualTo(1);
  }

  // 요구사항 21.6
  @Test
  void exhaustedRetriesCarryApiNameStatusAndAttempts() {
    ScriptedLlm llm =
        new ScriptedLlm().fail(http(503, null)).fail(http(503, null)).fail(http(503, null));

    assertThatThrownBy(() -> client(llm, 2).send(REQUEST))
        .isInstanceOfSatisfying(
            RetriesExhaustedException.class,
            e -> {
              assertThat(e.api()).isEqualTo("Claude");
              assertThat(e.lastStatus()).isEqualTo("503");
              assertThat(e.attempts()).isEqualTo(3);
            });
  }

  @Test
  void exhaustedNetworkErrorReportsKindNotNumber() {
    ScriptedLlm llm = new ScriptedLlm().fail(network("timeout")).fail(network("timeout"));

    assertThatThrownBy(() -> client(llm, 1).send(REQUEST))
        .isInstanceOfSatisfying(
            RetriesExhaustedException.class, e -> assertThat(e.lastStatus()).isEqualTo("timeout"));
  }

  // 재시도 대상이 아닌 상태 코드 없는 오류(응답 해석 실패 등)는 재시도 없이 그대로 던진다
  @Test
  void statuslessNonNetworkErrorPropagatesUnchanged() {
    LlmApiException invalid = new LlmApiException("bad body", null, "invalid_response", null, null);
    ScriptedLlm llm = new ScriptedLlm().fail(invalid).succeed();

    assertThatThrownBy(() -> client(llm, 3).send(REQUEST)).isSameAs(invalid);
    assertThat(llm.calls).isEqualTo(1);
  }
}
