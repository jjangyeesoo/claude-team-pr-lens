# PR Lens

팀 컨텍스트(`CLAUDE.md`, `.claude/rules/`, PR에 링크된 스펙)를 기준으로 GitHub PR을 리뷰하는 도구입니다. 3명이 5주 동안 Claude Code로 만들면서 팀 개발 방법론을 실험하는 연구회 프로젝트입니다.

- **지금 상태 (2026-10-04)**: P1 스펙까지 준비됐고 PR Lens 코드는 아직 없습니다. 소스는 스타터의 메모 샘플 그대로입니다. 진행 상황과 다음 할 일은 [`docs/HANDOFF.md`](docs/HANDOFF.md)에 있습니다.
- **단계**: P1 CLI(`prlens review <PR URL>`) → P2 webhook 자동 리뷰와 저장 → P3 웹 조회
- **구조**: 모노레포. `backend/`(CLI와 P2부터 서버), `web/`(P3부터 조회 화면)
- backend: Spring Boot 4.1 · Java 17 · Gradle (Kotlin DSL) · Spotless(google-java-format)
- web: Next.js 16 (App Router) · React 19 · TypeScript · ESLint · Prettier · Vitest
- 메모 샘플: 스타터의 메모 API(`/api/v1/memos`)와 메모 화면(`/memos`)이 남아 있습니다. backend는 P1 작업 1.1에서, web은 P3에서 지웁니다.
- 필요한 것: JDK 17+, Node 24+(`web/.nvmrc`), git. hook은 Java 단일 파일 스크립트(`java Hook.java`)라 hook 자체에는 Node가 필요 없습니다.

이 저장소의 Claude Code 설정(`.claude/`, hooks, 스킬)은 `claude-team-starter`에서 가져와 PR Lens에 맞게 고친 것입니다. 아래 "자동으로 강제되는 것" 이후의 절은 그 설정이 어떻게 동작하는지 설명합니다.

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/study-project/PRD.md`](docs/study-project/PRD.md) | 제품 요구사항 (FR-1~14) |
| [`docs/study-project/ROADMAP.md`](docs/study-project/ROADMAP.md) | 5주 계획과 주차별 트랙 |
| [`docs/study-project/PLAYBOOK.md`](docs/study-project/PLAYBOOK.md) | 역할, 승인 규칙, 지표, 작업 규칙 |
| [`docs/specs/`](docs/specs/) | 단계별 스펙 (`pr-lens-p1-cli`, `pr-lens-p2-automation`, `pr-lens-p3-web`) |
| [`docs/adr/`](docs/adr/README.md) | 아키텍처 결정 기록 |
| [`docs/spikes/`](docs/spikes/) | 기술 검증 기록 |
| [`docs/claude-code-team-methodology.md`](docs/claude-code-team-methodology.md) | 팀 방법론 가이드 ("소규모 IT 팀을 위한 Claude Code 개발 방법론") |

## 빠른 시작

```bash
cd backend && ./gradlew test        # Windows PowerShell: .\gradlew.bat test
cd ../web && npm ci && npm run verify
claude                              # 저장소 루트에서 실행. 처음에는 폴더 신뢰(trust)를 묻습니다. 신뢰해야 팀 allow 규칙이 적용됩니다
```

메모 샘플 화면을 보려면 `backend`에서 `./gradlew bootRun`, `web`에서 `npm run dev`를 실행하고 http://localhost:3000/memos 를 엽니다. backend 주소는 `web/.env.local`의 `API_BASE_URL`로 바꿀 수 있습니다(기본 `http://localhost:8080`).

첫 세션에서 확인할 것:
1. `/context`에 루트 `CLAUDE.md`가 로드되어 있는지. `backend/`나 `web/` 파일을 읽게 한 뒤 다시 보면 그 폴더의 `CLAUDE.md`와 해당 rules가 추가로 로드됩니다
2. `/hooks`에 `PreToolUse`(ProtectFiles)와 `Stop`(VerifyOnStop)이 보이는지
3. `/`를 입력하면 `/spec`, `/pr-ready`, `/adr`, `/new-endpoint`, `/new-page`가 나오는지 (`/new-endpoint`와 `/new-page`는 아직 메모 샘플 기준이라 P2, P3 전에 고쳐야 합니다)

