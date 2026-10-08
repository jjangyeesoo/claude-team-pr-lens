---
paths:
  - "backend/**"
---
# 테스트 규칙
- 도구: JUnit Jupiter 6 + AssertJ. 속성 기반 테스트는 jqwik, 아키텍처 규칙은 ArchUnit을 쓴다
- 아키텍처 규칙은 `src/test/java/com/prlens/ArchitectureTest.java`에 모은다. 대상 클래스가 생긴 규칙에서는 `allowEmptyShould(true)`를 뗀다. ArchUnit이 못 보는 것(문자열 리터럴의 `%n`)은 `SourceConventionsTest`가 소스를 읽어 검사한다
- 순수 함수(diff, glob, 필터, 검증, 직렬화, 비용 계산)는 Spring 없이 테스트한다
- 속성 테스트: 설계 문서의 Property 하나를 `@Property` 메서드 하나로 구현하고, 바로 위에 태그 주석을 단다: `// Feature: pr-lens-p1-cli, Property N: <이름>`. `tries`는 100 이상. 실패한 반례는 예시 테스트로 고정한다
- 공용 생성기는 `src/test/java/com/prlens/testkit/`에 둔다
- 외부 경계(`GitHubClient`, `LlmClient`, `Sleeper`)에만 가짜 구현을 쓴다. 같은 모듈의 객체는 실제 객체를 쓴다
- 실제 GitHub·Claude API를 호출하는 테스트는 환경변수가 있을 때만 돌게 하고 CI에서는 실행하지 않는다
- 시간과 대기는 `Clock`, `Sleeper`를 주입해 고정한다. 테스트에서 실제로 기다리지 않는다
- 픽스처는 `src/test/resources/fixtures/<name>/`에 둔다
  - 비밀정보와 개인정보는 가짜 값으로 바꾼다
  - `.env`, `secrets/`, `package-lock.json` 같은 경로는 JSON 안의 데이터로만 둔다. 그 이름의 실제 파일을 만들지 않는다 (보호 hook이 막는다)
  - `expected.md`만 고쳤을 때는 Stop hook이 검증을 건너뛰므로 `./gradlew test`를 직접 실행한다
- 웹 계층(P2부터): `@WebMvcTest(XxxController.class)` + 필요한 빈만 `@Import`. 전체 컨텍스트(`@SpringBootTest`)는 통합 테스트에만 쓴다
- 테스트 메서드 이름은 동작을 설명하는 camelCase 문장: `parseRejectsOverlappingHunks...`
- 버그 수정 PR에는 그 버그를 재현하는 테스트가 반드시 포함된다
