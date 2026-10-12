package com.prlens.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** `RetryExecutor` (design.md "재시도 정책", 요구사항 1.13, 21). */
class RetryExecutorTest {

  private final List<Duration> slept = new ArrayList<>();
  private final List<String> retries = new ArrayList<>();

  private RetryExecutor executor(int maxRetries) {
    return new RetryExecutor(
        new RetryPolicy(maxRetries),
        slept::add,
        (api, nextAttempt, maxAttempts, status, wait) ->
            retries.add(api + " " + nextAttempt + "/" + maxAttempts + " " + status + " " + wait));
  }

  /** 준비한 응답을 순서대로 돌려주고 호출 횟수를 센다. */
  private static final class Script implements Supplier<Attempt<String>> {
    private final Deque<Attempt<String>> outcomes = new ArrayDeque<>();
    private int calls;

    @SafeVarargs
    Script(Attempt<String>... outcomes) {
      this.outcomes.addAll(List.of(outcomes));
    }

    @Override
    public Attempt<String> get() {
      calls++;
      if (outcomes.isEmpty()) {
        throw new AssertionError("준비한 응답보다 많이 호출했습니다: " + calls + "번째");
      }
      return outcomes.poll();
    }
  }

  private static Attempt<String> ok() {
    return new Attempt.Success<>("ok");
  }

  private static Attempt<String> failed(String statusOrError) {
    return new Attempt.Retryable<>(statusOrError, null, null);
  }

  private static Attempt<String> failedWithRetryAfter(String retryAfter) {
    return new Attempt.Retryable<>("429", retryAfter, null);
  }

  private static Attempt<String> rateLimited(String retryAfter, Duration delay) {
    return new Attempt.Retryable<>("403", retryAfter, delay);
  }

  private static List<Duration> seconds(long... values) {
    List<Duration> durations = new ArrayList<>();
    for (long value : values) {
      durations.add(Duration.ofSeconds(value));
    }
    return durations;
  }

  @Test
  void retriesUpToMaxRetriesThenSucceeds() {
    Script script = new Script(failed("429"), failed("503"), failed("500"), ok());

    String value = executor(3).execute("GitHub", script);

    assertThat(value).isEqualTo("ok");
    assertThat(script.calls).isEqualTo(4);
    assertThat(slept).isEqualTo(seconds(1, 2, 4));
  }

  @Test
  void zeroRetriesCallsOnce() {
    Script script = new Script(failed("500"));

    RetriesExhaustedException exception =
        catchThrowableOfType(
            RetriesExhaustedException.class, () -> executor(0).execute("GitHub", script));

    assertThat(exception.attempts()).isEqualTo(1);
    assertThat(script.calls).isEqualTo(1);
    assertThat(slept).isEmpty();
  }

  @Test
  void networkErrorAndTimeoutRetryLikeServerError() {
    Script script = new Script(failed("connect_error"), failed("timeout"), ok());

    String value = executor(3).execute("GitHub", script);

    assertThat(value).isEqualTo("ok");
    assertThat(slept).isEqualTo(seconds(1, 2));
    assertThat(retries).containsExactly("GitHub 2/4 connect_error PT1S", "GitHub 3/4 timeout PT2S");
  }

  @Test
  void backoffDoublesFromOneSecondAndCapsAtThirty() {
    Script script =
        new Script(
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            failed("503"),
            ok());

    executor(10).execute("Claude", script);

    assertThat(slept).isEqualTo(seconds(1, 2, 4, 8, 16, 30, 30, 30, 30, 30));
  }

  @Test
  void validRetryAfterReplacesBackoff() {
    Script script =
        new Script(
            failedWithRetryAfter("0"), failedWithRetryAfter("60"), failedWithRetryAfter("7"), ok());

    executor(3).execute("Claude", script);

    assertThat(slept).isEqualTo(seconds(0, 60, 7));
  }

  @Test
  void invalidRetryAfterFallsBackToBackoff() {
    Script script =
        new Script(
            failedWithRetryAfter("Wed, 21 Oct 2026 07:28:00 GMT"),
            failedWithRetryAfter("-1"),
            failedWithRetryAfter("1.5"),
            failedWithRetryAfter(""),
            ok());

    executor(4).execute("Claude", script);

    assertThat(slept).isEqualTo(seconds(1, 2, 4, 8));
  }

