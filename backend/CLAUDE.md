# backend (PR Lens: CLI와 서버)
Spring Boot 4.1 / Java 17 / Gradle (Kotlin DSL). 이 파일은 Claude가 `backend/` 아래 파일을 다룰 때만 로드된다.
P1은 `prlens review <PR URL>` CLI이고 Spring 컨텍스트 없이 실행한다. P2부터 webhook과 조회 API 서버가 추가된다.

## Commands (`backend/`에서 실행)
- 전체 테스트: `./gradlew test`  (Windows PowerShell: `.\gradlew.bat test`)
- 단일 테스트: `./gradlew test --tests "com.prlens.diff.DiffParserTest"` (전체보다 단일 테스트를 우선 실행)
- 포맷: `./gradlew spotlessApply` (google-java-format). 직접 들여쓰기를 맞추지 말고 이 명령에 맡긴다
- 서버 실행: `./gradlew bootRun` (포트 8080, P2부터 사용)

## Architecture (ADR 0003)
- 기본 패키지는 `com.prlens`이고 그 아래에 역할 단위 패키지를 평면으로 둔다. `domain`/`api` 하위 구분은 두지 않는다
  - 공유: `model`(트랙 사이를 오가는 불변 record), `support`(재시도, 마스킹, 경고, 예외 기반 타입)
  - T2 GitHub 연동: `github`, `pullrequest`, `diff`, `glob`, `filter`, `context`
  - T1 리뷰 엔진: `llm`, `review`
  - T3 CLI 출력: `codec`, `output`, `config`, `cli`
- 패키지별 클래스와 의존 규칙의 원본은 `docs/specs/pr-lens-p1-cli/design.md`의 "계층과 의존 방향", "패키지 배치"다. 새 패키지는 이 표를 같은 PR에서 고칠 때만 추가한다
- 의존 규칙 (ArchUnit 테스트가 강제)
  - `model`은 JDK 외에 의존하지 않는다 (Jackson 애너테이션도 넣지 않는다)
  - `review`는 `cli`, `output`, `github`, `config`에 의존하지 않고, `LlmClient` 인터페이스만 쓴다
  - `context`, `pullrequest`는 `GitHubClient` 인터페이스만 쓴다
  - Spring 애너테이션은 `cli`의 조립 코드에만 둔다
- diff 해석, 필터, glob, 검증, 직렬화, 비용 계산은 I/O 없는 순수 함수로 만든다. I/O(GitHub, Claude, 파일, 표준 스트림)는 얇은 어댑터로 가장자리에 둔다
- 오류는 `PrLensException` 하위 타입으로 던지고 `cli`에서 한 번만 종료 코드로 바꾼다. HTTP API의 에러 응답(P2부터)은 `ErrorResponse { code, message, details }`다 (ADR 0002)

## Gotchas
- 전환 중: P1 작업 1.1 전까지 소스는 `com.example.starter`와 메모 샘플(`memo`, `common`)이다. 새 코드를 메모 샘플의 `{domain,api}` 구조로 만들지 않는다
- 출력: stdout에는 최종 결과만 쓴다. 진행·경고·오류는 stderr. `println`과 `%n`을 쓰지 않고 `\n`을 직접 붙인다 (OS와 무관하게 같은 바이트)
- 저장소 내 경로는 `/` 구분자의 `String`으로만 다룬다. `java.nio.file.Path`로 바꾸지 않는다
- Jackson은 3(`tools.jackson`)만 쓴다. Anthropic SDK가 Jackson 2(`com.fasterxml.jackson.databind`)도 끌어오므로 import를 확인한다
- 비밀값(`GITHUB_TOKEN`, `ANTHROPIC_API_KEY`)은 환경변수에서만 읽고 출력·로그·프롬프트에 넣지 않는다
- 테스트는 JUnit 6이다. Spring Boot 4의 `@WebMvcTest`는 `org.springframework.boot.webmvc.test.autoconfigure`에 있다 (P2부터 사용)