## 구성

```
CLAUDE.md                        팀 공용 지시문 (구조, 워크플로, 스택 간 규칙, 컨벤션)
backend/
├── CLAUDE.md                    backend 명령·아키텍처 (backend 파일을 다룰 때만 로드)
└── build.gradle.kts, src/ ...   CLI와 서버 (지금은 메모 샘플)
web/
├── CLAUDE.md                    web 명령·아키텍처 (web 파일을 다룰 때만 로드). 첫 줄의 @AGENTS.md는 Next.js가 관리
├── AGENTS.md                    Next.js가 생성: "번들된 문서(node_modules/next/dist/docs)를 먼저 읽어라"
└── src/app, src/lib/api ...     조회 화면 (지금은 메모 샘플)
.claude/
├── settings.json                권한(allow/ask/deny)과 hooks (팀 공유)
├── hooks/
│   ├── ProtectFiles.java        PreToolUse: .env, 비밀 설정, gradle wrapper, lock 파일, 산출물 수정 차단
│   └── VerifyOnStop.java        Stop: 바뀐 스택만 포맷 + 검증, 실패하면 계속 고치게 함
├── rules/
│   ├── backend/testing.md       backend/** 를 다룰 때 로드 (JUnit 6, jqwik, ArchUnit, 픽스처)
│   ├── backend/api-design.md    REST API 규칙 (paths가 메모 샘플의 api 패키지 기준. P2 전에 수정)
│   └── frontend/nextjs.md       web/src/** 를 다룰 때 로드
├── skills/
│   ├── spec/                    /spec <기능>: 인터뷰 후 docs/specs/에 작은 스펙 작성
│   ├── new-endpoint/            /new-endpoint <API>: 컨벤션대로 TDD 방식 엔드포인트 추가
│   ├── new-page/                /new-page <화면>: 컨벤션대로 backend 데이터를 보여 주는 페이지 추가
│   ├── pr-ready/                /pr-ready: PR 단위·크기 확인, 바뀐 스택 검증, 리뷰, PR 설명 초안
│   └── adr/                     /adr <제목>: 아키텍처 결정 기록
└── agents/reviewer.md           새 컨텍스트에서 diff를 스펙 작업과 패키지 규칙(ADR 0003) 기준으로 리뷰하는 서브에이전트
docs/
├── study-project/               PRD, ROADMAP, PLAYBOOK
├── specs/pr-lens-*/             단계별 스펙 (requirements.md, design.md, tasks.md)
├── specs/_template.md           작은 스펙용 템플릿 (/spec이 사용)
├── adr/                         결정 기록 (README.md는 CLAUDE.md가 import)
├── spikes/                      기술 검증 기록
├── spec-review/, HANDOFF.md     스펙 검토 결과와 인수인계 (스펙 수정이 끝나면 삭제)
└── claude-code-team-methodology.md   팀 방법론 가이드
.github/
├── pull_request_template.md     작업 번호, 검증 증거, AI 리뷰 지적·반영 건수
└── workflows/ci.yml             backend, web 검증 (브랜치 보호 필수 체크용. GitHub에서 실행해 본 적은 없음)
.worktreeinclude                 claude --worktree 때 복사할 로컬 설정 파일 목록
```

## 개발 흐름

```
스펙의 tasks.md에서 작업을 고름        → 예: docs/specs/pr-lens-p1-cli/tasks.md 작업 5
"pr-lens-p1-cli 작업 5.1 구현해"       → 작업 본문, 그 작업의 요구사항 인수 기준, design.md의 관련 절만 읽고 테스트 먼저, 구현
(응답이 끝날 때마다)                    → Stop hook이 바뀐 스택만 검증 (backend: spotlessApply test, web: npm run verify)
/pr-ready                              → 검증 증거, reviewer 리뷰, PR 설명 초안
사람이 PR을 올리고 동료가 리뷰
```

