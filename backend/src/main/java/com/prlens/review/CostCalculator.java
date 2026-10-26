package com.prlens.review;

import com.prlens.llm.LlmUsage;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 추정 비용을 계산하는 순수 함수 (요구사항 20.2, 20.5). P2가 실패한 Review_Run의 비용을 계산할 때도 쓴다. 분할 모드의 합은 Chunk별로 반올림한 값을
 * 더해야 한다.
 */
public final class CostCalculator {

  private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

  private CostCalculator() {}

  /**
   * @return Σ(토큰 × 단가 / 1,000,000)을 소수 넷째 자리로 HALF_UP 반올림한 값. 단가가 없으면 null
   */
  public static BigDecimal estimate(LlmUsage usage, ModelPricing pricing) {
    if (pricing == null) {
      return null;
    }
    return cost(usage.inputTokens(), pricing.inputPerMTok())
        .add(cost(usage.outputTokens(), pricing.outputPerMTok()))
        .add(cost(usage.cacheWriteTokens(), pricing.cacheWritePerMTok()))
        .add(cost(usage.cacheReadTokens(), pricing.cacheReadPerMTok()))
        .setScale(4, RoundingMode.HALF_UP);
  }

  // 100만으로 나누는 것은 항상 끝이 있는 소수라 반올림 없이 정확하다. 반올림은 합계에 한 번만 한다
  private static BigDecimal cost(long tokens, BigDecimal perMillion) {
    return BigDecimal.valueOf(tokens).multiply(perMillion).divide(MILLION);
  }
}
