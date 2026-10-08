---
name: new-endpoint
description: 팀 컨벤션에 맞춰 새 REST 엔드포인트(Controller, DTO, 테스트)를 만든다. 새 API를 추가할 때 사용 (P2부터. P1은 HTTP API가 없는 CLI다).
argument-hint: "[METHOD /path 설명]"
---
다음 엔드포인트를 추가한다: $ARGUMENTS

작업 위치: `backend/`. 본보기로 삼을 기존 컨트롤러는 없다(메모 샘플은 P1 작업 1.1에서 지웠다). 아래 규칙과 스펙의 설계 절이 기준이다.

1. 스펙을 확인한다. 엔드포인트가 그 단계 스펙(`docs/specs/<스펙>/`)의 요구사항과 `design.md`에 있어야 한다. 없으면 멈추고 스펙에 먼저 더할지 묻는다. P1 단계라면 HTTP API가 범위 밖이라는 것을 알리고 멈춘다.
2. 패키지를 정한다 (ADR 0003, 역할 단위 평면 패키지).
   - Controller와 요청·응답 DTO는 `design.md` "패키지 배치" 표가 정한 역할 패키지에 함께 둔다 (P2: 조회 API는 `com.prlens.query`, webhook 수신은 `com.prlens.webhook`). `api`, `domain`, `controller`, `dto` 같은 하위 패키지를 만들지 않는다.
   - 표에 없는 패키지가 필요하면 같은 PR에서 표와 의존 규칙을 고친다. 새 코드를 `common` 아래에 두지 않는다.
   - Controller는 요청 검증과 응답 변환만 한다. 로직은 그 역할 패키지의 클래스나 표가 정한 패키지(`store` 등)에 두고, 의존 방향은 설계의 "의존 규칙"(ArchUnit)을 따른다.
3. `.claude/rules/backend/api-design.md`를 직접 읽고 따른다 (`paths`가 `**/api/**`라 평면 패키지에서는 자동으로 로드되지 않는다). 경로는 `/api/v1/`, 에러는 `ErrorResponse { code, message, details }`다 (ADR 0002, 0008).
4. 실패하는 `@WebMvcTest(XxxController.class)` 테스트를 먼저 작성한다: 정상 응답, 검증 실패(400 + `ErrorResponse`), 없는 리소스(404). 필요한 빈만 `@Import`한다. Spring Boot 4의 `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있다.
5. 로직이 있으면 Spring 없는 단위 테스트도 작성한다 (`.claude/rules/backend/testing.md`).
6. 구현한 뒤 `backend/`에서 `./gradlew test --tests "<새 테스트 클래스>"`로 통과를 확인하고, 마지막에 전체 테스트를 실행한다.
7. web이 이 API를 쓰면(P3부터) 응답 타입을 같은 PR에서 `web/src/lib/api/types.ts`에 추가한다.