- PR 하나는 `tasks.md`의 상위 작업 하나입니다. 작업 목록에 "PR 경계"가 표시된 작업은 그 경계대로 나눕니다. 크기 목표는 `src/main` 변경 400줄 이하입니다.
- 스펙에 없는 새 기능은 `/spec`으로 스펙을 먼저 만듭니다. 작은 변경(한 문장으로 설명되는 diff)은 스펙 없이 바로 요청해도 됩니다.
- 스펙에서 결정 대기(D-n, G-n)로 표시된 항목은 팀이 정합니다. Claude는 제안값으로 구현하고 임의로 확정하지 않습니다.

**스택 간 규칙**: web이 쓰는 API가 바뀌면 backend DTO와 `web/src/lib/api/types.ts`를 같은 PR에서 함께 바꿉니다. P3 전에는 web이 PR Lens API를 쓰지 않으므로 backend만 바꿉니다. 비즈니스 로직은 backend에만 두고, web은 조회와 표시만 합니다.

## 자동으로 강제되는 것 vs 권고

| 항목 | 방식 | 위치 |
|---|---|---|
| `.env`, `.env.*`, `application-local.*`, `application-secret.*`, `secrets/` 읽기·수정 금지 (모든 깊이). allow된 명령에 `--output`, `--init-script`, `-I`, `--config`, `-c`를 붙이는 것도 금지 | **강제** (permissions deny) | `.claude/settings.json` |
| 위 파일 + gradle wrapper, `package-lock.json`, `node_modules/`, `.next/`, `.git/` 수정 금지. Windows 별칭 경로(`파일:스트림`, `ENV~1` 같은 8.3 이름, `gradlew.`처럼 끝에 점·공백을 붙인 이름)도 차단 | **강제** (PreToolUse hook, 2차 방어선) | `ProtectFiles.java` |
| 코드 변경 후 포맷과 테스트 통과 (바뀐 스택만) | **강제** (Stop hook, 실패 시 한 번 되돌려 보냄) | `VerifyOnStop.java` |
| 모든 `build.gradle.kts`, `settings.gradle.kts`, `libs.versions.toml`, `package.json` 수정, `.claude/` 아래 파일 수정(settings, hooks, rules, skills, agents), `npm install`, `git push` | **확인 요청** (permissions ask) | `.claude/settings.json` |
| 줄 끝 LF 통일 (`* text=auto eol=lf`) | **강제** (git) | `.gitattributes` |
| 패키지 의존 규칙 (ADR 0003) | **강제 예정** (ArchUnit 테스트, P1 작업 1.2에서 추가). 그 전에는 권고 | `backend/CLAUDE.md`, P1 설계 문서 |
| API 규칙, Next.js 규칙, 테스트 스타일, PR 단위 | 권고 (CLAUDE.md, rules) + reviewer 에이전트가 검토 | `CLAUDE.md`, `*/CLAUDE.md`, `.claude/rules/` |

> **`claude`는 저장소 루트에서 실행하세요.** 권한 규칙의 `/`는 프로젝트 루트를 뜻하도록 `/**/` 형태로 적어 두었지만, 하위 폴더에서 시작하면 hook과 규칙의 기준 위치가 달라질 수 있습니다.

### 강제의 한계 (알고 쓰세요)
- **deny 규칙이 막는 것**: Claude의 파일 도구(Read/Edit/Write), 그리고 Claude Code가 알아보는 Bash 파일 명령(`cat`, `head`, `sed`, `tee`, `> file` 리다이렉션 등).
  - 공식 문서에 따르면 Read deny는 같은 경로의 Edit/Write와 새 파일 생성도 막습니다. 다만 NotebookEdit은 막지 않기 때문에 `Edit` deny를 함께 두었습니다.
- **막지 못하는 것**
  - 파일 이름을 명령에 드러내지 않고 읽는 명령. 예: `.env`가 있는 폴더에서 `grep -r KEY .`
  - 스크립트나 프로그램이 간접적으로 파일을 여는 경우. 예: Python, Node, Gradle 태스크, Next.js의 `.env.local` 로딩
  - 이런 경우까지 막으려면 OS 수준 격리인 `/sandbox`를 켜세요.
