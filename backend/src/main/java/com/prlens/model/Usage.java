package com.prlens.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * @param estimatedCostUsd 소수 넷째 자리로 맞춘 추정 비용. 모델의 단가가 없으면 null
 */
public record Usage(
    long inputTokens,
    long outputTokens,
    long cacheWriteTokens,
    long cacheReadTokens,
    String model,
    BigDecimal estimatedCostUsd) {
  public Usage {
    requireNotNegative(inputTokens, "inputTokens");
    requireNotNegative(outputTokens, "outputTokens");
    requireNotNegative(cacheWriteTokens, "cacheWriteTokens");
    requireNotNegative(cacheReadTokens, "cacheReadTokens");
    Objects.requireNonNull(model, "model");
    if (estimatedCostUsd != null) {
      estimatedCostUsd = estimatedCostUsd.setScale(4, RoundingMode.HALF_UP);
    }
  }

  private static void requireNotNegative(long tokens, String name) {
    if (tokens < 0) {
      throw new IllegalArgumentException(name + ": 음수일 수 없습니다: " + tokens);
    }
  }
}
