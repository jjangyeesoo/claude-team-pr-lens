# 0002. 표준 에러 응답 포맷
- 상태: accepted · 날짜: 2026-09-27

## 배경
엔드포인트마다 에러 형식이 다르면 클라이언트 처리가 복잡해지고, AI가 새 엔드포인트를 만들 때 일관성이 깨진다.

## 결정
모든 에러는 `ErrorResponse { code, message, details }`로 응답하고, `ApiExceptionHandler`에서 한곳에 매핑한다. `code`는 UPPER_SNAKE_CASE.

## 고려한 대안
- Spring `ProblemDetail`(RFC 9457): 표준이지만 팀 클라이언트가 이미 `code` 필드에 의존한다. 추후 전환을 검토한다.

## 결과
- 새 예외 타입은 반드시 `ApiExceptionHandler`에 등록한다 (`.claude/rules/backend/api-design.md`).
