# Project: PR Lens
팀 컨텍스트(`CLAUDE.md`, `.claude/rules/`, 링크된 스펙)를 기준으로 GitHub PR을 리뷰하는 도구다. 모노레포이고 단계별로 만든다: P1 CLI(`prlens review <PR URL>`), P2 webhook 자동 리뷰와 저장, P3 웹 조회.
스타터에서 출발해 web에 메모 샘플(`memos`)이 아직 남아 있다(backend는 P1 작업 1.1에서 제거했고, web은 P3에서 제거). 메모 코드를 새 코드의 본보기로 삼지 않는다.
스택별 명령과 구조는 각 폴더의 CLAUDE.md에 있다 (그 폴더의 파일을 다룰 때 로드된다).

## Structure
- `backend/`: Spring Boot 4.1 / Java 17 / Gradle. CLI와 (P2부터) 서버. 명령은 `backend/`에서 실행한다 (`cd backend && ./gradlew test`)
- `web/`: Next.js 16 / TypeScript. P3부터 사용. 명령은 `web/`에서 실행한다 (`cd web && npm run verify`)
- `docs/specs/<이름>/`: 스펙(`README.md` 색인, `requirements.md`, `design.md`, `tasks.md`), `docs/adr/`: 아키텍처 결정, `docs/spikes/`: 기술 검증 기록. 결정 목록: @docs/adr/README.md

## Workflow
- 구현은 스펙의 `tasks.md` 작업 단위로 한다. 스펙 폴더의 `README.md`(색인)를 먼저 보고, 작업 본문, 그 작업이 가리키는 요구사항 인수 기준, `design.md`의 관련 절만 읽는다. 스펙 파일은 크므로 통째로 읽지 않는다 (`/task <스펙> <번호>`가 이 절차를 따른다)
- 스펙에서 결정 대기(D-n, G-n)로 표시된 항목은 임의로 확정하지 않는다. 제안값으로 구현하고, 바꿔야 하면 먼저 묻는다
- 스펙에 없는 새 기능은 그 단계의 스펙 폴더에 요구사항, 설계, 작업을 먼저 더한다. 스펙 문서(P3의 `design.md`, `tasks.md` 등)는 `/spec <스펙>`으로 쓴다
- 버그 수정은 재현하는 실패 테스트를 먼저 작성한다
- 작업이 끝나면 Stop hook이 **바뀐 스택만** 검증한다 (backend: `spotlessApply test`, web: `npm run verify`). 실패하면 근본 원인을 고친다
- PR 전에 `/pr-ready`를 실행한다
- `.claude/`나 `CLAUDE.md`를 고칠 때는 먼저 `.claude/rules/context/editing.md`를 읽는다

## Cross-stack rules
- web이 쓰는 API가 바뀌면 backend DTO와 `web/src/lib/api/types.ts`를 **같은 PR에서** 함께 바꾼다. P3 전에는 web이 PR Lens API를 쓰지 않으므로 backend만 바꾼다 (메모 API를 지울 때도 web은 고치지 않는다)
- 비즈니스 로직은 backend에만 둔다. web은 조회와 표시만 한다

## Conventions
- 브랜치: 스펙 작업은 `feat/<track>-<단계>-<번호>-<slug>` (예: `feat/t2-p1-5-diff-parser`, 공유 작업은 `feat/shared-p1-2.1-model-types`). 번호는 PR 단위의 `tasks.md` 작업 번호다. 버그 수정은 `fix/`로 같은 형식, 작업 번호가 없는 변경은 `feat/<track>-<slug>`, 트랙과 무관한 변경은 `docs/<slug>`, `chore/<slug>`. 브랜치를 만들면 바로 push해 진행 중임을 알린다 · 커밋: Conventional Commits (`feat:`, `fix:`, `test:`, `docs:`)
- PR 하나 = `tasks.md`의 상위 작업 하나 (작업 목록에 "PR 경계"가 표시된 작업은 그 경계를 따른다). `src/main` 변경 400줄 이하를 목표로 하고, 넘으면 PR 설명에 이유를 적는다
- 의존성 추가(`build.gradle.kts`, `package.json` 수정)는 사람의 승인이 필요하다. 먼저 이유를 설명하고 묻는다
- IMPORTANT: 테스트를 통과시키려고 assertion을 지우거나 테스트를 skip하지 않는다
- 비밀값(`GITHUB_TOKEN`, `ANTHROPIC_API_KEY`)은 환경변수로만 다루고 커밋하지 않는다

## Compaction
- 컨텍스트를 압축할 때는 수정한 파일 목록, 연결된 스펙 경로와 작업 번호, 실패 중인 테스트 이름, `/task`가 보고했지만 아직 스펙 문서에 반영하지 않은 "뒤 작업에 넘길 것"을 보존한다
