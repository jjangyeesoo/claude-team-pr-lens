package com.prlens.model;

import java.math.BigDecimal;
import java.util.Objects;

/** 토큰 100만 개당 USD 단가. */
public record ModelPricing(
    BigDecimal inputPerMTok,
    BigDecimal outputPerMTok,
    BigDecimal cacheWritePerMTok,
    BigDecimal cacheReadPerMTok) {
  public ModelPricing {
    inputPerMTok = Objects.requireNonNull(inputPerMTok, "inputPerMTok").stripTrailingZeros();
    outputPerMTok = Objects.requireNonNull(outputPerMTok, "outputPerMTok").stripTrailingZeros();
    cacheWritePerMTok =
        Objects.requireNonNull(cacheWritePerMTok, "cacheWritePerMTok").stripTrailingZeros();
    cacheReadPerMTok =
        Objects.requireNonNull(cacheReadPerMTok, "cacheReadPerMTok").stripTrailingZeros();
  }
}