- **ProtectFiles**는 파일 편집 도구(Edit, Write, NotebookEdit)만 검사합니다. 경로는 프로젝트 폴더 기준으로 판정합니다(프로젝트가 `C:\secrets\...` 아래 있어도 오탐하지 않음). 입력을 해석하지 못하면 편집을 허용합니다(fail-open). `npm install`이 lock 파일을 바꾸는 것은 막지 않습니다(ask 규칙으로 사람이 확인).
  - 심볼릭 링크나 junction을 거친 경로는 실제 경로로 한 번 더 검사합니다. 실제 경로를 구하지 못하면 문자열 검사 결과만 씁니다.
- **`.env.example`도 보호 대상**입니다(`.env.*` 패턴). Claude가 읽어야 하는 예시 파일은 `env.example`처럼 점 없이 이름 붙이세요.
- **allow 규칙의 `*`는 뒤 인자를 제한하지 않습니다.** 파일을 쓰거나 다른 설정을 끌어오는 옵션(`git diff --output`, `gradlew --init-script`·`-I`, `npm run`·`vitest`의 `--config`·`-c`)은 deny로 막았지만, 목록에 없는 옵션이나 추가 태스크(`./gradlew test publish`)는 막지 못합니다.

### 운영 규칙 (설정으로 막지 못하는 부분)
위 한계 가운데 설계상 남겨 둔 세 가지는 사람이 지킵니다.

- **셸로 보호 파일을 고치지 않습니다.** ProtectFiles는 Edit, Write, NotebookEdit만 보기 때문에 Bash나 PowerShell 명령으로 쓰는 것은 막지 못합니다. 보호 파일(gradle wrapper, lock 파일, `.git/`)을 바꿔야 하면 사람이 직접 하거나, Claude가 제안한 명령을 읽어 보고 승인합니다. 셸까지 막아야 하는 작업은 `/sandbox`를 켜고 합니다.
- **신뢰할 수 없는 코드를 이 폴더에 체크아웃한 채로 Claude를 실행하지 않습니다.** Stop hook은 응답이 끝날 때 승인 없이 `gradlew spotlessApply test`와 `npm run verify`를 실행하므로, 그 브랜치의 빌드 스크립트와 테스트 코드가 그대로 실행됩니다. 외부 기여자의 PR이나 출처를 모르는 브랜치는 내용을 먼저 읽고, 필요하면 별도 폴더나 격리된 환경에서 엽니다.
- **보호 대상은 deny 규칙에도 함께 넣습니다.** ProtectFiles는 입력을 해석하지 못하면 편집을 허용하는(fail-open) 2차 방어선입니다. 비밀값처럼 반드시 막아야 하는 파일을 hook에만 추가하지 말고 `settings.json`의 deny에도 넣습니다.

### VerifyOnStop 동작
- **스택별로 판단합니다.** 설정은 hook 안의 `STACKS` 목록 하나입니다.

  | 스택 | 변경을 감지하는 폴더 | 실행 명령 (그 폴더에서) | 실행 전 조건 |
  |---|---|---|---|
  | backend | `backend/` | `gradlew spotlessApply test` | `backend/gradlew` 존재 |
  | web | `web/` | `npm run verify` (format → lint → typecheck → test) | `web/node_modules` 존재 (없으면 `npm ci` 안내) |

- **검증 대상에서 빠지는 경로**: `docs/`, `.claude/`, `.github/`, `*.md`. 스택 폴더 밖의 파일(루트 설정 등)도 검증을 일으키지 않습니다.
- **판단 기준은 "스택 폴더의 현재 내용"입니다.** 커밋, 스테이징, 미스테이징, untracked 파일을 모두 합친 내용(.gitignore 대상 제외)을 git 트리 해시로 계산하고, 마지막으로 통과한 내용과 다르면 실행합니다. 해시는 실제 인덱스를 복사한 임시 인덱스로 계산하므로 스테이징 상태는 바뀌지 않습니다.
  - Claude가 수정하고 곧바로 커밋하거나, pull·브랜치 전환으로 내용이 바뀌어도 검증합니다.
  - 통과한 내용을 그대로 커밋하거나 `git add`만 한 경우는 다시 돌리지 않습니다.
  - 처음 실행이고 작업 트리가 깨끗하면 커밋된 상태를 기준으로 삼고 건너뜁니다.
