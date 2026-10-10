package com.prlens.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.lang.reflect.Modifier;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** `support`의 경계 타입 (design.md "공유 경계", "예외 계층과 종료 코드"). */
class SupportTypesTest {

  private static final class SampleException extends PrLensException {
    SampleException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  @Test
  void prLensExceptionIsAbstractUncheckedAndNotSealed() {
    assertThat(Modifier.isAbstract(PrLensException.class.getModifiers())).isTrue();
    assertThat(PrLensException.class.isSealed()).isFalse();
    assertThat(RuntimeException.class).isAssignableFrom(PrLensException.class);
  }

  @Test
  void prLensExceptionKeepsMessageAndCause() {
    IllegalStateException cause = new IllegalStateException("원인");
    PrLensException exception = new SampleException("메시지", cause);

    assertThat(exception).hasMessage("메시지").hasCause(cause);
  }

  @Test
  void warningRequiresCodeAndMessage() {
    assertThatNullPointerException().isThrownBy(() -> new Warning(null, "m")).withMessage("code");
    assertThatNullPointerException()
        .isThrownBy(() -> new Warning("c", null))
        .withMessage("message");
    assertThat(new Warning("c", "m")).isEqualTo(new Warning("c", "m"));
  }

  @Test
  void attemptPermitsSuccessRetryableAndFatal() {
    assertThat(Attempt.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(
            Attempt.Success.class, Attempt.Retryable.class, Attempt.Fatal.class);
  }

  @Test
  void retryableAttemptMayOmitRetryAfterAndDelay() {
    Attempt.Retryable<String> byBackoff = new Attempt.Retryable<>("503", null, null);
    Attempt.Retryable<String> byCaller =
        new Attempt.Retryable<>("403", "30", Duration.ofSeconds(30));

    assertThat(byBackoff.retryAfter()).isNull();
    assertThat(byBackoff.delay()).isNull();
    assertThat(byCaller.delay()).isEqualTo(Duration.ofSeconds(30));
    assertThatNullPointerException()
        .isThrownBy(() -> new Attempt.Retryable<String>(null, null, null))
        .withMessage("statusOrError");
  }

  @Test
  void fatalAttemptRequiresApiName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Attempt.Fatal<String>(null, 422))
        .withMessage("api");
    assertThat(new Attempt.Fatal<String>("GitHub", 422).status()).isEqualTo(422);
  }
}
