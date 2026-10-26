package com.prlens.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prlens.support.SecretMasker;
import com.prlens.testkit.FakeClaudeServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 작업 13.1: 가짜 Claude 서버로 실제 HTTP 왕복을 확인한다
class AnthropicLlmClientTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final String KEY = "sk-ant-test-" + "SECRETVALUE123";

  // 키 순서가 알파벳 순이 아니어서, 직렬화가 순서를 바꾸면 드러난다
  private static final String SCHEMA =
      "{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"summary\",\"findings\"],"
          + "\"properties\":{\"summary\":{\"type\":\"string\"},"
          + "\"findings\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}}}}";

  private static final String TEXT = "{\"summary\":\"s\",\"findings\":[]}";
  private static final String THINKING_THEN_TEXT =
      "[{\"type\":\"thinking\",\"thinking\":\"\",\"signature\":\"\"},"
          + "{\"type\":\"text\",\"text\":\"{\\\"summary\\\":\\\"s\\\",\\\"findings\\\":[]}\"}]";
  private static final String USAGE =
      "{\"input_tokens\":10,\"output_tokens\":20,"
          + "\"cache_creation_input_tokens\":30,\"cache_read_input_tokens\":40}";
  private static final String ERROR_BODY =
      "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"busy\"}}";

  private static LlmRequest request() {
    return new LlmRequest(
        "claude-opus-5-5",
        16_000,
        "medium",
        "SYSTEM",
        "<prlens:team_context>ctx</prlens:team_context>",
        "<prlens:pr_title>title</prlens:pr_title>",
        SCHEMA);
  }

  private static AnthropicLlmClient client(FakeClaudeServer server) {
    return client(server, Duration.ofSeconds(5), null);
  }

  private static AnthropicLlmClient client(
      FakeClaudeServer server, Duration timeout, Consumer<String> debugLog) {
    return new AnthropicLlmClient(
        KEY,
        new AnthropicLlmClient.Options(
            server.baseUrl(), timeout, new SecretMasker(List.of(KEY)), debugLog));
  }

  private static FakeClaudeServer okServer() {
    return new FakeClaudeServer()
        .respond(200, FakeClaudeServer.message("end_turn", THINKING_THEN_TEXT, USAGE));
  }

  private static JsonNode sentBody(FakeClaudeServer server) {
    return MAPPER.readTree(server.requests().get(0).body());
  }

  // 요구사항 9.1, 20.8
  @Test
  void sendsModelEffortMaxTokensSystemAndSchema() {
    try (FakeClaudeServer server = okServer()) {
      client(server).send(request());

      JsonNode body = sentBody(server);
      assertThat(server.requests().get(0).method()).isEqualTo("POST");
      assertThat(server.requests().get(0).path()).isEqualTo("/v1/messages");
      assertThat(body.get("model").asString()).isEqualTo("claude-opus-5-5");
      assertThat(body.get("max_tokens").asInt()).isEqualTo(16_000);
      assertThat(body.get("system").asString()).isEqualTo("SYSTEM");
      assertThat(body.get("output_config").get("effort").asString()).isEqualTo("medium");
      assertThat(body.get("output_config").get("format").get("type").asString())
          .isEqualTo("json_schema");
      assertThat(body.get("output_config").get("format").get("schema"))
          .isEqualTo(MAPPER.readTree(SCHEMA));
    }
  }

  // 요구사항 9.1, 20.6
  @Test
  void sendsTwoUserBlocksWithCacheControlOnFirstOnly() {
    try (FakeClaudeServer server = okServer()) {
      client(server).send(request());

      JsonNode messages = sentBody(server).get("messages");
      assertThat(messages).hasSize(1);
      assertThat(messages.get(0).get("role").asString()).isEqualTo("user");
      JsonNode content = messages.get(0).get("content");
      assertThat(content).hasSize(2);
      assertThat(content.get(0).get("text").asString()).isEqualTo(request().cachedUserBlock());
      assertThat(content.get(0).get("cache_control").get("type").asString()).isEqualTo("ephemeral");
      assertThat(content.get(1).get("text").asString()).isEqualTo(request().dataUserBlock());
      assertThat(content.get(1).has("cache_control")).isFalse();
    }
  }

  // 요구사항 19.6
  @Test
  void apiKeyGoesOnlyInXApiKeyHeaderNeverInBody() {
    try (FakeClaudeServer server = okServer()) {
      client(server).send(request());

      assertThat(server.requests().get(0).header("x-api-key")).isEqualTo(KEY);
      assertThat(server.requests().get(0).body()).doesNotContain(KEY);
    }
  }

  // 요구사항 19.6: 디버그 로그에 키가 없다 (성공과 오류 응답 모두)
  @Test
  void debugLogNeverContainsApiKey() {
    List<String> log = new ArrayList<>();
    try (FakeClaudeServer server =
        okServer().respond(401, ERROR_BODY, "x-api-key", KEY, "request-id", "req_1")) {
      AnthropicLlmClient client = client(server, Duration.ofSeconds(5), log::add);
      client.send(request());
      assertThatThrownBy(() -> client.send(request())).isInstanceOf(LlmApiException.class);
    }

    assertThat(log).isNotEmpty();
    assertThat(String.join("\n", log)).doesNotContain(KEY);
  }

  // 요구사항 21.2: SDK 자체 재시도를 끄고 재시도는 RetryingLlmClient 한 곳에서만 한다
  @Test
  void sdkRetriesAreDisabledSoOneRequestPerSend() {
    try (FakeClaudeServer server =
        new FakeClaudeServer()
            .respond(503, ERROR_BODY)
            .respond(200, FakeClaudeServer.message("end_turn", THINKING_THEN_TEXT, USAGE))) {
      assertThatThrownBy(() -> client(server).send(request())).isInstanceOf(LlmApiException.class);

      assertThat(server.requests()).hasSize(1);
    }
  }

  // 요구사항 21.2: 제한 시간 초과는 상태 코드 없는 timeout이다
  @Test
  void requestTimeoutBecomesLlmApiExceptionWithTimeoutKind() {
    try (FakeClaudeServer server = new FakeClaudeServer().respondAfter(3_000, 200, "{}")) {
      AnthropicLlmClient client = client(server, Duration.ofMillis(500), null);

      assertThatThrownBy(() -> client.send(request()))
          .isInstanceOfSatisfying(
              LlmApiException.class,
              e -> {
                assertThat(e.status()).isNull();
                assertThat(e.errorKind()).isEqualTo("timeout");
              });
    }
  }

  // 요구사항 20.1: thinking 블록 뒤의 첫 text 블록을 고르고 usage 네 토큰을 옮긴다
  @Test
  void mapsStopReasonFirstTextBlockAfterThinkingAndFourTokenUsage() {
    try (FakeClaudeServer server = okServer()) {
      LlmResponse response = client(server).send(request());

      assertThat(response.stopReason()).isEqualTo("end_turn");
      assertThat(response.text()).isEqualTo(TEXT);
      assertThat(response.usage()).isEqualTo(new LlmUsage(10, 20, 30, 40));
    }
  }

  @Test
  void missingCacheUsageMapsToZero() {
    try (FakeClaudeServer server =
        new FakeClaudeServer()
            .respond(
                200,
                FakeClaudeServer.message(
                    "end_turn", THINKING_THEN_TEXT, "{\"input_tokens\":1,\"output_tokens\":2}"))) {
      LlmResponse response = client(server).send(request());

      assertThat(response.usage()).isEqualTo(new LlmUsage(1, 2, 0, 0));
    }
  }

  // refusal은 text 블록이 없을 수 있다. 파서가 stop_reason으로 처리한다
  @Test
  void missingTextBlockGivesNullText() {
    try (FakeClaudeServer server =
        new FakeClaudeServer().respond(200, FakeClaudeServer.message("refusal", "[]", USAGE))) {
      LlmResponse response = client(server).send(request());

      assertThat(response.stopReason()).isEqualTo("refusal");
      assertThat(response.text()).isNull();
    }
  }

  // 요구사항 21.6~21.8: HTTP 오류는 상태 코드와 retry-after 원문을 담는다
  @Test
  void httpErrorBecomesLlmApiExceptionWithStatusKindAndRawRetryAfter() {
    try (FakeClaudeServer server =
        new FakeClaudeServer()
            .respond(429, ERROR_BODY, "retry-after", "30")
            .respond(503, ERROR_BODY)) {
      AnthropicLlmClient client = client(server);

      assertThatThrownBy(() -> client.send(request()))
          .isInstanceOfSatisfying(
              LlmApiException.class,
              e -> {
                assertThat(e.status()).isEqualTo(429);
                assertThat(e.errorKind()).isEqualTo("429");
                assertThat(e.retryAfter()).isEqualTo("30");
              });
      assertThatThrownBy(() -> client.send(request()))
          .isInstanceOfSatisfying(
              LlmApiException.class,
              e -> {
                assertThat(e.status()).isEqualTo(503);
                assertThat(e.retryAfter()).isNull();
              });
    }
  }

  @Test
  void connectionFailureHasNullStatusAndConnectionKind() {
    AnthropicLlmClient client =
        new AnthropicLlmClient(
            KEY,
            new AnthropicLlmClient.Options(
                FakeClaudeServer.closedPortBaseUrl(),
                Duration.ofSeconds(5),
                new SecretMasker(List.of(KEY)),
                null));

    assertThatThrownBy(() -> client.send(request()))
        .isInstanceOfSatisfying(
            LlmApiException.class,
            e -> {
              assertThat(e.status()).isNull();
              assertThat(e.errorKind()).isEqualTo("connection_error");
            });
  }

  // 주의(18.5 리뷰): SDK의 런타임 예외를 그대로 던지면 CLI에 "내부 오류"로만 보인다
  @Test
  void undecodableBodyBecomesLlmApiExceptionNotRawSdkException() {
    try (FakeClaudeServer server = new FakeClaudeServer().respond(200, "not json at all")) {
      assertThatThrownBy(() -> client(server).send(request()))
          .isInstanceOfSatisfying(
              LlmApiException.class,
              e -> {
                assertThat(e.status()).isNull();
                assertThat(e.errorKind()).isEqualTo("invalid_response");
              });
    }
  }

  // 결정(7.1 spike): 스키마 키 순서가 요청마다 같아야 프롬프트 캐시가 읽힌다
  @Test
  void schemaSerializationIsIdenticalAcrossRequests() {
    try (FakeClaudeServer server =
        okServer().respond(200, FakeClaudeServer.message("end_turn", THINKING_THEN_TEXT, USAGE))) {
      AnthropicLlmClient client = client(server);
      client.send(request());
      client.send(request());

      assertThat(server.requests().get(0).body()).isEqualTo(server.requests().get(1).body());
      JsonNode schema = sentBody(server).get("output_config").get("format").get("schema");
      assertThat(List.copyOf(schema.propertyNames()))
          .isEqualTo(List.copyOf(MAPPER.readTree(SCHEMA).propertyNames()));
      assertThat(List.copyOf(schema.get("properties").propertyNames()))
          .containsExactly("summary", "findings");
    }
  }
}
