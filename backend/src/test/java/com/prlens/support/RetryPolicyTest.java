package com.prlens.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

/** `RetryPolicy` (design.md "재시도 정책", 요구사항 21.1). */
class RetryPolicyTest {

  @Test
  void policyRejectsMaxRetriesOutsideZeroToTen() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetryPolicy(-1))
        .withMessageContaining("maxRetries");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> new RetryPolicy(11))
        .withMessageContaining("maxRetries");
    assertThat(new RetryPolicy(0).maxRetries()).isZero();
    assertThat(new RetryPolicy(10).maxRetries()).isEqualTo(10);
  }

  @Test
  void maxAttemptsIsFirstAttemptPlusRetries() {
    assertThat(new RetryPolicy(0).maxAttempts()).isEqualTo(1);
    assertThat(new RetryPolicy(3).maxAttempts()).isEqualTo(4);
  }
}
