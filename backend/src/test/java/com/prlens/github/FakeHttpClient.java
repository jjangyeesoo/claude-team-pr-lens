package com.prlens.github;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;

/** 응답이나 예외를 차례로 돌려주고 받은 요청을 기록하는 테스트용 {@link HttpClient}. */
final class FakeHttpClient extends HttpClient {

  private final ArrayDeque<Object> queue = new ArrayDeque<>();
  private final List<HttpRequest> requests = new ArrayList<>();

  /** {@code headers}는 이름, 값 순서의 쌍이다. */
  FakeHttpClient respond(int status, String body, String... headers) {
    Map<String, List<String>> map = new HashMap<>();
    for (int i = 0; i < headers.length; i += 2) {
      map.computeIfAbsent(headers[i], k -> new ArrayList<>()).add(headers[i + 1]);
    }
    queue.add(new FakeResponse(status, body, HttpHeaders.of(map, (a, b) -> true)));
    return this;
  }

  FakeHttpClient fail(IOException error) {
    queue.add(error);
    return this;
  }

  List<HttpRequest> requests() {
    return List.copyOf(requests);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException {
    requests.add(request);
    Object next = queue.poll();
    if (next == null) {
      throw new IllegalStateException("준비한 응답이 없습니다: " + request.uri());
    }
    if (next instanceof IOException error) {
      throw error;
    }
    return (HttpResponse<T>) next;
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request, HttpResponse.BodyHandler<T> handler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> CompletableFuture<HttpResponse<T>> sendAsync(
      HttpRequest request,
      HttpResponse.BodyHandler<T> handler,
      HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Optional<CookieHandler> cookieHandler() {
    return Optional.empty();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return Optional.empty();
  }

  @Override
  public Redirect followRedirects() {
    return Redirect.NEVER;
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return Optional.empty();
  }

  @Override
  public SSLContext sslContext() {
    return null;
  }

  @Override
  public SSLParameters sslParameters() {
    return null;
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return Optional.empty();
  }

  @Override
  public Version version() {
    return Version.HTTP_1_1;
  }

  @Override
  public Optional<Executor> executor() {
    return Optional.empty();
  }

  private record FakeResponse(int status, String body, HttpHeaders headers)
      implements HttpResponse<String> {
    @Override
    public int statusCode() {
      return status;
    }

    @Override
    public HttpRequest request() {
      return null;
    }

    @Override
    public Optional<HttpResponse<String>> previousResponse() {
      return Optional.empty();
    }

    @Override
    public Optional<SSLSession> sslSession() {
      return Optional.empty();
    }

    @Override
    public URI uri() {
      return null;
    }

    @Override
    public HttpClient.Version version() {
      return HttpClient.Version.HTTP_1_1;
    }
  }
}
