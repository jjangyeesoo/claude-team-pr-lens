# 0008. P2·P3의 HTTP API는 기존 규약을 따른다 (`/api/v1/`, `ErrorResponse`)
- 상태: accepted · 날짜: 2026-10-04

## 배경
P2·P3 스펙은 저장소를 보지 못한 채 쓰여서 두 곳이 저장소 규칙과 다르다(스펙 검토 `docs/spec-review/` 01, 03, 04, 06에서 공통으로 지적).

- 에러 형식: 스펙은 `ProblemDetail`을 가정하거나 정하지 않았다. 저장소는 ADR 0002로 `ErrorResponse { code, message, details }`를 쓰고, web의 API 클라이언트(`web/src/lib/api/http.ts`)가 이 형식을 파싱한다. P2 요구사항도 원래 ADR 0002를 따르라고 적고 있다.
- API 경로: 스펙은 `/api/repositories`, `/api/stats`처럼 버전이 없다. 저장소 규칙(`.claude/rules/backend/api-design.md`)은 `/api/v1/<복수형-kebab-case>`다.

번호 0004~0007은 PRD 10장이 예약한 결정(CLI 라이브러리, LLM 설정, DB·테스트, 저장소 구조)에 쓴다.

## 결정
- 에러 응답은 ADR 0002의 `ErrorResponse`를 유지한다. P2·P3 스펙의 `ProblemDetail` 서술을 `ErrorResponse`로 고친다.
- 조회 REST API의 경로에 `/api/v1/`을 적용한다. P2·P3 스펙의 경로를 고친다.

## 고려한 대안
- **`ProblemDetail`(RFC 9457)로 변경**: 표준 형식이지만 ADR 0002에서 이미 기각했고, 바꾸려면 ADR 0002 대체와 web 클라이언트 수정이 필요하다.
- **버전 없는 경로를 규칙의 예외로 두기**: 스펙을 덜 고치지만, 도그푸딩 때 PR Lens가 자기 저장소의 규칙 위반을 지적하게 된다.

## 결과
- P2 스펙(요구사항, 설계, 작업 목록)과 P3 요구사항의 해당 부분을 고쳐야 한다. OpenAPI 문서를 쓰기 전에 반영한다.
- 아직 정하지 않은 것 (P2 요구사항의 결정 대기 D-16에 제안과 함께 올렸다)
  - webhook 수신 경로(`/webhooks/github`)는 GitHub가 호출하는 엔드포인트다. `/api/v1/` 규칙의 대상인지.
  - 저장소 규칙의 나머지(`page`·`size` 페이지네이션, `@Valid`, `ApiExceptionHandler` 매핑)와 스펙(`limit = 100` 고정, 경로 변수 수동 검증, `query` 전용 오류 처리)의 차이.
  - `api-design.md`의 `paths`가 `**/api/**`라 평면 패키지(`webhook`, `query`, `server`)에 로드되지 않는다. P2 전에 `paths`를 고친다.
