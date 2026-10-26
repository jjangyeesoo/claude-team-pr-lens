package com.prlens.testkit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 실제 HTTP 왕복을 확인하기 위한 가짜 Claude API 서버. JDK {@link HttpServer}를 {@code 127.0.0.1}의 임의 포트에 띄운다.
 *
 * <p>응답은 등록한 순서대로 한 번씩 소비하고, 다 소비한 뒤의 요청은 500이다. 요청 본문까지 기록하므로 어떤 JSON을 보냈는지 확인할 수 있다.
 */
public final class FakeClaudeServer implements AutoCloseable {

  /** 서버가 받은 요청 한 건. 헤더 이름은 소문자다. */
  public record Recorded(
      String method, String path, Map<String, List<String>> headers, String body) {
    public String header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null || values.isEmpty() ? null : values.get(0);
    }
  }

  private record Canned(int status, String body, Map<String, String> headers, long delayMillis) {}

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final ArrayDeque<Canned> responses = new ArrayDeque<>();
  private final List<Recorded> requests = new ArrayList<>();

  public FakeClaudeServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException("가짜 서버를 시작하지 못했습니다", e);
    }
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
  }

  /** 끝에 {@code /}가 없는 기준 주소. */
  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  /** {@code headers}는 이름, 값 순서의 쌍이다. */
  public FakeClaudeServer respond(int status, String body, String... headers) {
    return enqueue(new Canned(status, body, pairs(headers), 0));
  }

  /** {@code delayMillis} 뒤에 응답한다. 클라이언트의 제한 시간 초과를 만들 때 쓴다. */
  public FakeClaudeServer respondAfter(long delayMillis, int status, String body) {
    return enqueue(new Canned(status, body, Map.of(), delayMillis));
  }

  public List<Recorded> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  /** 닫힌 포트의 기준 주소. 연결 실패를 만들 때 쓴다. */
  public static String closedPortBaseUrl() {
    try (FakeClaudeServer closed = new FakeClaudeServer()) {
      return closed.baseUrl();
    }
  }

  /**
   * Messages API 응답 본문. {@code contentJson}은 content 블록 배열, {@code usageJson}은 usage 객체의 JSON이다.
   */
  public static String message(String stopReason, String contentJson, String usageJson) {
    return "{\"id\":\"msg_test\",\"type\":\"message\",\"role\":\"assistant\","
        + "\"model\":\"claude-opus-5-5\",\"content\":"
        + contentJson
        + ",\"stop_reason\":"
        + (stopReason == null ? "null" : "\"" + stopReason + "\"")
        + ",\"stop_sequence\":null,\"usage\":"
        + usageJson
        + "}";
  }

  @Override
  public void close() {
    server.stop(0);
    executor.shutdownNow();
  }

  private FakeClaudeServer enqueue(Canned canned) {
    synchronized (responses) {
      responses.add(canned);
    }
    return this;
  }

  private static Map<String, String> pairs(String[] headers) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < headers.length; i += 2) {
      map.put(headers[i], headers[i + 1]);
    }
    return map;
  }

  private void handle(HttpExchange exchange) throws IOException {
    Map<String, List<String>> headers = new HashMap<>();
    exchange
        .getRequestHeaders()
        .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    synchronized (requests) {
      requests.add(
          new Recorded(
              exchange.getRequestMethod(), exchange.getRequestURI().getPath(), headers, body));
    }
    Canned canned;
    synchronized (responses) {
      canned = responses.poll();
    }
    if (canned == null) {
      canned =
          new Canned(
              500,
              "{\"type\":\"error\",\"error\":{\"type\":\"api_error\",\"message\":\"준비한 응답이 없습니다\"}}",
              Map.of(),
              0);
    }
    try {
      if (canned.delayMillis() > 0) {
        Thread.sleep(canned.delayMillis());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }
    byte[] bytes = canned.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("content-type", "application/json");
    canned.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
    exchange.sendResponseHeaders(canned.status(), bytes.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(bytes);
    }
  }
}
