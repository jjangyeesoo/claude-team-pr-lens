package com.prlens.github;

import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * 받은 요청의 제한 시간을 기록한 뒤 짧은 값으로 바꿔 실제 {@link HttpClient}에 넘기는 테스트용 클라이언트. 테스트가 30초를 기다리지 않고 제한 시간 초과를
 * 확인하게 한다.
 */
final class ShortTimeoutHttpClient extends HttpClient {

  private final HttpClient delegate;
  private final Duration shortTimeout;
  private final List<Optional<Duration>> originalTimeouts = new ArrayList<>();

  ShortTimeoutHttpClient(HttpClient delegate, Duration shortTimeout) {
    this.delegate = delegate;
    this.shortTimeout = shortTimeout;
  }

  /** 클라이언트가 요청마다 건 원래 제한 시간. */
  List<Optional<Duration>> originalTimeouts() {
    return List.copyOf(originalTimeouts);
  }

  @Override
  public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
      throws IOException, InterruptedException {
    originalTimeouts.add(request.timeout());
    HttpRequest shortened =
        HttpRequest.newBuilder(request, (name, value) -> true).timeout(shortTimeout).build();
    return delegate.send(shortened, handler);
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
    return delegate.cookieHandler();
  }

  @Override
  public Optional<Duration> connectTimeout() {
    return delegate.connectTimeout();
  }

  @Override
  public Redirect followRedirects() {
    return delegate.followRedirects();
  }

  @Override
  public Optional<ProxySelector> proxy() {
    return delegate.proxy();
  }

  @Override
  public SSLContext sslContext() {
    return delegate.sslContext();
  }

  @Override
  public SSLParameters sslParameters() {
    return delegate.sslParameters();
  }

  @Override
  public Optional<Authenticator> authenticator() {
    return delegate.authenticator();
  }

  @Override
  public Version version() {
    return delegate.version();
  }

  @Override
  public Optional<Executor> executor() {
    return delegate.executor();
  }
}
