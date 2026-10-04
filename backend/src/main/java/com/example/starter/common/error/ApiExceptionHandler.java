package com.example.starter.common.error;

import com.example.starter.memo.domain.MemoNotFoundException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 모든 API 에러를 ErrorResponse { code, message, details }로 응답한다 (ADR-0002). */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
    List<String> details =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return error(HttpStatus.BAD_REQUEST, "INVALID_INPUT", "요청 값이 올바르지 않습니다.", details);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
    return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "요청 본문을 읽을 수 없습니다.", List.of());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return error(
        HttpStatus.BAD_REQUEST,
        "INVALID_PARAMETER",
        "요청 파라미터 형식이 올바르지 않습니다.",
        List.of(e.getName() + ": " + e.getValue()));
  }

  @ExceptionHandler(MemoNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleNotFound(MemoNotFoundException e) {
    return error(HttpStatus.NOT_FOUND, "MEMO_NOT_FOUND", e.getMessage(), List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException e) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다.", List.of());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotAllowed(
      HttpRequestMethodNotSupportedException e) {
    return error(
        HttpStatus.METHOD_NOT_ALLOWED,
        "METHOD_NOT_ALLOWED",
        "지원하지 않는 HTTP 메서드입니다: " + e.getMethod(),
        List.of());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception e) {
    log.error("Unhandled exception", e);
    return error(
        HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.", List.of());
  }

  private static ResponseEntity<ErrorResponse> error(
      HttpStatus status, String code, String message, List<String> details) {
    return ResponseEntity.status(status).body(new ErrorResponse(code, message, details));
  }
}