- **두 스택이 모두 바뀌면 병렬로 실행**합니다. 전체 제한 시간은 540초입니다(hook timeout 600초보다 짧게).
- **건너뛰는 경우**: 질문만 한 턴, 문서만 바뀐 경우, 마지막 통과 이후 내용이 같은 경우. 통과 기록은 `build/claude-verify/state.properties`에 스택별로 저장됩니다.
- **실패하면** 실패한 스택의 마지막 60줄(실패한 테스트 이름, 기대값/실제값, 타입·lint 오류)을 Claude에게 보내 계속 고치게 합니다.
  - 전체 로그는 `build/claude-verify/<스택>.log`에 있습니다.
  - **다시 실패하면 더 막지 않고** 사용자에게 알립니다. 무한 루프를 막기 위해서입니다.
- **도구가 실행조차 안 되면** Claude를 막지 않고 사용자에게만 알립니다. 의존성 미설치(`node_modules` 없음), 명령 없음(`npm`이 PATH에 없음. 실행 전에 `where`/`command -v`로 확인), 시간 초과가 해당합니다. 코드 문제가 아닌데 Claude가 "고치려" 들지 않게 하기 위해서입니다. 이때는 통과 기록을 남기지 않으므로, 도구가 준비되면 다음 Stop에서 다시 검증합니다.
- **주의: 사용자가 직접 작성 중인 미완성 코드도 검증 대상입니다.** 테스트가 깨진 상태로 두고 Claude에게 질문만 해도 한 번 막힙니다. 이때 Claude가 코드를 고치려 하면 멈추고 방향을 알려 주세요.
- **주의: 포맷터(`spotlessApply`, `prettier --write`)는 응답이 끝난 뒤 파일을 다시 포맷합니다.** Claude가 읽은 내용과 줄바꿈·들여쓰기가 달라질 수 있습니다. 기능에는 영향이 없습니다.
- git 저장소가 아니면 아무것도 하지 않습니다. 스택 폴더가 없으면 그 스택은 건너뜁니다(backend만 쓰는 저장소에서도 그대로 동작).

## 다른 프로젝트에 적용하기: 공통 코어와 스택별 오버레이

이 절은 스타터에서 온 설명입니다. 이 저장소의 Claude Code 설정을 다른 프로젝트에 가져갈 때 참고합니다.

이 저장소는 "공통 코어 + Java/Gradle 오버레이 + Next.js 오버레이"가 합쳐진 상태입니다. 기존 프로젝트나 다른 스택에 가져갈 때는 아래 표를 기준으로 복사하고 수정하세요. **FE가 없는 프로젝트라면 `web/`, `.claude/rules/frontend/`, `skills/new-page`를 지우면 됩니다.** hook은 폴더가 없는 스택을 자동으로 건너뜁니다.

