package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.llm.LlmUsage;
import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CostCalculatorTest {

  private static final ModelPricing PRICING =
      Configuration.defaults().pricing().get("claude-opus-5-5");

  // 요구사항 20.2: 토큰 네 종류에 각 단가를 곱해 합한다
  @Test
  void sumsFourTokenKindsTimesPricesPerMillion() {
    LlmUsage usage = new LlmUsage(29_400, 1_150, 7_200, 14_400);

    // 29,400×4 + 1,150×20 + 7,200×5 + 14,400×0.2 = 117,600 + 23,000 + 36,000 + 2,880 = 179,480 →
    // /1e6
    assertThat(CostCalculator.estimate(usage, PRICING)).isEqualByComparingTo("0.1795");
  }

  @Test
  void roundsHalfUpAtFourthDecimal() {
    ModelPricing pricing =
        new ModelPricing(
            new BigDecimal("1"), new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("0"));

    assertThat(CostCalculator.estimate(new LlmUsage(50, 0, 0, 0), pricing).toPlainString())
        .isEqualTo("0.0001");
    assertThat(CostCalculator.estimate(new LlmUsage(49, 0, 0, 0), pricing).toPlainString())
        .isEqualTo("0.0000");
  }

  // 요구사항 20.5: 단가가 없으면 알 수 없음(null)
  @Test
  void returnsNullWithoutPricing() {
    assertThat(CostCalculator.estimate(new LlmUsage(1, 1, 1, 1), null)).isNull();
  }
}
