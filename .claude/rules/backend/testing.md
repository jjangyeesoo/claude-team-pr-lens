---
paths:
  - "backend/**"
---
# 테스트 규칙
- 단위 테스트: 도메인 로직은 Spring 없이 순수 JUnit 5 + AssertJ로 테스트한다 (예: `MemoServiceTest`)
- 웹 계층: `@WebMvcTest(XxxController.class)` + 필요한 빈만 `@Import`. 전체 컨텍스트(`@SpringBootTest`)는 통합 테스트에만 쓴다
- mock은 외부 시스템 경계(HTTP 클라이언트, 메시지 브로커 등)에만 쓴다. 같은 모듈의 서비스는 실제 객체를 쓴다
- 시간 의존 로직은 `Clock`을 주입해 고정한다
- 테스트 메서드 이름은 동작을 설명하는 camelCase 문장: `createRejectsBlankTitle...`
- 버그 수정 PR에는 그 버그를 재현하는 테스트가 반드시 포함된다