| 파일 | 구분 | 다른 프로젝트에 적용할 때 |
|---|---|---|
| `.claude/skills/spec`, `pr-ready`, `adr` | 공통 코어 | 그대로 복사. `pr-ready`의 스택별 검증 명령만 수정 |
| `.claude/agents/reviewer.md` | 공통 코어 (+스택 항목) | backend/web 규칙 항목을 프로젝트 구조에 맞게 수정 |
| `docs/specs/_template.md`, `docs/adr/`, PR 템플릿 | 공통 코어 | 그대로 복사 (ADR 내용만 새로 작성) |
| `.claude/hooks/ProtectFiles.java` | 공통 코어 | 보호 패턴(`protectedReason`)만 수정 |
| `.claude/hooks/VerifyOnStop.java` | 코어 로직 + 스택 설정 | `STACKS` 목록만 수정 (폴더, 명령, 실행 전 조건, 실패 판정 문자열). Maven이면 `mvnw`, Python이면 `uv run pytest` 등 |
| `.claude/settings.json` deny/ask | 공통 코어 | 그대로. 보호 대상이 바뀌면 `ProtectFiles`, `.gitignore`와 함께 수정 |
| `.claude/settings.json` allow | **스택별** | 빌드 명령에 맞게 교체 (Bash와 PowerShell 둘 다) |
| 루트 `CLAUDE.md` | 공통 코어 | 구조와 스택 간 규칙만 프로젝트에 맞게 수정 |
| `backend/CLAUDE.md`, `web/CLAUDE.md` | **스택별** | Commands/Architecture/Gotchas를 새로 작성 (`/init`으로 초안을 만든 뒤 병합) |
| `.claude/rules/*`, `skills/new-endpoint`, `skills/new-page` | **스택별** | 프레임워크에 맞게 새로 작성. rules의 `paths`를 폴더 구조에 맞출 것 |
| `backend/build.gradle.kts`의 `spotless {}` | **스택별** | 팀 포맷터로 교체. **기존 코드베이스라면 `ratchetFrom("origin/main")` 주석을 풀어** 첫 실행에 전체 파일이 재포맷되지 않게 함 |
| `backend/build.gradle.kts`의 `testLogging {}` | **스택별 (필수)** | Stop hook이 Claude에게 넘기는 실패 정보가 이 설정에 달려 있음. 빼지 말 것 |
| `web/package.json`의 `verify` 스크립트 | **스택별 (필수)** | Stop hook이 실행하는 진입점. 포맷 → lint → 타입 → 테스트 순서를 유지 |
| `.github/workflows/ci.yml` | 스택별 | job을 스택 구성에 맞게 수정. action 버전은 도입 시점의 최신으로 확인 |
| `.gitignore`의 Claude Code·Secrets 항목, `.worktreeinclude` | 공통 코어 | deny 규칙, ProtectFiles와 같은 목록을 유지 |
| `.gitattributes` | 공통 코어 | `* text=auto eol=lf` 유지. 빼면 Windows(`core.autocrlf=true`)에서 포맷터가 LF로 바꾼 파일이 전부 "수정됨"으로 보이고 CI의 `format:check`가 실패함. 기존 저장소에 넣을 때는 `git add --renormalize .` 커밋을 따로 만들 것 |
| `gradlew` 실행 권한 | **스택별** | 복사 후 `git update-index --chmod=+x backend/gradlew` 확인 (hook은 `sh gradlew`로 실행해 권한이 없어도 동작) |

기본 브랜치 이름: `reviewer` 에이전트와 `/pr-ready`는 `main`을 기준으로 diff를 봅니다. 다른 이름이면 두 파일을 수정하세요.

### Next.js 관련 메모
- **`web/AGENTS.md`는 Next.js가 관리합니다.** `create-next-app`이 `AGENTS.md`와 `@AGENTS.md`만 담긴 `CLAUDE.md`를 만들고, `next dev`가 이 블록을 다시 써 넣습니다. 팀 지시는 `web/CLAUDE.md`의 `@AGENTS.md` 아래에 적고, `AGENTS.md`는 고치지 않습니다(Prettier에서도 제외).
- **Next.js 16은 학습 데이터와 다를 수 있습니다.** `AGENTS.md`가 Claude에게 `node_modules/next/dist/docs/`의 번들 문서를 먼저 읽게 합니다. 그래서 `npm ci` 전에는 이 문서가 없습니다.
- **`next/font/google`을 쓰지 않습니다.** 빌드할 때 네트워크가 필요해 오프라인이나 제한된 CI에서 빌드가 깨집니다.
- **`typecheck`는 `next typegen && tsc --noEmit`입니다.** `LayoutProps` 같은 라우트 타입이 생성되어야 `tsc`가 통과합니다.

