package com.prlens.cli;

import com.prlens.support.PrLensException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import picocli.CommandLine.ExecutionException;

/**
 * 실행 중 던져진 오류를 stderr 메시지와 종료 코드 2로 바꾼다 (설계의 "예외 계층과 종료 코드"). {@link PrLensException}은 하위 타입이 메시지를
 * 이미 담고 있으므로 {@code getMessage()}를 그대로 쓴다. 그 밖의 예외와 {@link Error}는 "내부 오류"로 알리고, 스택은 디버그 모드에서만 쓴다.
 *
 * <p>stdout에는 아무것도 쓰지 않는다. 결과는 문자열을 모두 만든 뒤에만 쓰므로 오류 경로에서 stdout은 비어 있다.
 */
final class ErrorMapper {

  static final int FAILURE = 2;

  private ErrorMapper() {}

  /**
   * 메시지를 {@code \n}으로 끝내고 두 스트림을 flush한다. 끝나지 않은 줄을 {@code MaskingPrintStream}이 들고 있으면 마지막 메시지가 사라질
   * 수 있다. 스트림을 닫는 것은 {@code PrLensMain}이 {@code System.exit} 직전에만 한다.
   */
  static int toExitCode(Throwable error, PrintStream out, PrintStream err, boolean debug) {
    Throwable cause = unwrap(error);
    if (cause instanceof PrLensException) {
      line(err, cause.getMessage() == null ? name(cause) : cause.getMessage());
    } else {
      line(err, "내부 오류가 발생했습니다 (" + name(cause) + ")");
      if (debug) {
        line(err, stackTrace(cause));
      }
    }
    out.flush();
    err.flush();
    return FAILURE;
  }

  /** picocli가 실행 중 예외를 감싼 {@link ExecutionException}은 원인을 꺼낸다. */
  private static Throwable unwrap(Throwable error) {
    Throwable current = error;
    while (current instanceof ExecutionException && current.getCause() != null) {
      current = current.getCause();
    }
    return current;
  }

  private static String name(Throwable error) {
    String simple = error.getClass().getSimpleName();
    return simple.isEmpty() ? error.getClass().getName() : simple;
  }

  /** {@code printStackTrace}는 OS의 줄바꿈을 쓰므로 문자열로 받아 {@code \n}으로 맞춘다. */
  private static String stackTrace(Throwable error) {
    StringWriter text = new StringWriter();
    error.printStackTrace(new PrintWriter(text));
    return text.toString().replaceAll("\\R", "\n").stripTrailing();
  }

  private static void line(PrintStream err, String message) {
    err.print(message.endsWith("\n") ? message : message + "\n");
  }
}
