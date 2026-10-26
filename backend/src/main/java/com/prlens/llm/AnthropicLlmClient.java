package com.prlens.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Usage;
import com.prlens.support.SecretMasker;
import java.io.InterruptedIOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Anthropic Messages API 어댑터 (요구사항 9, 20.8, 21.2).
 *
 * <p>SDK 자체 재시도는 끄고 재시도는 {@link RetryingLlmClient} 한 곳에서만 한다. 키는 이 클래스만 안다(요구사항 19.6). SDK가 던지는 오류는
 * 모두 {@link LlmApiException}으로 감싸, CLI가 원인을 보여 줄 수 있게 한다.
 */
public final class AnthropicLlmClient implements LlmClient {

  /** 상태 코드가 없는 오류의 종류. {@link RetryingLlmClient}가 앞의 둘만 재시도한다. */
  static final String TIMEOUT = "timeout";

  static final String CONNECTION_ERROR = "connection_error";
  static final String INVALID_RESPONSE = "invalid_response";

  /** 오류 응답 사유(메시지 부분)의 최대 글자 수 (설계 G-21). */
  static final int DETAIL_MAX_CHARS = 200;

  private static final JsonMapper MAPPER = JsonMapper.builder().build();

  /**
   * @param baseUrl API 기준 주소. null이면 SDK 기본값이다. 테스트의 가짜 서버가 바꾼다
   * @param debugLog null이면 디버그 로그를 쓰지 않는다
   */
  public record Options(
      String baseUrl, Duration timeout, SecretMasker masker, Consumer<String> debugLog) {
    public static Options defaults() {
      return new Options(null, Duration.ofSeconds(180), new SecretMasker(List.of()), null);
    }
  }

  private final AnthropicClient client;
  private final Options options;

  public AnthropicLlmClient(String apiKey) {
    this(apiKey, Options.defaults());
  }

  public AnthropicLlmClient(String apiKey, Options options) {
    AnthropicOkHttpClient.Builder builder =
        AnthropicOkHttpClient.builder().apiKey(apiKey).maxRetries(0).timeout(options.timeout());
    if (options.baseUrl() != null) {
      builder.baseUrl(options.baseUrl());
    }
    this.client = builder.build();
    this.options = options;
  }

  @Override
  public LlmResponse send(LlmRequest request) {
    MessageCreateParams params = toParams(request);
    debug(
        "> POST messages model="
            + request.model()
            + " effort="
            + request.effort()
            + " max_tokens="
            + request.maxOutputTokens());
    try {
      return toResponse(client.messages().create(params));
    } catch (AnthropicServiceException e) {
      throw serviceError(e);
    } catch (AnthropicIoException e) {
      String kind = hasTimeoutCause(e) ? TIMEOUT : CONNECTION_ERROR;
      debug("< " + kind);
      throw new LlmApiException("Claude API에 연결하지 못했습니다 (" + kind + ")", null, kind, null, e);
    } catch (AnthropicException e) {
      debug("< " + INVALID_RESPONSE);
      throw new LlmApiException("Claude API 응답을 해석하지 못했습니다", null, INVALID_RESPONSE, null, e);
    }
  }

  private static MessageCreateParams toParams(LlmRequest request) {
    JsonOutputFormat format =
        JsonOutputFormat.builder().schema(schema(request.jsonSchema())).build();
    OutputConfig outputConfig =
        OutputConfig.builder()
            .effort(OutputConfig.Effort.of(request.effort()))
            .format(format)
            .build();
    // 첫 블록(Common_Context) 끝에만 캐시 지점을 둔다 (요구사항 20.6)
    ContentBlockParam cached =
        ContentBlockParam.ofText(
            TextBlockParam.builder()
                .text(request.cachedUserBlock())
                .cacheControl(CacheControlEphemeral.builder().build())
                .build());
    return MessageCreateParams.builder()
        .model(request.model())
        .maxTokens(request.maxOutputTokens())
        .system(request.system())
        .outputConfig(outputConfig)
        .addUserMessageOfBlockParams(
            List.of(cached, ContentBlockParam.ofText(request.dataUserBlock())))
        .build();
  }

