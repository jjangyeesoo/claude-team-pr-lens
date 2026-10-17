package com.prlens.codec;

import com.prlens.support.PrLensException;
import java.util.List;

/**
 * ReviewResult JSON을 읽을 수 없을 때 던진다.
 *
 * <p>값이 잘못된 경우 {@code path}에 필드 경로(예: {@code findings[2].severity})를 담고, 열거형이면 {@code allowed}에 허용
 * 값을 담는다. 문법 오류면 {@code path}가 null이고 {@code line}과 {@code column}에 위치를 담는다.
 */
public final class CodecException extends PrLensException {

  private final String path;
  private final List<String> allowed;
  private final Integer line;
  private final Integer column;

  CodecException(String message, String path, List<String> allowed, Integer line, Integer column) {
    super(message);
    this.path = path;
    this.allowed = List.copyOf(allowed);
    this.line = line;
    this.column = column;
  }

  public String path() {
    return path;
  }

  public List<String> allowed() {
    return allowed;
  }

  public Integer line() {
    return line;
  }

  public Integer column() {
    return column;
  }
}
