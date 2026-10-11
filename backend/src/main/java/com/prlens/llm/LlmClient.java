package com.prlens.llm;

/** Claude API 호출부. HTTP 오류와 네트워크 오류는 {@link LlmApiException}으로 던진다. */
public interface LlmClient {
  LlmResponse send(LlmRequest request);
}
