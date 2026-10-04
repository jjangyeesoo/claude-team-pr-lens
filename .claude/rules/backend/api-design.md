---
paths:
  - "backend/src/main/java/**/api/**/*.java"
---
# REST API 규칙
- URL: `/api/v1/<복수형-kebab-case>` (예: `/api/v1/memos`)
- JSON 필드: camelCase
- 요청 DTO는 record + Bean Validation(`@NotBlank`, `@Size` 등)으로 검증하고, 컨트롤러 파라미터에 `@Valid`를 붙인다
- 생성은 `201 Created` + `Location` 헤더, 조회 실패는 `404` + `ErrorResponse`
- 목록 API가 커질 수 있으면 페이지네이션을 넣는다 (`page`, `size`)
- 새 예외 타입은 `ApiExceptionHandler`에 매핑하고, 에러 `code`는 UPPER_SNAKE_CASE로 짓는다
