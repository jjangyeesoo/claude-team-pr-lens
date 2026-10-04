# backend (Spring Boot REST API)
Spring Boot 4.1 / Java 17 / Gradle (Kotlin DSL). 이 파일은 Claude가 `backend/` 아래 파일을 다룰 때만 로드된다.

## Commands (`backend/`에서 실행)
- 전체 테스트: `./gradlew test`  (Windows PowerShell: `.\gradlew.bat test`)
- 단일 테스트: `./gradlew test --tests "com.example.starter.memo.api.MemoControllerTest"` (전체보다 단일 테스트를 우선 실행)
- 포맷: `./gradlew spotlessApply` (google-java-format). 직접 들여쓰기를 맞추지 말고 이 명령에 맡긴다
- 실행: `./gradlew bootRun` (포트 8080)

## Architecture
- 기능(도메인) 단위 패키지: `com.example.starter.<feature>.{domain,api}`
  - `domain`: 비즈니스 로직, 엔티티. `api`를 import하지 않는다
  - `api`: Controller, 요청/응답 DTO(record). 엔티티를 그대로 응답하지 않고 `*Response.from()`으로 변환한다
- 공통 에러 처리: `common/error/ApiExceptionHandler`. 에러 응답은 항상 `ErrorResponse { code, message, details }`
- 시간: `Clock` 빈(`common/config/TimeConfig`)을 생성자로 주입받는다. `Instant.now()`를 직접 호출하지 않는다

## Gotchas
- Spring Boot 4: 테스트 슬라이스 패키지가 바뀌었다. `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있다
- 응답 DTO(`*Response`)를 바꾸면 `web/src/lib/api/types.ts`도 같은 PR에서 바꾼다
