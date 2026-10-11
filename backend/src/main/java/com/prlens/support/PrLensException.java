package com.prlens.support;

/** PR Lens가 던지는 오류의 기반 타입. 하위 타입이 여러 패키지에 있어 sealed로 두지 않는다. 종료 코드로 바꾸는 일은 {@code cli}가 한 번만 한다. */
public abstract class PrLensException extends RuntimeException {

  protected PrLensException(String message) {
    super(message);
  }

  protected PrLensException(String message, Throwable cause) {
    super(message, cause);
  }
}
