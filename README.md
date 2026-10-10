# PR Lens

팀 컨텍스트(`CLAUDE.md`, `.claude/rules/`, PR에 링크된 스펙)를 기준으로 GitHub PR을 리뷰하는 도구입니다. 3명이 5주 동안 Claude Code로 만들면서 팀 개발 방법론을 실험하는 연구회 프로젝트입니다.

- **지금 상태 (2026-10-04)**: P1·P2 스펙과 P3 요구사항까지 준비됐고(팀 승인 전) PR Lens 코드는 아직 없습니다. backend는 패키지 뼈대(`com.prlens`)만 있고 web은 스타터의 메모 샘플 그대로입니다. 진행 상황과 다음 할 일은 [ROADMAP의 "진행 상황"](docs/product/ROADMAP.md#진행-상황-2026-10-04-기준)에 있습니다.
- **단계**: P1 CLI(`prlens review <PR URL>`) → P2 webhook 자동 리뷰와 저장 → P3 웹 조회. 2~4주차에 이 순서로 되는 데까지 개발하고 5주차에 발표합니다. 주차별 일정은 가이드이고 강제하지 않습니다
- **구조**: 모노레포. `backend/`(CLI와 P2부터 서버), `web/`(P3부터 조회 화면)
- backend: Spring Boot 4.1 · Java 17 · Gradle (Kotlin DSL) · Spotless(google-java-format)
- web: Next.js 16 (App Router) · React 19 · TypeScript · ESLint · Prettier · Vitest
- 메모 샘플: web에 스타터의 메모 화면(`/memos`)이 남아 있고 P3에서 지웁니다. backend의 메모 API(`/api/v1/memos`)는 P1 작업 1.1에서 지웠으므로 이 화면은 데이터를 불러오지 못합니다.
- 필요한 것: JDK 17+, Node 24+(`web/.nvmrc`), git. hook은 Java 단일 파일 스크립트(`java Hook.java`)라 hook 자체에는 Node가 필요 없습니다.

이 저장소의 Claude Code 설정(`.claude/`, hooks, 스킬)은 `claude-team-starter`에서 가져와 PR Lens에 맞게 고친 것입니다.

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/product/PRD.md`](docs/product/PRD.md) | 제품 요구사항 (FR-1~14) |
| [`docs/product/ROADMAP.md`](docs/product/ROADMAP.md) | 5주 계획과 주차별 트랙 (일정은 가이드), 진행 상황 |
| [`docs/product/PLAYBOOK.md`](docs/product/PLAYBOOK.md) | 역할, 승인 규칙, 지표, 작업 규칙 |
| [`docs/specs/`](docs/specs/README.md) | 단계별 스펙 (`pr-lens-p1-cli`, `pr-lens-p2-automation`, `pr-lens-p3-web`)과 읽는 법 |
| [`docs/adr/`](docs/adr/README.md) | 아키텍처 결정 기록 |
| [`docs/spikes/`](docs/spikes/) | 기술 검증 기록 |
| [`docs/guide/`](docs/guide/) | 팀 방법론 가이드("소규모 IT 팀을 위한 Claude Code 개발 방법론"), Claude Code 설정의 동작과 한계 |

문서 전체 지도는 [`docs/README.md`](docs/README.md)에 있습니다.

## 빠른 시작

```bash
cd backend && ./gradlew test        # Windows PowerShell: .\gradlew.bat test
cd ../web && npm ci && npm run verify
claude                              # 저장소 루트에서 실행. 처음에는 폴더 신뢰(trust)를 묻습니다. 신뢰해야 팀 allow 규칙이 적용됩니다
```

web 개발 서버는 `web`에서 `npm run dev`로 띄웁니다(http://localhost:3000). backend 주소는 `web/.env.local`의 `API_BASE_URL`로 바꿀 수 있습니다(기본 `http://localhost:8080`).

첫 세션에서 확인할 것:
1. `/context`에 루트 `CLAUDE.md`가 로드되어 있는지. `backend/`나 `web/` 파일을 읽게 한 뒤 다시 보면 그 폴더의 `CLAUDE.md`와 해당 rules가 추가로 로드됩니다
2. `/hooks`에 `PreToolUse`(ProtectFiles)와 `Stop`(VerifyOnStop)이 보이는지
3. `/`를 입력하면 `/task`, `/spec`, `/pr-ready`, `/adr`, `/new-endpoint`, `/new-page`가 나오는지 (`/new-endpoint`와 `/new-page`는 아직 메모 샘플 기준이라 P2, P3 전에 고쳐야 합니다)

## 구성

```
CLAUDE.md                        팀 공용 지시문 (구조, 워크플로, 스택 간 규칙, 컨벤션)
backend/
├── CLAUDE.md                    backend 명령·아키텍처 (backend 파일을 다룰 때만 로드)
└── build.gradle.kts, src/ ...   CLI와 서버 (지금은 패키지 뼈대)
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
│   ├── frontend/nextjs.md       web/src/** 를 다룰 때 로드
│   └── context/editing.md       .claude/** 와 CLAUDE.md 를 다룰 때 로드 (브랜치, 맞물린 파일, 권한 변경)
├── skills/
│   ├── task/                    /task <스펙> [번호]: 작업 하나 구현(브랜치, 실패 테스트, 구현). 번호가 없으면 시작 가능한 작업 목록
│   ├── spec/                    /spec <스펙> [문서]: 스펙 폴더의 다음 문서(요구사항 → 설계 → 작업 목록)를 /task가 읽는 형식으로 작성
│   ├── new-endpoint/            /new-endpoint <API>: 컨벤션대로 TDD 방식 엔드포인트 추가
│   ├── new-page/                /new-page <화면>: 컨벤션대로 backend 데이터를 보여 주는 페이지 추가
│   ├── pr-ready/                /pr-ready: PR 단위·크기 확인, 바뀐 스택 검증, 리뷰, PR 설명 초안
│   └── adr/                     /adr <제목>: 아키텍처 결정 기록
└── agents/
    ├── reviewer.md              새 컨텍스트에서 diff를 스펙 작업과 패키지 규칙(ADR 0003) 기준으로 리뷰하는 서브에이전트
    └── context-reviewer.md      .claude/ 와 CLAUDE.md 변경을 리뷰하는 서브에이전트 (맞물림, 기존 규칙·hook과의 충돌)
docs/
├── README.md                    문서 지도
├── product/                     PRD, ROADMAP, PLAYBOOK
├── specs/pr-lens-*/             단계별 스펙 (README.md 색인, requirements.md, design.md, tasks.md)
├── adr/                         결정 기록 (README.md는 CLAUDE.md가 import)
├── spikes/                      기술 검증 기록
├── spec-review/                 스펙 검토 결과 중 남은 것 (P3 스펙을 고친 뒤 삭제)
└── guide/                       팀 방법론 가이드, Claude Code 설정 설명
.github/
├── pull_request_template.md     작업 번호, 검증 증거, AI 리뷰 지적·반영 건수
└── workflows/ci.yml             backend, web 검증 (브랜치 보호 필수 체크용. GitHub에서 실행해 본 적은 없음)
.worktreeinclude                 claude --worktree 때 복사할 로컬 설정 파일 목록
```

## 개발 흐름

```
/task pr-lens-p1-cli                   → 지금 시작할 수 있는 작업과 진행 중인 작업(원격 브랜치 기준)을 보고 작업을 고름
/task pr-lens-p1-cli 5.1               → 브랜치(feat/t2-p1-5-diff-parser)를 만들어 push하고, 작업 본문과 그 작업의 요구사항 인수 기준,
                                         design.md의 관련 절만 읽고 실패 테스트 먼저, 구현, tasks.md 체크박스 갱신
(응답이 끝날 때마다)                    → Stop hook이 바뀐 스택만 검증 (backend: spotlessApply test, web: npm run verify)
/pr-ready                              → 검증 증거, reviewer 리뷰(.claude/ 변경은 context-reviewer), PR 설명 초안
사람이 PR을 올리고 동료가 리뷰
```

- PR 하나는 `tasks.md`의 상위 작업 하나입니다. 작업 목록에 "PR 경계"가 표시된 작업은 그 경계대로 나눕니다. 크기 목표는 `src/main` 변경 400줄 이하입니다.
- 스펙에 없는 새 기능은 그 단계의 스펙 폴더에 요구사항, 설계, 작업을 먼저 더합니다. 작은 변경(한 문장으로 설명되는 diff)은 스펙 없이 바로 요청해도 됩니다.
- 스펙 문서는 `/spec <스펙>`으로 한 번에 하나씩 씁니다. P3는 P2가 끝나갈 때 `/spec pr-lens-p3-web`으로 요구사항 수정, 설계, 작업 목록 순서로 씁니다.
- 스펙에서 결정 대기(D-n, G-n)로 표시된 항목은 팀이 정합니다. Claude는 제안값으로 구현하고 임의로 확정하지 않습니다.

**스택 간 규칙**: web이 쓰는 API가 바뀌면 backend DTO와 `web/src/lib/api/types.ts`를 같은 PR에서 함께 바꿉니다. P3 전에는 web이 PR Lens API를 쓰지 않으므로 backend만 바꿉니다. 비즈니스 로직은 backend에만 두고, web은 조회와 표시만 합니다.

## Claude Code 설정

권한 규칙, 보호 hook, Stop hook의 동작과 한계는 [`docs/guide/claude-code-setup.md`](docs/guide/claude-code-setup.md)에 있습니다. 꼭 알아야 할 것만 적습니다.

- **`claude`는 저장소 루트에서 실행합니다.** 처음에 폴더를 신뢰해야 팀 allow 규칙이 적용됩니다.
- **막혀 있는 것**: `.env`, `secrets/`, 비밀 설정 파일의 읽기·수정(deny), gradle wrapper·lock 파일·`.git/` 수정(보호 hook).
- **승인을 묻는 것**: `build.gradle.kts`·`package.json` 수정(의존성), `.claude/` 아래 수정, `npm install`, `git push`.
- **응답이 끝날 때마다** Stop hook이 바뀐 스택의 포맷과 테스트를 실행합니다. 실패하면 Claude가 한 번 더 고칩니다. 문서만 바뀌면 건너뜁니다.
- **신뢰할 수 없는 브랜치를 체크아웃한 채로 Claude를 실행하지 않습니다.** Stop hook이 그 브랜치의 빌드 스크립트와 테스트를 승인 없이 실행합니다.

## 팀 운영 규칙 (요약)
자세한 규칙은 [플레이북](docs/product/PLAYBOOK.md)에 있습니다.

- `CLAUDE.md`, `.claude/` 변경은 `context` 라벨을 붙여 PR로 올리고, 그 주의 컨텍스트 담당이 승인합니다.
- Claude가 같은 실수를 반복하면 규칙을 추가하는 PR을, 규칙이 없어도 잘하는 것이 있으면 삭제하는 PR을 올립니다.
- 개인 설정은 `CLAUDE.local.md`와 `.claude/settings.local.json`에 둡니다 (gitignore됨).