  @Test
  void retryAfterOverSixtyThrowsImmediately() {
    Script script = new Script(failedWithRetryAfter("61"));

    RetryAfterTooLongException exception =
        catchThrowableOfType(
            RetryAfterTooLongException.class, () -> executor(3).execute("Claude", script));

    assertThat(exception.api()).isEqualTo("Claude");
    assertThat(exception.seconds()).isEqualTo(61);
    assertThat(exception).hasMessageContaining("Claude").hasMessageContaining("61");
    assertThat(script.calls).isEqualTo(1);
    assertThat(slept).isEmpty();
    assertThat(retries).isEmpty();
  }

  @Test
  void retryAfterTooLargeForLongThrowsImmediately() {
    Script script = new Script(failedWithRetryAfter("99999999999999999999"));

    assertThatThrownBy(() -> executor(3).execute("Claude", script))
        .isInstanceOf(RetryAfterTooLongException.class);
    assertThat(slept).isEmpty();
  }

  @Test
  void tooLongIsCheckedBeforeLastAttempt() {
    Script script = new Script(failedWithRetryAfter("61"));

    assertThatThrownBy(() -> executor(0).execute("Claude", script))
        .isInstanceOf(RetryAfterTooLongException.class);
  }

  @Test
  void exhaustedCarriesApiLastStatusAndAttempts() {
    Script script = new Script(failed("503"), failed("503"), failed("503"), failed("timeout"));

    RetriesExhaustedException exception =
        catchThrowableOfType(
            RetriesExhaustedException.class, () -> executor(3).execute("GitHub", script));

    assertThat(exception.api()).isEqualTo("GitHub");
    assertThat(exception.lastStatus()).isEqualTo("timeout");
    assertThat(exception.attempts()).isEqualTo(4);
    assertThat(exception)
        .hasMessageContaining("GitHub")
        .hasMessageContaining("timeout")
        .hasMessageContaining("4");
    assertThat(script.calls).isEqualTo(4);
  }

  @Test
  void listenerGetsNextAttemptMaxAttemptsStatusAndWait() {
    Script script = new Script(failed("503"), failedWithRetryAfter("5"), ok());

    executor(2).execute("Claude", script);

    assertThat(retries).containsExactly("Claude 2/3 503 PT1S", "Claude 3/3 429 PT5S");
  }

  @Test
  void listenerIsNotCalledOnFinalFailure() {
    Script script = new Script(failed("503"), failed("503"));

    assertThatThrownBy(() -> executor(1).execute("Claude", script))
        .isInstanceOf(RetriesExhaustedException.class);

    assertThat(retries).hasSize(1);
    assertThat(slept).hasSize(1);
  }

  @Test
  void fatalThrowsNonRetryableWithoutRetry() {
    Script script = new Script(new Attempt.Fatal<>("GitHub", 422));

    NonRetryableApiException exception =
        catchThrowableOfType(
            NonRetryableApiException.class, () -> executor(3).execute("GitHub", script));

    assertThat(exception.api()).isEqualTo("GitHub");
    assertThat(exception.status()).isEqualTo(422);
    assertThat(exception).hasMessageContaining("GitHub").hasMessageContaining("422");
    assertThat(script.calls).isEqualTo(1);
    assertThat(slept).isEmpty();
  }

  @Test
  void callerExceptionPassesThrough() {
    PrLensException thrown = new PrLensException("GitHub 404") {};

    assertThatThrownBy(
            () ->
                executor(3)
                    .<String>execute(
                        "GitHub",
                        () -> {
                          throw thrown;
                        }))
        .isSameAs(thrown);
    assertThat(slept).isEmpty();
    assertThat(retries).isEmpty();
  }

  @Test
  void callerDelayWinsOverRetryAfterAndBackoff() {
    Script script = new Script(rateLimited("5", Duration.ofSeconds(42)), ok());

    executor(3).execute("GitHub", script);

    assertThat(slept).isEqualTo(seconds(42));
  }

  @Test
  void callerDelayOverSixtyThrowsImmediately() {
    Script script = new Script(rateLimited(null, Duration.ofMillis(60_001)));

    RetryAfterTooLongException exception =
        catchThrowableOfType(
            RetryAfterTooLongException.class, () -> executor(3).execute("GitHub", script));

    assertThat(exception.seconds()).isEqualTo(61);
    assertThat(script.calls).isEqualTo(1);
    assertThat(slept).isEmpty();
  }

  @Test
  void callerDelayOfExactlySixtyWaits() {
    Script script = new Script(rateLimited(null, Duration.ofSeconds(60)), ok());

    executor(3).execute("GitHub", script);

    assertThat(slept).isEqualTo(seconds(60));
  }

  @Test
  void negativeCallerDelayWaitsZero() {
    Script script = new Script(rateLimited(null, Duration.ofSeconds(-3)), ok());

    executor(3).execute("GitHub", script);

    assertThat(slept).isEqualTo(seconds(0));
  }
}
