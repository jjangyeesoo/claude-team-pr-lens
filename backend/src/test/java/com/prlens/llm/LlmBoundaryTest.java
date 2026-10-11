package com.prlens.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.support.PrLensException;
import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Claude API 호출부의 경계 (요구사항 17.9, design.md "공유 경계"). */
class LlmBoundaryTest {

  private static final LlmRequest REQUEST =
      new LlmRequest("claude-opus-5-5", 16_000, "medium", "system", "criteria", "data", "{}");

  // 요구사항 17.9: 네트워크와 API 키 없이 고정 응답으로 바꿔 끼울 수 있다
  @Test
  void llmClientCanBeReplacedWithFixedResponse() {
    LlmResponse fixed = new LlmResponse("end_turn", "{}", new LlmUsage(10, 20, 0, 5));
    LlmClient fake = request -> fixed;

    assertThat(fake.send(REQUEST)).isSameAs(fixed);
    assertThat(LlmClient.class.isInterface()).isTrue();
  }

  @Test
  void requestCarriesEveryFieldTheAdapterNeeds() {
    assertThat(REQUEST.model()).isEqualTo("claude-opus-5-5");
    assertThat(REQUEST.maxOutputTokens()).isEqualTo(16_000);
    assertThat(REQUEST.effort()).isEqualTo("medium");
    assertThat(REQUEST.system()).isEqualTo("system");
    assertThat(REQUEST.cachedUserBlock()).isEqualTo("criteria");
    assertThat(REQUEST.dataUserBlock()).isEqualTo("data");
    assertThat(REQUEST.jsonSchema()).isEqualTo("{}");
  }

  // 같은 타입이 나란한 구성 요소는 순서가 바뀌어도 컴파일되므로 값으로 고정한다
  @Test
  void usageAndResponseKeepComponentOrder() {
    LlmUsage usage = new LlmUsage(10, 20, 30, 40);
    LlmResponse response = new LlmResponse("max_tokens", "{\"summary\":", usage);

    assertThat(usage.inputTokens()).isEqualTo(10);
    assertThat(usage.outputTokens()).isEqualTo(20);
    assertThat(usage.cacheWriteTokens()).isEqualTo(30);
    assertThat(usage.cacheReadTokens()).isEqualTo(40);
    assertThat(response.stopReason()).isEqualTo("max_tokens");
    assertThat(response.text()).isEqualTo("{\"summary\":");
    assertThat(response.usage()).isEqualTo(usage);
  }

  @Test
  void apiExceptionCarriesStatusErrorKindAndRetryAfter() {
    LlmApiException exception = new LlmApiException("과부하", 529, "529", "12", null);

    assertThat(exception).isInstanceOf(PrLensException.class).hasMessage("과부하");
    assertThat(exception.status()).isEqualTo(529);
    assertThat(exception.errorKind()).isEqualTo("529");
    assertThat(exception.retryAfter()).isEqualTo("12");
  }

  @Test
  void apiExceptionWithoutStatusMeansConnectionFailureOrTimeout() {
    IOException cause = new IOException("connection reset");
    LlmApiException exception = new LlmApiException("연결 실패", null, "connection", null, cause);

    assertThat(exception.status()).isNull();
    assertThat(exception.retryAfter()).isNull();
    assertThat(exception).hasCause(cause);
  }
}