### OS 관련 메모
- hooks는 exec form(`"command": "java"`, `"args": [...]`)으로 등록되어 셸을 거치지 않습니다. 그래서 Windows, macOS, Linux에서 똑같이 동작합니다.
- **Windows**
  - Gradle은 `cmd /c .\gradlew.bat`, npm은 `cmd /c npm`으로 실행합니다 (`npm`은 `npm.cmd`라 cmd를 거쳐야 함).
  - Gradle 출력은 OS 인코딩(한국어 Windows는 MS949)으로, Node 도구 출력은 UTF-8로 읽고, Claude에게는 UTF-8로 전달합니다.
  - **프로젝트 경로에 `&`가 있으면 `gradlew.bat` 자체가 동작하지 않습니다.** Gradle의 한계입니다. 이 경우 Stop hook은 검증을 건너뛰었다고 알립니다.
- **macOS/Linux**: Gradle은 `sh ./gradlew`, npm은 `npm`으로 실행합니다. 이 OS들에서는 직접 실행해 보지 않았으니 처음 도입할 때 한 번 확인하세요.

## 검증 기록

**2026-10-04 (PR Lens 저장소로 전환), Windows 11, JDK 17, Node 24**
- backend `gradlew spotlessCheck test` 통과(테스트 11개), web `npm run verify` 통과(테스트 5개)
- `ProtectFiles.java` 보강: 이름 끝의 점·공백(`gradlew.`, `.git./config`)과 심볼릭 링크·junction을 거친 경로를 차단. 훅에 경로를 직접 넣어 수정 전후를 비교했습니다
- `settings.json`: `.claude/` 아래 수정은 ask, allow된 명령에 `--output`·`--init-script`·`-I`·`--config`·`-c`를 붙이는 형태는 deny. `git log --output`과 `npx vitest run --config`가 실제 세션에서 거부되는 것을 확인했습니다
- `VerifyOnStop.java`: 복제본에서 실패 테스트를 넣으면 차단하고 고치면 통과하는 것을 확인했습니다
- 의존성 조합(jqwik 1.10.1, ArchUnit 1.5.1, picocli, Anthropic SDK와 JUnit 6.0.3): [`docs/spikes/2026-10-04-build-stack.md`](docs/spikes/2026-10-04-build-stack.md)
- **확인하지 못한 것**: macOS/Linux 실행, GitHub에서의 CI 실행(워크플로는 작성했지만 원격 저장소가 아직 없음. 같은 명령을 로컬에서 실행해 통과하는 것만 확인), `Edit(/.claude/**)` ask 규칙이 실제로 승인을 묻는지

아래는 스타터 시절의 기록입니다.

**2026-09-30 (QA 에이전트 검증 후 수정), Windows 11, JDK 17, Node 24, 한글·공백·괄호 경로의 복제본**
- 별도 QA 에이전트가 찾은 버그 4건을 고치고 재현 시나리오로 확인했습니다.
  - Windows에서 `npm`이 PATH에 없으면 "코드 실패"로 막던 문제: `cmd /c`는 없는 명령에 종료 코드 1을 돌려줘서 실패와 구분되지 않았음 → 실행 전 확인으로 변경, 알림만
  - `core.autocrlf=true` 환경에서 첫 검증 뒤 web 파일이 전부 "수정됨"으로 보이던 문제 → `.gitattributes` LF 통일. clone 직후 `format:check` 통과, 검증 뒤 작업 트리 깨끗함
  - git 경고(stderr)가 출력에 섞이거나 작업 트리 rename(` R`)이 있으면 hook이 예외로 죽던 문제 → git stderr 분리, porcelain 파싱 제거
  - 도구 실행 불가 알림 뒤 커밋된 오류가 다시 검증되지 않던 문제 → 내용 해시 방식으로 변경