  /** 스키마 키 순서가 요청마다 같아야 프롬프트 캐시가 읽히므로, JSON 원문의 순서대로 옮긴다. */
  private static JsonOutputFormat.Schema schema(String json) {
    JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
    Map<?, ?> tree = MAPPER.readValue(json, Map.class);
    tree.forEach(
        (key, value) -> schema.putAdditionalProperty(String.valueOf(key), JsonValue.from(value)));
    return schema.build();
  }

  private static LlmResponse toResponse(Message message) {
    String stopReason = message.stopReason().map(StopReason::asString).orElse(null);
    // 첫 블록이 아니라 첫 text 블록을 고른다 (medium effort 이상은 thinking 블록이 앞에 온다)
    String text =
        message.content().stream()
            .filter(ContentBlock::isText)
            .findFirst()
            .map(block -> block.asText().text())
            .orElse(null);
    Usage usage = message.usage();
    return new LlmResponse(
        stopReason,
        text,
        new LlmUsage(
            usage.inputTokens(),
            usage.outputTokens(),
            usage.cacheCreationInputTokens().orElse(0L),
            usage.cacheReadInputTokens().orElse(0L)));
  }

  private LlmApiException serviceError(AnthropicServiceException e) {
    int status = e.statusCode();
    List<String> retryAfter = e.headers().values("retry-after");
    if (options.debugLog() != null) {
      debug("< " + status);
      for (String name : e.headers().names()) {
        for (String value : e.headers().values(name)) {
          debug("< " + name + ": " + options.masker().maskHeader(name, value));
        }
      }
    }
    return new LlmApiException(
        "Claude API 요청이 실패했습니다 (상태 코드 " + status + ")",
        status,
        String.valueOf(status),
        retryAfter.isEmpty() ? null : retryAfter.get(0),
        detail(e),
        e);
  }

  /**
   * 오류 응답 본문의 {@code error.type}과 {@code error.message}를 {@code 종류: 메시지}로 만든다 (설계 G-21). 메시지는 공백을
   * 정리하고 비밀값을 가린 뒤 {@value #DETAIL_MAX_CHARS}자까지만 쓴다. 본문이 오류 객체가 아니면 null이다.
   */
  private String detail(AnthropicServiceException e) {
    Map<String, JsonValue> error = objectOf(objectOf(e.body()).get("error"));
    if (error.isEmpty()) {
      return null;
    }
    String type = collapse(textOf(error.get("type")));
    String message = collapse(textOf(error.get("message")));
    // 마스킹을 먼저 한다. 자른 뒤에 하면 경계에 걸린 비밀값의 앞부분이 그대로 남는다
    if (message != null) {
      message = options.masker().mask(message);
      if (message.codePointCount(0, message.length()) > DETAIL_MAX_CHARS) {
        message = message.substring(0, message.offsetByCodePoints(0, DETAIL_MAX_CHARS));
      }
    }
    if (type == null) {
      return message;
    }
    type = options.masker().mask(type);
    return message == null ? type : type + ": " + message;
  }

  /** 공백(줄바꿈 포함)을 한 칸으로 줄이고 앞뒤를 자른다. 비었거나 null이면 null이다. */
  private static String collapse(String text) {
    if (text == null) {
      return null;
    }
    String collapsed = text.replaceAll("\\s+", " ").strip();
    return collapsed.isEmpty() ? null : collapsed;
  }

  // SDK의 JsonValue는 raw 타입 JsonField를 상속해 asObject()와 asString()이 raw Optional로 보이므로 여기서 한 번만 형을
  // 맞춘다
  @SuppressWarnings("unchecked")
  private static Map<String, JsonValue> objectOf(JsonValue value) {
    if (value == null) {
      return Map.of();
    }
    return ((Optional<Map<String, JsonValue>>) (Optional<?>) value.asObject()).orElse(Map.of());
  }

  private static String textOf(JsonValue value) {
    return value == null ? null : (String) value.asString().orElse(null);
  }

  private static boolean hasTimeoutCause(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof InterruptedIOException) {
        return true;
      }
    }
    return false;
  }

  private void debug(String line) {
    if (options.debugLog() != null) {
      options.debugLog().accept(line);
    }
  }
}
