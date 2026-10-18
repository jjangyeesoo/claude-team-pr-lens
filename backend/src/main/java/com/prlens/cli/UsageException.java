package com.prlens.cli;

import com.prlens.support.PrLensException;

/** 명령 인자가 잘못됐을 때 던진다 (URL 형식, {@code --format} 값 등). 종료 코드 2로 바뀐다. */
public final class UsageException extends PrLensException {

  public UsageException(String message) {
    super(message);
  }
}
