package com.prlens.testkit;

import com.prlens.support.Attempt;
import java.time.Duration;
import java.util.List;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.Tuple;

/** 재시도 실행기에 넣을 응답 수열 생성기 (design.md "속성 기반 테스트 규칙"의 "응답 수열"). */
public final class AttemptArbitraries {

  private AttemptArbitraries() {}

  /** 길이 0~12의 응답 수열. 끝까지 실패하는 경우가 나오도록 재시도 대상 응답의 비중이 높다. */
  public static Arbitrary<List<Attempt<String>>> sequences() {
    return attempts().list().ofMinSize(0).ofMaxSize(12);
  }

  public static Arbitrary<Attempt<String>> attempts() {
    Arbitrary<Attempt<String>> success = Arbitraries.just(new Attempt.Success<>("ok"));
    // "api"는 테스트가 execute에 넘기는 이름과 일부러 다르게 둔다. 실행기가 Fatal.api()를 쓰면 드러나게 하려는 것이다
    Arbitrary<Attempt<String>> fatal =
        Arbitraries.integers()
            .between(400, 499)
            .filter(status -> status != 429)
            .map(status -> new Attempt.Fatal<>("api", status));
    Arbitrary<Attempt<String>> retryable =
        Combinators.combine(statuses(), retryAfters(), delays())
            .as((status, retryAfter, delay) -> new Attempt.Retryable<>(status, retryAfter, delay));
    return Arbitraries.frequencyOf(
        Tuple.of(1, success), Tuple.of(1, fatal), Tuple.of(12, retryable));
  }

  private static Arbitrary<String> statuses() {
    return Arbitraries.of("429", "500", "502", "503", "529", "403", "connect_error", "timeout");
  }

  /** 없음, 유효한 값(0~60), 60 초과, {@code long}을 넘는 숫자열, 무효 값. */
  private static Arbitrary<String> retryAfters() {
    return Arbitraries.frequencyOf(
        Tuple.of(4, Arbitraries.<String>just(null)),
        Tuple.of(3, Arbitraries.integers().between(0, 60).map(String::valueOf)),
        Tuple.of(1, Arbitraries.integers().between(61, 100_000).map(String::valueOf)),
        Tuple.of(1, Arbitraries.of("9223372036854775808", "99999999999999999999")),
        Tuple.of(
            2,
            Arbitraries.of(
                "-1", "1.5", "Wed, 21 Oct 2026 07:28:00 GMT", "", " 5", "+5", "abc", "5s")));
  }

  /** 호출자가 정한 대기 시간. 없음의 비중이 높고, 음수와 60초 경계를 포함한다. */
  private static Arbitrary<Duration> delays() {
    return Arbitraries.frequencyOf(
        Tuple.of(6, Arbitraries.<Duration>just(null)),
        Tuple.of(2, Arbitraries.longs().between(-5_000, 120_000).map(Duration::ofMillis)),
        Tuple.of(
            1,
            Arbitraries.of(Duration.ZERO, Duration.ofMillis(60_000), Duration.ofMillis(60_001))));
  }
}
