package com.prlens.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.testkit.AttemptArbitraries;
import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

/** `RetryExecutor`의 속성 테스트 (design.md Property 42). */
class RetryPolicyPropertyTest {

  private static final Duration MAX_WAIT = Duration.ofSeconds(60);

  @Provide
  Arbitrary<List<Attempt<String>>> attemptSequences() {
    return AttemptArbitraries.sequences();
  }

  // Feature: pr-lens-p1-cli, Property 42: 재시도 정책
  @Property
  void retryPolicyHoldsForAnyResponseSequence(
      @ForAll @IntRange(min = 0, max = 10) int maxRetries,
      @ForAll("attemptSequences") List<Attempt<String>> outcomes) {
    Expected expected = expect(maxRetries, outcomes);
    List<Duration> slept = new ArrayList<>();
    List<String> retries = new ArrayList<>();
    int[] calls = {0};
    RetryExecutor executor =
        new RetryExecutor(
            new RetryPolicy(maxRetries),
            slept::add,
            (api, nextAttempt, maxAttempts, status, wait) ->
                retries.add(retryLine(api, nextAttempt, maxAttempts, status, wait)));

    String result;
    try {
      result = "value:" + executor.execute("API", () -> outcomeAt(outcomes, ++calls[0]));
    } catch (NonRetryableApiException e) {
      result = "nonRetryable:" + e.api() + ":" + e.status();
    } catch (RetryAfterTooLongException e) {
      result = "tooLong:" + e.api() + ":" + e.seconds();
    } catch (RetriesExhaustedException e) {
      result = "exhausted:" + e.api() + ":" + e.lastStatus() + ":" + e.attempts();
    }

    assertThat(calls[0]).isLessThanOrEqualTo(maxRetries + 1).isEqualTo(expected.calls);
    assertThat(result).isEqualTo(expected.result);
    assertThat(slept).isEqualTo(expected.waits);
    assertThat(retries).isEqualTo(expected.retries);
  }

  /** 수열이 끝나면 성공을 돌려준다. 수열 안의 성공("ok")과 구분되도록 값을 다르게 둔다. */
  private static Attempt<String> outcomeAt(List<Attempt<String>> outcomes, int attempt) {
    return attempt <= outcomes.size()
        ? outcomes.get(attempt - 1)
        : new Attempt.Success<>("default");
  }

  private static String retryLine(
      String api, int nextAttempt, int maxAttempts, String status, Duration wait) {
    return api + " " + nextAttempt + "/" + maxAttempts + " " + status + " " + wait;
  }

  private static final class Expected {
    private final List<Duration> waits = new ArrayList<>();
    private final List<String> retries = new ArrayList<>();
    private int calls;
    private String result;
  }

  /** 구현과 따로 쓴 기대값 계산. 429 외 4xx와 60초를 넘는 대기는 남은 시도와 관계없이 멈춘다. */
  private static Expected expect(int maxRetries, List<Attempt<String>> outcomes) {
    Expected expected = new Expected();
    for (int attempt = 1; expected.result == null; attempt++) {
      Attempt<String> outcome = outcomeAt(outcomes, attempt);
      expected.calls++;
      if (outcome instanceof Attempt.Success<String> success) {
        expected.result = "value:" + success.value();
      } else if (outcome instanceof Attempt.Fatal<String> fatal) {
        expected.result = "nonRetryable:API:" + fatal.status();
      } else {
        Attempt.Retryable<String> retryable = (Attempt.Retryable<String>) outcome;
        Duration wait = expectedWait(attempt, retryable);
        if (wait.compareTo(MAX_WAIT) > 0) {
          long seconds = wait.getNano() > 0 ? wait.toSeconds() + 1 : wait.toSeconds();
          expected.result = "tooLong:API:" + seconds;
        } else if (attempt == maxRetries + 1) {
          expected.result = "exhausted:API:" + retryable.statusOrError() + ":" + attempt;
        } else {
          expected.waits.add(wait);
          expected.retries.add(
              retryLine("API", attempt + 1, maxRetries + 1, retryable.statusOrError(), wait));
        }
      }
    }
    return expected;
  }

  /** 호출자가 정한 값(음수는 0) → 숫자만으로 된 {@code retry-after} → min(2^(k-1), 30)초. */
  private static Duration expectedWait(int k, Attempt.Retryable<String> retryable) {
    if (retryable.delay() != null) {
      return retryable.delay().isNegative() ? Duration.ZERO : retryable.delay();
    }
    String retryAfter = retryable.retryAfter();
    if (retryAfter != null
        && !retryAfter.isEmpty()
        && retryAfter.chars().allMatch(c -> c >= '0' && c <= '9')) {
      BigInteger seconds = new BigInteger(retryAfter);
      return seconds.bitLength() <= 63
          ? Duration.ofSeconds(seconds.longValueExact())
          : Duration.ofSeconds(Long.MAX_VALUE);
    }
    return Duration.ofSeconds(Math.min(30, BigInteger.TWO.pow(k - 1).longValueExact()));
  }
}
