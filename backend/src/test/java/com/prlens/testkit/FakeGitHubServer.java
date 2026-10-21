package com.prlens.testkit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 실제 HTTP 왕복을 확인하기 위한 가짜 GitHub 서버. JDK {@link HttpServer}를 {@code 127.0.0.1}의 임의 포트에 띄운다.
 *
 * <p>응답은 경로와 쿼리(예: {@code /repos/o/r/pulls/5/files?per_page=100&page=1})마다 등록한 순서대로 한 번씩 소비한다. 마지막
 * 것도 반복하지 않는다. 등록하지 않았거나 다 소비한 경로는 404 {@code {"message":"Not Found"}}다.
 */
public final class FakeGitHubServer implements AutoCloseable {

  /** 서버가 받은 요청 한 건. 헤더 이름은 소문자다. */
  public record Recorded(String method, String pathAndQuery, Map<String, List<String>> headers) {
    public String header(String name) {
      List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null || values.isEmpty() ? null : values.get(0);
    }
  }

  private record Canned(int status, String body, Map<String, String> headers, boolean slow) {}

  private static final long SLOW_WAIT_SECONDS = 5;

  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final CountDownLatch release = new CountDownLatch(1);
  private final Map<String, ArrayDeque<Canned>> responses = new HashMap<>();
  private final List<Recorded> requests = new ArrayList<>();

  public FakeGitHubServer() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException e) {
      throw new IllegalStateException("가짜 서버를 시작하지 못했습니다", e);
    }
    server.setExecutor(executor);
    server.createContext("/", this::handle);
    server.start();
  }

  /** 뒤에 {@code /}가 붙은 기준 주소. 클라이언트가 뒤 {@code /}를 자르는 동작도 함께 확인된다. */
  public URI baseUri() {
    return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
  }

  /** {@code headers}는 이름, 값 순서의 쌍이다. */
  public FakeGitHubServer respond(String pathAndQuery, int status, String body, String... headers) {
    Map<String, String> map = new HashMap<>();
    for (int i = 0; i < headers.length; i += 2) {
      map.put(headers[i], headers[i + 1]);
    }
    enqueue(pathAndQuery, new Canned(status, body, map, false));
    return this;
  }

  /** 서버가 닫힐 때까지(최대 몇 초) 응답하지 않는 응답을 등록한다. */
  public FakeGitHubServer respondSlowly(String pathAndQuery) {
    enqueue(pathAndQuery, new Canned(200, "{}", Map.of(), true));
    return this;
  }

  public List<Recorded> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  private void enqueue(String pathAndQuery, Canned canned) {
    synchronized (responses) {
      responses.computeIfAbsent(pathAndQuery, k -> new ArrayDeque<>()).add(canned);
    }
  }

  private void handle(HttpExchange exchange) throws IOException {
    try (exchange) {
      URI uri = exchange.getRequestURI();
      String key = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
      Map<String, List<String>> headers = new HashMap<>();
      exchange
          .getRequestHeaders()
          .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), values));
      synchronized (requests) {
        requests.add(new Recorded(exchange.getRequestMethod(), key, headers));
      }
      Canned canned;
      synchronized (responses) {
        ArrayDeque<Canned> queue = responses.get(key);
        canned = queue == null ? null : queue.poll();
      }
      if (canned == null) {
        canned = new Canned(404, "{\"message\":\"Not Found\"}", Map.of(), false);
      }
      if (canned.slow()) {
        awaitRelease();
      }
      byte[] bytes = canned.body().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
      canned.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
      exchange.sendResponseHeaders(canned.status(), bytes.length == 0 ? -1 : bytes.length);
      if (bytes.length > 0) {
        try (OutputStream out = exchange.getResponseBody()) {
          out.write(bytes);
        }
      }
    } catch (IOException e) {
      // 클라이언트가 제한 시간으로 연결을 끊은 경우
    }
  }

  private void awaitRelease() {
    try {
      release.await(SLOW_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void close() {
    release.countDown();
    server.stop(0);
    executor.shutdownNow();
  }
}
