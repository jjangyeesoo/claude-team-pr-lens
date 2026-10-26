package com.prlens.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.support.SecretMasker;
import com.prlens.testkit.FakeClaudeServer;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

// 작업 13.3: 요청·응답 매핑. 가짜 서버가 받은 JSON과 돌려준 JSON으로 확인한다
class AnthropicRequestMappingTest {

  private static final JsonMapper MAPPER = JsonMapper.builder().build();
  private static final String CONTENT = "[{\"type\":\"text\",\"text\":\"{}\"}]";

  private static LlmRequest request(String model, String effort) {
    return new LlmRequest(
        model,
        1_000,
        effort,
        "SYSTEM",
        "<prlens:team_context>ctx</prlens:team_context>",
        "<prlens:pr_title>title</prlens:pr_title>",
        "{\"type\":\"object\"}");
  }

  private static LlmResponse send(FakeClaudeServer server, LlmRequest request) {
    return new AnthropicLlmClient(
            "sk-ant-test-key",
            new AnthropicLlmClient.Options(
                server.baseUrl(), Duration.ofSeconds(5), new SecretMasker(List.of()), null))
        .send(request);
  }

  private static FakeClaudeServer server(String usageJson) {
    return new FakeClaudeServer()
        .respond(200, FakeClaudeServer.message("end_turn", CONTENT, usageJson));
  }

  // 요구사항 20.8: 모델과 effort는 설정값 그대로 항상 요청에 있다
  @Test
  void modelAndEffortAreAlwaysExplicit() {
    String[][] cases = {
      {"claude-opus-5-5", "medium"}, {"claude-sonnet-5-5", "low"}, {"another-model", "max"}
    };
    for (String[] c : cases) {
      try (FakeClaudeServer server = server("{\"input_tokens\":1,\"output_tokens\":1}")) {
        send(server, request(c[0], c[1]));

        JsonNode body = MAPPER.readTree(server.requests().get(0).body());
        assertThat(body.get("model").asString()).isEqualTo(c[0]);
        assertThat(body.get("output_config").get("effort").asString()).isEqualTo(c[1]);
      }
    }
  }

  // 요구사항 20.6: 캐시 지점은 요청에 하나이고 Common_Context 블록 끝에 있다
  @Test
  void cachePointSitsAfterCommonContextBlock() {
    try (FakeClaudeServer server = server("{\"input_tokens\":1,\"output_tokens\":1}")) {
      send(server, request("claude-opus-5-5", "medium"));

      String raw = server.requests().get(0).body();
      assertThat(raw.split("cache_control", -1)).hasSize(2);
      JsonNode content = MAPPER.readTree(raw).get("messages").get(0).get("content");
      assertThat(content.get(0).get("text").asString()).endsWith("</prlens:team_context>");
      assertThat(content.get(0).has("cache_control")).isTrue();
      assertThat(content.get(1).get("text").asString()).startsWith("<prlens:pr_title>");
      assertThat(MAPPER.readTree(raw).get("system").asString()).isEqualTo("SYSTEM");
    }
  }

  // 요구사항 20.1, 20.9: 네 토큰이 자리를 바꾸지 않고 그대로 옮겨진다
  @Test
  void usageFieldsMapToFourTokens() {
    String usage =
        "{\"input_tokens\":1000000,\"output_tokens\":16000,"
            + "\"cache_creation_input_tokens\":5000,\"cache_read_input_tokens\":123456}";
    try (FakeClaudeServer server = server(usage)) {
      LlmResponse response = send(server, request("claude-opus-5-5", "medium"));

      assertThat(response.usage()).isEqualTo(new LlmUsage(1_000_000, 16_000, 5_000, 123_456));
    }
  }
}
