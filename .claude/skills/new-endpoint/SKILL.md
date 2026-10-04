---
name: new-endpoint
description: 팀 컨벤션에 맞춰 새 REST 엔드포인트(Controller, DTO, 서비스 메서드, 테스트)를 만든다. 새 API를 추가할 때 사용.
argument-hint: "[METHOD /path 설명]"
---
다음 엔드포인트를 추가한다: $ARGUMENTS

작업 위치: `backend/`. 기준 예시: `memo` 패키지 (`MemoController`, `CreateMemoRequest`, `MemoResponse`, `MemoService`, `MemoControllerTest`).

1. 기존 memo 패키지의 구조와 스타일을 그대로 따른다 (`.claude/rules/backend/api-design.md` 규칙 포함).
2. 실패하는 `@WebMvcTest` 테스트를 먼저 작성한다: 정상 응답, 검증 실패(400 + ErrorResponse), 없는 리소스(404)를 다룬다.
3. 도메인 로직이 있으면 서비스 단위 테스트도 작성한다.
4. 구현한 뒤 `backend/`에서 `./gradlew test --tests "<새 테스트 클래스>"`로 통과를 확인하고, 마지막에 전체 테스트를 실행한다.
5. web에서 이 API를 쓸 예정이면 응답 타입을 `web/src/lib/api/types.ts`에 추가한다.