- `VerifyOnStop.java` 시나리오 16개 통과: 첫 실행 기준 기록, 병렬 통과, 재실행·`git add`만·통과 내용 커밋은 건너뜀, web 타입 오류는 web만 차단, 루프 방지, 되돌리면 건너뜀, 깨진 테스트 커밋 차단, revert 건너뜀, `node_modules` 없음 알림 후 복구하면 커밋된 오류 차단, `npm` 없음 알림, rename, 문서만 변경
- `ProtectFiles.java` 15개 사례 통과: 프로젝트 기준 판정(보호 이름이 든 상위 폴더에서 오탐 없음), `.env::$DATA`, `ENV~1`, `PACKAG~1.JSO` 차단
- **폴더를 신뢰한 실제 세션**(`claude -p`, Claude Code 2.1.285)에서 권한과 hook 확인
  - allow: `cd web && npm run verify`, `cd backend && ./gradlew test`가 확인 없이 실행됨 (`cd`가 붙어도 규칙에 맞음)
  - ask: `npm install left-pad`, `web/package.json` 수정은 승인 대기 → 헤드리스라 거부됨. **`--permission-mode acceptEdits`에서도 ask 규칙이 우선**함
  - deny: 루트 `.env`, `backend/secrets/x.txt`(하위 폴더) Read와 `cat .env` 모두 거부
  - hook: `backend/gradlew` 수정을 ProtectFiles가 차단, web 파일 수정 후 Stop hook이 web만 검증(테스트 5개 통과)
  - 참고: 신뢰하기 전에는 같은 명령들이 allow를 무시당해 모두 승인 대기로 막힘. 도입 첫날 전원이 한 번 대화형으로 실행해 신뢰해야 함

**2026-09-30 (풀스택 확장), Claude Code 2.1.285, Windows 11, JDK 17, Node 24**
- backend: `./gradlew spotlessCheck test` 통과 (backend/로 옮긴 뒤)
- web: `npm run verify`(format, lint, typecheck, test) 통과, `npm run build` 통과 (backend 없이 빌드됨, `/memos`는 요청 시점 렌더링)
- `VerifyOnStop.java` 시나리오
  - 두 스택 모두 변경 → 병렬 실행 후 통과 / 다시 실행 → 건너뜀
  - web에 lint·타입 오류 → web만 차단(exit 2), backend 보고 없음 / 같은 실패 + `stop_hook_active` → 차단하지 않고 알림
  - backend 테스트 실패 → backend만 차단, 실패한 테스트 이름과 기대값 전달
  - 문서만 변경 → 건너뜀 / `web/node_modules` 없음 → 차단하지 않고 `npm ci` 안내
- `ProtectFiles.java`: `package-lock.json`, `node_modules/`, `.next/`, Windows 경로의 `.env.local`, `backend/` 아래 gradle wrapper와 `application-local.*` 차단. `web/src`, `web/package.json`은 허용(package.json은 ask 규칙)
- 실제 `claude -p` 실행: Claude가 web 테스트를 추가하자 Stop hook이 web만 검증해 5개 테스트 통과. 폴더 신뢰 전이라 allow 규칙은 무시되어 명령 실행은 거부됨(의도된 동작)
- **확인하지 못한 것**: macOS/Linux 실행, GitHub에서의 CI 실행. 첫 도입 때 확인하세요

**2026-09-27 (초기 버전, backend 단일 구조)**
- `ProtectFiles.java`: 22가지 입력이 모두 기대한 종료 코드 (대소문자, 상대경로, NotebookEdit, 공백·한글 경로, content 안의 `"file_path"` 문자열 오탐 없음)
- `VerifyOnStop.java`: 한글·공백·괄호가 들어간 경로의 복제본에서 16개 시나리오 통과 (한글 파일명, 깨진 테스트를 커밋한 경우, 재실패 시 루프 방지, `&` 경로에서 차단하지 않고 알림)
- 실제 `claude -p` 실행: gradle wrapper 수정 시도를 PreToolUse hook이 차단, 테스트를 깨는 수정을 Stop hook이 전달했고 Claude는 테스트를 지우지 않고 원인을 분석함

## 팀 운영 규칙 (요약)
자세한 규칙은 [플레이북](docs/study-project/PLAYBOOK.md)에 있습니다.

- `CLAUDE.md`, `.claude/` 변경은 `context` 라벨을 붙여 PR로 올리고, 그 주의 컨텍스트 담당이 승인합니다.
- Claude가 같은 실수를 반복하면 규칙을 추가하는 PR을, 규칙이 없어도 잘하는 것이 있으면 삭제하는 PR을 올립니다.
- 개인 설정은 `CLAUDE.local.md`와 `.claude/settings.local.json`에 둡니다 (gitignore됨).
