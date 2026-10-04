# Project: starter (Claude Code 팀 스타터 샘플)
모노레포. `backend/`는 Spring Boot REST API, `web/`은 이를 조회하는 Next.js 화면이다. 샘플 도메인은 메모(`memo`)다.
스택별 명령과 구조는 각 폴더의 CLAUDE.md에 있다 (그 폴더의 파일을 다룰 때 로드된다).

## Structure
- `backend/`: Spring Boot 4.1 / Java 17 / Gradle. 명령은 `backend/`에서 실행한다 (`cd backend && ./gradlew test`)
- `web/`: Next.js 16 / TypeScript. 명령은 `web/`에서 실행한다 (`cd web && npm run verify`)
- `docs/specs/`: 기능 스펙, `docs/adr/`: 아키텍처 결정. 결정 목록: @docs/adr/README.md

## Workflow
- 새 기능은 `/spec`으로 `docs/specs/`에 스펙을 먼저 만들고, 새 세션에서 스펙을 참조해 구현한다
- 버그 수정은 재현하는 실패 테스트를 먼저 작성한다
- 작업이 끝나면 Stop hook이 **바뀐 스택만** 검증한다 (backend: `spotlessApply test`, web: `npm run verify`). 실패하면 근본 원인을 고친다
- PR 전에 `/pr-ready`를 실행한다

## Cross-stack rules
- API가 바뀌면 backend DTO와 `web/src/lib/api/types.ts`를 **같은 PR에서** 함께 바꾼다
- 비즈니스 로직은 backend에만 둔다. web은 조회와 표시만 한다

## Conventions
- 브랜치: `feat/<issue>-<slug>`, `fix/<issue>-<slug>` · 커밋: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`)
- PR 하나 = 스펙(또는 이슈) 하나, 변경 400줄 이하를 목표로 한다
- 의존성 추가(`build.gradle.kts`, `package.json` 수정)는 사람의 승인이 필요하다. 먼저 이유를 설명하고 묻는다
- IMPORTANT: 테스트를 통과시키려고 assertion을 지우거나 테스트를 skip하지 않는다

## Compaction
- 컨텍스트를 압축할 때는 수정한 파일 목록, 연결된 스펙 경로, 실패 중인 테스트 이름을 보존한다
