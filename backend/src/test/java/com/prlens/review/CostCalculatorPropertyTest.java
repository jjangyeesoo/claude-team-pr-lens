package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.llm.LlmUsage;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import java.math.BigInteger;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

// 작업 11.7: Property 40(추정 비용 계산)
// 기댓값은 구현과 다른 방법으로 구한다: 단가를 100만분의 1 USD 정수로 두고 BigInteger로 합한 뒤 정수 나눗셈으로 반올림한다.
class CostCalculatorPropertyTest {

  private record Case(LlmUsage usage, long[] priceMicros) {}

  @Provide
  Arbitrary<Case> cases() {
    Arbitrary<Long> tokens = Arbitraries.longs().between(0, 5_000_000_000L);
    // 단가는 0~100 USD(100만 토큰당)를 소수 여섯째 자리까지 만든다
    Arbitrary<Long> micros = Arbitraries.longs().between(0, 100_000_000L);
    return Combinators.combine(tokens, tokens, tokens, tokens, micros, micros, micros, micros)
        .as(
            (in, out, write, read, pIn, pOut, pWrite, pRead) ->
                new Case(
                    new LlmUsage(in, out, write, read), new long[] {pIn, pOut, pWrite, pRead}));
  }

  // Feature: pr-lens-p1-cli, Property 40: 추정 비용 계산
  // 예시로 확인: CostCalculatorTest.roundsHalfUpAtFourthDecimal (HALF_UP 동률은 생성기에서 드물게 나온다)
  @Property(tries = 300)
  void estimateEqualsExactSumRoundedHalfUpAtFourthDecimal(@ForAll("cases") Case c) {
    LlmUsage usage = c.usage();
    long[] tokens = {
      usage.inputTokens(), usage.outputTokens(), usage.cacheWriteTokens(), usage.cacheReadTokens()
    };
    ModelPricing pricing =
        new ModelPricing(
            micros(c.priceMicros()[0]),
            micros(c.priceMicros()[1]),
            micros(c.priceMicros()[2]),
            micros(c.priceMicros()[3]));

    BigInteger tokensTimesMicros = BigInteger.ZERO;
    for (int i = 0; i < 4; i++) {
      tokensTimesMicros =
          tokensTimesMicros.add(
              BigInteger.valueOf(tokens[i]).multiply(BigInteger.valueOf(c.priceMicros()[i])));
    }
    // USD = tokensTimesMicros / 1e6 / 1e6. 1e-4 USD 단위로는 x / 1e8이고 HALF_UP은 (2x + d) / 2d, d = 1e8
    BigInteger d = BigInteger.valueOf(100_000_000L);
    BigInteger units =
        tokensTimesMicros.multiply(BigInteger.TWO).add(d).divide(d.multiply(BigInteger.TWO));
    BigDecimal expected = new BigDecimal(units).movePointLeft(4);

    BigDecimal actual = CostCalculator.estimate(usage, pricing);

    assertThat(actual.scale()).isEqualTo(4);
    assertThat(actual).isEqualByComparingTo(expected);
    assertThat(actual.signum()).isGreaterThanOrEqualTo(0);
  }

  private static BigDecimal micros(long value) {
    return BigDecimal.valueOf(value, 6);
  }
}
