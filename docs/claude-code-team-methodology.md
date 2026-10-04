# 소규모 IT 팀을 위한 Claude Code 개발 방법론 가이드

> 대상: 2~5명 규모의 개발 팀 · 기준일: 2026-09-30 · 버전: v0.4 (초안, 3.5 TDD 추가)
> 핵심 주제: **팀 협업과 컨텍스트 공유**. 개발 프로세스, 품질·보안, 역할 분담·병렬 작업을 함께 다룹니다.
> 기능 설명은 Claude Code 공식 문서를 기준으로 했습니다(출처는 문서 끝에 정리). Claude Code는 업데이트가 잦으니 도입 전에 `/help`, `/context`, `/hooks`로 실제 동작을 확인하세요.

---

## 목차
1. [개요: 왜 팀 단위 방법론이 필요한가](#1-개요-왜-팀-단위-방법론이-필요한가)
2. [팀 컨텍스트 공유 (핵심 장)](#2-팀-컨텍스트-공유-핵심-장)
3. [개발 프로세스](#3-개발-프로세스)
4. [품질·보안](#4-품질보안)
5. [역할 분담·병렬 작업](#5-역할-분담병렬-작업)
6. [도입 로드맵](#6-도입-로드맵)
7. [부록 A: 공통 코어와 스택별 오버레이 구분표](#7-부록-a-공통-코어와-스택별-오버레이-구분표)
8. [부록 B: 출처](#8-부록-b-출처)

---

## 1. 개요: 왜 팀 단위 방법론이 필요한가

개인이 Claude Code를 쓰면 좋은 프롬프트와 습관이 **그 사람 머릿속과 로컬 설정에만** 남습니다. 팀으로 쓰면 다음 문제가 생깁니다.

| 문제 | 증상 | 이 가이드의 해법 |
|---|---|---|
| 컨텍스트가 사람마다 다름 | 같은 요청에 팀원마다 다른 스타일의 코드가 나옴 | 공유 CLAUDE.md, `.claude/rules/`, 공용 스킬 (2장) |
| 암묵지가 공유되지 않음 | "그건 이렇게 시켜야 잘 돼"가 구두로만 전달됨 | 워크플로를 스킬·서브에이전트로 **코드화** (2.4) |
| 검증 기준이 없음 | 그럴듯하지만 엣지 케이스가 빠진 코드가 머지됨 | 검증 가능한 스펙, 테스트, hooks, 리뷰 세션 분리 (3·4장) |
| 권한·보안 정책이 제각각 | 한 사람은 `.env`를 읽히고, 한 사람은 막음 | 프로젝트 settings에 deny 규칙 커밋 (4장) |
| 병렬 작업 충돌 | AI 세션 여러 개가 같은 파일을 동시에 수정 | worktree, 작업 단위 분할 (5장) |

**이 가이드의 기본 원칙**

1. **컨텍스트는 코드처럼 관리한다.** CLAUDE.md, 규칙, 스킬은 git에 커밋하고 PR로 리뷰한다.
2. **Claude가 스스로 검증할 수 있게 한다.** 테스트, 빌드, 린트처럼 통과·실패가 나오는 신호를 준다. 공식 문서가 꼽는 "단 하나만 도입한다면 이것"에 해당합니다.
3. **권고는 CLAUDE.md에, 강제는 hooks와 permissions에 둔다.** CLAUDE.md는 "지켜 주세요" 수준이고, 반드시 지켜야 하는 것은 결정론적 장치로 만든다.
4. **사람이 최종 책임을 진다.** AI가 쓴 코드도 작성자 이름으로 PR을 올리고, 그 사람이 설명할 수 있어야 한다.

---

## 2. 팀 컨텍스트 공유 (핵심 장)

### 2.1 컨텍스트 계층 한눈에 보기

Claude Code가 세션을 시작할 때 읽는 "지시문(메모리)"과 "설정"은 각각 계층이 있습니다. 팀 설계의 출발점은 **무엇을 어느 계층에 둘지** 정하는 것입니다.

**지시문(CLAUDE.md) 계층**

| 계층 | 위치 | 공유 범위 | 팀에서의 용도 |
|---|---|---|---|
| 조직 정책(Managed) | Windows `C:\Program Files\ClaudeCode\CLAUDE.md`, macOS `/Library/Application Support/ClaudeCode/CLAUDE.md`, Linux `/etc/claude-code/CLAUDE.md` | 조직 전체 (제외 불가) | 소규모 팀은 보통 불필요 |
| 프로젝트 | `./CLAUDE.md` 또는 `./.claude/CLAUDE.md` | **팀 전체 (git 커밋)** | 빌드·테스트 명령, 컨벤션, 아키텍처 결정 |
| 프로젝트 규칙 | `./.claude/rules/*.md` (하위 폴더 가능) | **팀 전체 (git 커밋)** | 주제별·경로별 규칙 (2.3) |
| 로컬 개인 | `./CLAUDE.local.md` | 나만 (`.gitignore`에 추가) | 내 샌드박스 URL, 개인 테스트 데이터 |
| 사용자 | `~/.claude/CLAUDE.md`, `~/.claude/rules/` | 나만 (모든 프로젝트) | 개인 취향 (응답 언어, 선호 도구) |

- 상위 디렉터리부터 현재 디렉터리까지의 CLAUDE.md는 **모두 이어 붙여서** 로드됩니다(덮어쓰지 않음). 같은 폴더에서는 `CLAUDE.local.md`가 `CLAUDE.md` 뒤에 붙습니다.
- 하위 디렉터리의 CLAUDE.md는 Claude가 그 폴더의 파일을 읽을 때 **필요할 때만** 로드됩니다. 모노레포에서 모듈별 지시문을 두기에 좋습니다.
- `@path/to/file`로 다른 파일을 가져올 수 있습니다(최대 4단계 중첩). 다만 가져온 파일도 시작할 때 전부 컨텍스트에 들어가므로 용량을 줄여 주지는 않습니다.
- 저장소에 이미 `AGENTS.md`가 있다면 CLAUDE.md에서 `@AGENTS.md`로 가져와 다른 AI 도구와 한 파일을 공유할 수 있습니다.

**설정(settings.json) 계층.** 위에 있을수록 우선합니다.

| 우선순위 | 위치 | 공유 범위 |
|---|---|---|
| 1 | Managed settings (조직) | 조직 |
| 2 | 명령행 인자 (`claude --settings ...`) | 이번 세션 |
| 3 | `.claude/settings.local.json` | 나만, 이 프로젝트 (Claude Code가 만들 때 자동으로 git 제외) |
| 4 | **`.claude/settings.json`** | **팀 전체 (git 커밋)** |
| 5 | `~/.claude/settings.json` | 나만, 모든 프로젝트 |

- `permissions.allow` 같은 **배열 값은 계층 간에 병합**됩니다. 팀 설정에 개인 허용 규칙을 더하는 방식으로 동작합니다.
- 프로젝트의 `allow` 규칙, `extraKnownMarketplaces`, 대부분의 `env`는 각 팀원이 **폴더를 신뢰(trust)한 뒤에** 적용됩니다. `deny`와 `ask`는 신뢰 전에도 바로 적용됩니다. 그래서 보안 규칙은 deny로 두는 것이 안전합니다.

### 2.2 CLAUDE.md 설계: 무엇을 넣고 무엇을 뺄까

공식 권장: **파일 하나당 200줄 미만.** 길어질수록 컨텍스트를 차지하고 지시를 따르는 비율이 떨어집니다. 줄마다 "이 줄을 지우면 Claude가 실수할까?"를 물어보고, 아니면 지웁니다.

| ✅ 넣을 것 | ❌ 뺄 것 |
|---|---|
| Claude가 추측할 수 없는 빌드·테스트·실행 명령 | 코드를 읽으면 알 수 있는 내용 |
| 기본값과 다른 코드 스타일 규칙 | 언어의 표준 컨벤션 |
| 테스트 방법, 선호하는 테스트 러너 | 상세 API 문서 (링크로 대체) |
| 저장소 규칙 (브랜치 이름, PR 컨벤션, 커밋 형식) | 자주 바뀌는 정보 |
| 프로젝트 고유의 아키텍처 결정 | 긴 설명, 튜토리얼 |
| 개발 환경의 특이사항 (필수 환경변수) | 파일별 설명 |
| 흔한 함정, 직관적이지 않은 동작 | "깨끗한 코드를 작성하라" 같은 당연한 말 |

**팀용 CLAUDE.md 템플릿 (예시)**

```markdown
# Project: <서비스명>
<한두 줄 요약: 무엇을 하는 서비스인지, 주요 스택>

## Commands
- 설치: `npm ci`
- 개발 서버: `npm run dev`
- 단일 테스트: `npm test -- <path>`  (전체 테스트보다 단일 테스트를 우선 실행)
- 타입 체크: `npm run typecheck`  (변경을 마치면 반드시 실행)

## Architecture
- 도메인 로직은 `src/domain/`, 외부 연동은 `src/infra/`에 둔다. domain은 infra를 import하지 않는다.
- 주요 결정 기록: @docs/adr/README.md

## Conventions
- 브랜치: `feat/<issue>-<slug>`, `fix/<issue>-<slug>`
- 커밋: Conventional Commits (`feat:`, `fix:`, `docs:` ...)
- PR 하나 = 이슈 하나. 400줄이 넘으면 나눈다.

## Gotchas
- 로컬 DB는 `docker compose up db`가 먼저 떠 있어야 테스트가 통과한다.
- IMPORTANT: `src/generated/`는 자동 생성 코드이므로 직접 수정하지 않는다.

## Compaction
- 컨텍스트를 압축할 때는 수정한 파일 목록과 테스트 명령을 반드시 보존한다.
```

**운영 규칙 (팀 합의 사항)**

- **담당자 지정**: CLAUDE.md와 `.claude/`의 관리자(1명, 순환 가능)를 정합니다. 변경은 PR로 올리고 최소 1명이 리뷰합니다.
- **"실수 → 규칙" 루프**: Claude가 반복해서 틀리는 것이 있으면 규칙을 추가하는 PR을 올립니다. 반대로 규칙이 없어도 Claude가 잘하는 것은 지웁니다.
- **정기 가지치기**: 월 1회 `/doctor`를 실행해(코드에서 도출할 수 있는 내용의 삭제 제안을 받음) 오래되거나 충돌하는 규칙을 정리합니다.
- **강조는 아껴 쓰기**: `IMPORTANT`는 정말 중요한 한두 줄에만 붙입니다. 모든 줄을 강조하면 아무것도 두드러지지 않습니다.
- **로드 확인**: 새로 합류한 팀원은 첫 세션에서 `/context`로 메모리 파일이 로드됐는지 확인합니다.

### 2.3 `.claude/rules/`로 규칙을 나누기

CLAUDE.md가 커지면 주제별 파일로 나눕니다. `paths` 프런트매터를 쓰면 **해당 경로의 파일을 다룰 때만** 규칙이 로드되어 컨텍스트를 아낄 수 있습니다.

```
.claude/rules/
├── testing.md          # paths 없음 → 항상 로드
├── frontend/
│   └── react.md        # paths: ["src/web/**/*.tsx"]
└── backend/
    └── api-design.md   # paths: ["src/api/**/*.ts"]
```

```markdown
---
paths:
  - "src/api/**/*.ts"
---
# API 규칙
- 모든 엔드포인트는 입력 검증을 포함한다
- 에러 응답은 표준 포맷 `{ code, message, details }`를 따른다
```

팀 적용 팁: 영역별 담당자(FE/BE)가 자기 영역의 rules 파일을 책임지면 소유권이 분명해집니다.

### 2.4 팀 지식을 코드화하기: 스킬, 서브에이전트, hooks, 플러그인

"이 작업은 이렇게 시켜야 잘 된다"는 노하우를 **실행 가능한 파일**로 만들어 공유하는 것이 팀 방법론의 핵심입니다.

| 도구 | 위치 | 언제 쓰나 | 팀 예시 |
|---|---|---|---|
| **스킬** (슬래시 커맨드 포함) | `.claude/skills/<name>/SKILL.md` (또는 기존 방식 `.claude/commands/<name>.md`) | 반복 워크플로, 가끔 필요한 도메인 지식 | `/spec`, `/fix-issue 123`, `/pr-ready`, API 컨벤션 |
| **서브에이전트** | `.claude/agents/<name>.md` | 별도 컨텍스트에서 하는 조사·리뷰 | `security-reviewer`, `test-writer` |
| **Hooks** | `.claude/settings.json`의 `hooks` | **예외 없이** 매번 실행돼야 하는 것 | 편집 후 자동 포맷, 보호 파일 수정 차단 |
| **MCP** | `.mcp.json` (프로젝트 루트) | 외부 시스템 연동 | 이슈 트래커, DB, Figma |
| **플러그인** | 팀 마켓플레이스 저장소 | **여러 저장소**에 같은 구성 배포 | 팀 공통 스킬·에이전트·hooks 묶음 |

> 참고: 커스텀 커맨드는 스킬로 통합되었습니다. `.claude/commands/deploy.md`와 `.claude/skills/deploy/SKILL.md`는 둘 다 `/deploy`가 되고 동작도 같습니다. 새로 만든다면 부가 파일, 자동 호출 제어 같은 기능이 있는 스킬 형식을 권장합니다.

**판단 기준**
- 매 세션 필요 → CLAUDE.md
- 가끔 필요한 지식이나 절차 → 스킬 (필요할 때만 로드되어 컨텍스트를 아낌)
- 파일을 많이 읽는 조사, 편향 없는 리뷰 → 서브에이전트
- 반드시 지켜야 함 → hooks / permissions
- 저장소가 여러 개 → 플러그인으로 묶기

**예시 1: 스펙 작성 스킬** `.claude/skills/spec/SKILL.md`

```markdown
---
name: spec
description: 새 기능의 스펙 문서를 인터뷰 방식으로 작성한다
disable-model-invocation: true
argument-hint: "[기능 한 줄 설명]"
---
다음 기능의 스펙을 작성한다: $ARGUMENTS

1. AskUserQuestion으로 나를 인터뷰한다. 기술 구현, UX, 엣지 케이스, 트레이드오프 가운데 내가 놓쳤을 만한 어려운 부분 위주로 묻는다.
2. 인터뷰가 끝나면 `docs/specs/<yyyy-mm-dd>-<slug>.md`에 @docs/specs/_template.md 형식으로 저장한다.
3. 스펙에는 관련 파일과 인터페이스, 범위 밖 항목(Out of scope), 끝에서 끝까지 검증하는 방법을 반드시 포함한다.
```

`disable-model-invocation: true`는 부작용이 있거나 사람이 시작해야 하는 워크플로에 붙입니다(Claude가 알아서 호출하지 않음).

**예시 2: 리뷰 서브에이전트** `.claude/agents/reviewer.md`

```markdown
---
name: reviewer
description: 현재 diff를 스펙과 팀 컨벤션 기준으로 리뷰한다. 구현이 끝난 뒤 사용.
tools: Read, Grep, Glob, Bash
---
너는 이 팀의 시니어 리뷰어다. 현재 브랜치의 diff(`git diff main...HEAD`)를 다음 기준으로 검토한다.
- 연결된 스펙의 요구사항이 모두 구현되었는가, 엣지 케이스에 테스트가 있는가
- 범위 밖 변경이 없는가
- .claude/rules/의 규칙 위반
정확성과 요구사항에 영향을 주는 문제만 보고한다. 스타일 취향은 "선택 사항"으로 따로 표시한다.
```

**예시 3: 팀 플러그인 배포 (저장소가 여러 개일 때)**
1. 팀 마켓플레이스 저장소를 만듭니다: `.claude-plugin/marketplace.json`, `plugins/<plugin>/` 구조. 검증은 `claude plugin validate .`으로 합니다.
2. 각 프로젝트의 `.claude/settings.json`에 마켓플레이스와 플러그인을 선언합니다.
   ```json
   {
     "extraKnownMarketplaces": {
       "our-team": { "source": { "source": "github", "repo": "our-org/claude-plugins" } }
     },
     "enabledPlugins": { "team-conventions@our-team": true }
   }
   ```
3. 팀원이 폴더를 신뢰하면 마켓플레이스가 등록됩니다. 수동으로 추가할 때는 `claude plugin marketplace add our-org/claude-plugins`를 실행합니다.

주의할 점 (공식 문서 확인):
- `extraKnownMarketplaces`의 키(`our-team`)는 마켓플레이스 저장소의 `marketplace.json`에 적힌 `name`과 같아야 합니다. `enabledPlugins`의 형식은 `플러그인이름@마켓플레이스이름`입니다.
- 마켓플레이스 안에 상대경로로 들어 있는 플러그인은 신뢰 후 자동으로 로드됩니다. 반면 외부 저장소를 가리키는 플러그인은 팀원마다 한 번 `claude plugin install <name>@<marketplace> --scope project`를 실행해야 합니다.
- 비공개 저장소라면 팀원 각자에게 읽기 권한이 필요합니다.

> 저장소가 하나뿐인 2~3명 팀이라면 플러그인 없이 `.claude/` 폴더를 커밋하는 것만으로 충분합니다. 플러그인은 저장소가 여러 개가 되었을 때 도입하세요.

### 2.5 사람의 문서를 AI 컨텍스트로 연결하기

AI에게 줄 컨텍스트와 사람이 읽을 문서를 **따로 쓰지 말고, 하나를 두 용도로 씁니다.**

```
docs/
├── adr/                 # 아키텍처 결정 기록 (Architecture Decision Record)
│   ├── README.md        # 결정 목록 한 줄 요약 → CLAUDE.md에서 @import
│   └── 0003-use-postgres.md
├── specs/               # 기능 스펙 (3장의 워크플로에서 생성)
│   ├── _template.md
│   └── 2026-09-27-oauth-login.md
└── conventions.md       # 긴 컨벤션 설명 → 필요한 부분만 rules로 요약
```

- **ADR**: CLAUDE.md에는 ADR 목록(한 줄 요약)만 import합니다. 상세 내용은 Claude가 필요할 때 읽도록 경로만 알려 줍니다.
- **스펙**: 스펙 파일 경로를 이슈와 PR에 링크합니다. 구현 세션은 "`@docs/specs/xxx.md` 구현해"로 시작합니다.
- **이슈 트래커**: `gh` CLI나 MCP로 연결해 "이슈 #123 보고 구현해"가 되게 합니다. 공식 문서는 CLI 도구가 컨텍스트 면에서 가장 효율적이라고 권장합니다.
- **세션 핸드오프**: 작업을 다른 팀원에게 넘길 때는 대화 기록이 아니라 **스펙과 PR 설명**으로 넘깁니다. 세션은 로컬에 저장되어 공유되지 않습니다.

### 2.6 개인 영역과 팀 영역의 경계

| 팀이 커밋 (공유) | 개인 (커밋 안 함) |
|---|---|
| `CLAUDE.md`, `.claude/rules/` | `CLAUDE.local.md` |
| `.claude/settings.json` (권한 deny/allow, hooks, 플러그인) | `.claude/settings.local.json` (개인 허용 규칙, 개인 env) |
| `.claude/skills/`, `.claude/agents/`, `.claude/hooks/` | `~/.claude/` 아래 전부 (개인 스킬, 응답 언어 등) |
| `.mcp.json` (비밀값은 `${ENV_VAR}`로 참조) | API 키, 토큰 등 실제 비밀값 |

`.gitignore` 권장 항목:
```
CLAUDE.local.md
.claude/settings.local.json
.claude/worktrees/
```

---

## 3. 개발 프로세스

### 3.1 기본 흐름: 스펙 → 계획 → 구현 → 검증 → 리뷰

공식 권장 흐름(탐색 → 계획 → 구현 → 커밋)에 팀 협업 단계를 더한 흐름입니다.

```
[이슈] → ① 스펙(사람+AI 인터뷰) → ② 계획(Plan mode) → ③ 구현(+테스트) → ④ 자체 검증 → ⑤ AI 리뷰 → ⑥ 사람 리뷰 → [머지]
          docs/specs/ 커밋          필요 시 계획 공유       새 세션             hooks/테스트    서브에이전트        PR
```

| 단계 | 누가 | 어떻게 | 산출물 |
|---|---|---|---|
| ① 스펙 | 담당자 + Claude | `/spec <기능>`: Claude가 인터뷰하고 스펙 작성 | `docs/specs/*.md` (PR로 공유 가능) |
| ② 계획 | Claude (Plan mode) | `Shift+Tab`으로 plan mode 진입 → 코드 탐색 → 계획 작성. `Ctrl+G`로 계획을 직접 편집 | 계획 (큰 작업은 스펙에 덧붙임) |
| ③ 구현 | Claude | **새 세션**에서 스펙을 참조해 구현. 테스트 작성과 실행 포함 | 코드와 테스트 |
| ④ 검증 | Claude + hooks | 테스트, 타입 체크, 린트 통과. Stop hook이나 `/goal`로 강제 가능 | 통과 증거 (명령과 출력) |
| ⑤ AI 리뷰 | 서브에이전트 | 새 컨텍스트에서 스펙 대비 diff 검토 (`/code-review` 또는 팀의 reviewer 에이전트) | 지적 사항 |
| ⑥ 사람 리뷰 | 동료 | PR 리뷰. 스펙 링크와 검증 증거가 PR에 첨부됨 | 승인 후 머지 |

**작업 크기별 생략 규칙.** 모든 작업에 전 과정이 필요하지는 않습니다.
- **diff를 한 문장으로 설명할 수 있음** (오타 수정, 로그 추가): ③ → ④ → ⑥만 합니다.
- **여러 파일에 걸치거나 접근법이 불확실함**: ② 계획부터 시작합니다.
- **새 기능, 여러 사람이 관여함**: ①부터 전 과정을 거칩니다.

### 3.2 스펙 템플릿 (`docs/specs/_template.md`)

좋은 스펙은 그것만 보고도 이해되는(self-contained) 스펙입니다. 관련 파일과 인터페이스를 명시하고, 범위 밖 항목을 적고, **끝에서 끝까지 검증하는 단계**로 마무리합니다.

```markdown
# <기능명>
- 이슈: #123 · 담당: @name · 상태: draft | approved | done

## 배경 / 목표
## 요구사항 (수락 기준)
- [ ] 사용자는 ... 할 수 있다
## 범위 밖 (Out of scope)
## 설계
- 변경 대상 파일/모듈:
- 인터페이스 (API, 타입):
## 엣지 케이스
## 검증 방법
- 자동: `npm test -- src/auth` 통과, ...
- 수동(E2E): 1) ... 2) ... 결과 ...
```

### 3.3 검증 가능한 작업 지시 쓰기

| 나쁜 예 | 좋은 예 |
|---|---|
| "로그인 버그 고쳐" | "세션 만료 후 로그인이 실패한다는 제보. `src/auth/`의 토큰 갱신 흐름을 확인하고, 재현하는 실패 테스트를 먼저 작성한 다음 고쳐" |
| "테스트 추가해" | "`foo.ts`에 로그아웃 상태 엣지 케이스 테스트 추가. mock 쓰지 말 것" |
| "빌드 깨졌어" | "[에러 붙여넣기] 원인을 고치고 빌드 성공을 확인해. 에러를 억누르지 말 것" |

- **TDD와 잘 맞습니다**: "실패하는 테스트 작성 → 실패 확인 → 구현 → 통과"를 지시합니다. 테스트가 곧 검증 수단이 됩니다. 자세한 방법은 [3.5](#35-tdd-테스트를-먼저-쓰게-하기)에 있습니다.
- **증거를 요구합니다**: "성공했다"는 말 대신 실행한 명령과 출력을 보여 달라고 합니다. 리뷰어도 PR에서 이 증거를 봅니다.

### 3.4 세션과 컨텍스트 관리 습관

- **작업 하나 = 세션 하나.** 관련 없는 작업 사이에는 `/clear`를 실행합니다.
- **같은 문제로 두 번 넘게 교정했다면** `/clear`하고, 배운 점을 반영한 더 구체적인 프롬프트로 다시 시작합니다.
- **조사는 서브에이전트에 맡깁니다**: "서브에이전트로 X를 조사해". 파일을 대량으로 읽어도 메인 컨텍스트가 깨끗하게 유지됩니다.
- **세션에 이름을 붙입니다**: `/rename oauth-migration`, 이어서 할 때는 `claude --resume`.
- **체크포인트는 git을 대체하지 않습니다**: `Esc Esc` 또는 `/rewind`로 되돌릴 수 있지만, Bash로 바꾼 내용은 추적되지 않습니다. 의미 있는 단위마다 커밋하세요.

### 3.5 TDD: 테스트를 먼저 쓰게 하기

TDD(Test-Driven Development, 테스트 주도 개발)는 **코드보다 테스트를 먼저 쓰는** 방식입니다. 짧은 사이클을 반복합니다.

| 단계 | 하는 일 | 확인할 것 |
|---|---|---|
| **Red** | 아직 없는 동작의 테스트를 작성 | 테스트가 **실제로 실패**하는가, 실패 이유가 "기능이 없어서"인가 (컴파일 오류나 오타가 아닌가) |
| **Green** | 테스트를 통과시키는 **최소한의** 코드 작성 | 새 테스트와 기존 테스트가 모두 통과하는가 |
| **Refactor** | 통과 상태를 유지하며 이름·구조 정리 | 테스트를 고치지 않고도 계속 통과하는가 |

Red에서 실패를 먼저 확인하는 것이 핵심입니다. 처음부터 통과하는 테스트는 아무것도 검증하지 않고 있을 수 있습니다.

**왜 Claude Code와 잘 맞나**
- **테스트가 곧 명세입니다.** 기대 동작을 테스트로 먼저 고정하면 Claude가 목표를 오해할 여지가 줄어듭니다. 1장의 원칙 2("Claude가 스스로 검증할 수 있게 한다")를 가장 직접적으로 실현하는 방법입니다.
- **완료 기준이 분명해집니다.** "다 됐어요"라는 말 대신 테스트 결과로 판단합니다. Stop hook이 매 응답 끝에 테스트를 돌리면(4.2) Green 상태가 강제됩니다.
- **버그 수정에 특히 효과적입니다.** 재현 테스트가 먼저 실패해야 "버그를 제대로 이해했다"는 증거가 되고, 같은 버그가 다시 생기는 것도 막습니다.

**지시 예시**

```text
# 새 기능
docs/specs/rate-limit.md의 수락 기준마다 테스트를 먼저 작성해.
테스트를 실행해 모두 실패하는 것을 확인하고, 실패 출력을 보여 줘. 구현은 아직 하지 마.

# (실패 확인 후) 구현
이제 이 테스트들을 통과시키는 최소한의 코드를 작성해. 테스트 파일은 수정하지 마.

# 버그 수정
세션 만료 후 로그인이 실패한다는 제보가 있어. 이 버그를 재현하는 실패 테스트를 먼저 작성하고
실패를 확인한 다음 고쳐. 마지막에 전체 테스트 결과를 보여 줘.
```

**팀에서 쓸 때의 규칙**
- **"테스트 파일은 수정하지 마"를 구현 단계 지시에 넣습니다.** AI는 통과를 목표로 삼기 때문에 assertion을 지우거나 기대값을 바꾸거나 skip하는 방향으로 "해결"할 수 있습니다. CLAUDE.md에 금지 규칙을 두고(2.2), 리뷰어는 테스트 diff를 먼저 봅니다(4.1의 "테스트 약화").
- **테스트와 구현의 작성자를 나눌 수 있습니다.** 같은 세션이 둘 다 쓰면 같은 착각이 테스트와 코드에 함께 들어갑니다. 중요한 로직은 한 세션이 테스트를 쓰고, 새 세션이 그 테스트를 통과하는 코드를 씁니다(5.3). 사람이 테스트를 검토한 뒤 구현을 맡기면 더 확실합니다.
- **Red 단계를 커밋으로 남기면 리뷰가 쉬워집니다.** "테스트 추가(실패)" 커밋과 "구현" 커밋을 나누면, 리뷰어가 기대 동작과 구현을 따로 확인할 수 있습니다.
- **mock은 외부 경계에만 씁니다.** 모든 것을 mock하면 테스트가 구현을 따라 쓰이게 되어 명세 역할을 못 합니다.
- **스킬로 절차를 고정합니다.** "실패하는 테스트 먼저 → 실패 확인 → 구현 → 전체 테스트" 순서를 스킬에 적어 두면 팀원마다 지시 방법이 달라지지 않습니다. 스타터의 `/new-endpoint`, `/new-page`가 이 순서를 따릅니다.

**어디에 쓰고 어디에 덜 쓸까**

| 효과가 큼 | 효과가 작음 |
|---|---|
| 도메인 로직, 계산, 파싱·변환 | 탐색 중인 프로토타입, 기술 검증(spike) |
| 버그 수정 (재현 테스트) | UI 배치, 스타일 |
| 입력과 출력이 분명한 API | 외부 시스템과의 연결 자체를 확인하는 작업 |
| 리팩터링 전 기존 동작 고정 | 요구사항이 아직 정해지지 않은 기능 |

효과가 작은 쪽은 구현 뒤에 테스트를 붙이거나, 스펙의 "검증 방법"(3.2)에 적은 수동 확인으로 대신합니다.

---

## 4. 품질·보안

### 4.1 AI 생성 코드 리뷰 기준 (팀 PR 체크리스트)

PR 템플릿에 넣어 둘 항목:

```markdown
## AI 사용
- [ ] 연결 스펙: docs/specs/...
- [ ] 검증 증거 첨부 (테스트/빌드 명령과 결과)
- [ ] AI 리뷰(서브에이전트 또는 /code-review) 수행, 반영/보류 사유 기록
- [ ] 작성자는 모든 변경 줄을 설명할 수 있음
- [ ] 스펙 범위 밖 변경 없음 (있다면 사유)
```

사람 리뷰어가 특히 볼 것:
- **그럴듯하지만 틀린 코드**: 엣지 케이스 누락, 존재하지 않는 API 호출, 조용히 삼킨 에러
- **과잉 설계**: 불필요한 추상화, 방어 코드, 일어날 수 없는 경우를 검사하는 테스트. AI 리뷰 결과를 모두 반영하다 보면 생기기 쉽습니다.
- **테스트 약화**: 테스트를 통과시키려고 assertion을 지우거나 skip한 것
- **의존성 추가**: 새 패키지는 사람이 명시적으로 승인합니다.

### 4.2 Hooks로 강제할 검사

hooks는 Claude Code 생명주기의 특정 시점에 스크립트를 **결정론적으로** 실행합니다. 입력은 stdin JSON으로 받습니다.

| 이벤트 | 시점 | 팀 활용 |
|---|---|---|
| `PreToolUse` | 도구 실행 전 (**차단 가능**) | 보호 파일 수정 차단, 위험 명령 차단 |
| `PostToolUse` | 도구 실행 성공 후 | 편집한 파일 자동 포맷·린트 |
| `Stop` | Claude가 응답을 마쳤을 때 | 테스트·타입 체크가 통과해야 종료 가능하게 함 |
| `SessionStart` | 세션 시작·재개 시 | 현재 브랜치와 이슈 정보 주입 |
| `UserPromptSubmit` | 프롬프트 제출 시 | 프롬프트 검사, 컨텍스트 추가 |

**종료 코드**: `0` = 통과, **`2` = 차단**(PreToolUse·Stop 등 차단 가능한 이벤트에서는 stderr 메시지가 Claude에게 피드백으로 전달되어 방향을 바꿈. SessionStart처럼 차단할 수 없는 이벤트에서는 사용자에게 표시만 됨), 그 밖의 값 = 에러로 기록하고 계속 진행.

**예시: 편집 후 자동 포맷과 보호 파일 차단** (`.claude/settings.json`)

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit|Write|NotebookEdit",
        "hooks": [{ "type": "command", "command": "node",
                    "args": ["${CLAUDE_PROJECT_DIR}/.claude/hooks/protect-files.mjs"] }]
      }
    ],
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [{ "type": "command", "command": "node",
                    "args": ["${CLAUDE_PROJECT_DIR}/.claude/hooks/format.mjs"] }]
      }
    ]
  }
}
```

```js
// .claude/hooks/protect-files.mjs: Windows/macOS/Linux 공통 (Node만 있으면 동작)
let raw = '';
for await (const chunk of process.stdin) raw += chunk;
const input = JSON.parse(raw).tool_input ?? {};
const original = input.file_path ?? input.notebook_path ?? '';   // NotebookEdit은 notebook_path
// Windows·macOS는 대소문자를 구분하지 않으므로 소문자로, 상대경로도 잡도록 맨 앞에 '/'
const file = '/' + original.replaceAll('\\', '/').toLowerCase().replace(/^\/+/, '');
const name = file.slice(file.lastIndexOf('/') + 1);
const PROTECTED = ['/.git/', '/secrets/', '/src/generated/'];
const hit = name === '.env' || name.startsWith('.env.') || name === 'package-lock.json'
  ? name
  : PROTECTED.find((p) => file.includes(p));
if (hit) {
  console.error(`Blocked: ${file} matches protected pattern '${hit}'`);
  process.exit(2);
}
```

> **크로스플랫폼 주의**: 공식 예제는 bash와 `jq`를 사용합니다. Windows에서 shell 형식의 hook은 Git Bash로 실행되고, Git Bash가 없으면 PowerShell로 실행됩니다. 팀원의 OS가 섞여 있다면 **Node나 Python 스크립트로 작성**하는 편이 안전합니다(부록 A).
>
> **exec form 권장**: `"command": "node"`, `"args": ["${CLAUDE_PROJECT_DIR}/.claude/hooks/x.mjs"]`처럼 `args`를 쓰면 셸을 거치지 않고 실행되어 OS 간 따옴표·변수 문법 차이가 사라집니다.
>
> **Java 팀이라면**: Node 대신 JDK 단일 파일 실행(`"command": "java"`, `"args": ["-Dfile.encoding=UTF-8", ".../Hook.java"]`)을 쓰면 추가 설치가 필요 없습니다. 한 번 실행에 약 0.5초가 걸립니다(실측). 실제 구현은 `claude-team-starter/.claude/hooks/`를 참고하세요.
>
> **Windows 한글 환경에서 hook을 만들 때 (실측)**
> - JDK 17에서는 `-Dfile.encoding=UTF-8`이 없으면 소스 안의 한글이 깨집니다. JDK 18 이상에서는 이 옵션이 표준 출력에 적용되지 않으므로, 코드에서 `new PrintStream(System.err, true, UTF_8)`처럼 명시하는 것이 안전합니다.
> - Gradle, cmd 같은 외부 명령의 출력은 OS 인코딩(MS949)으로 나옵니다. hook이 이 출력을 Claude에게 넘길 때는 MS949로 읽어야 합니다.
> - git 경로 출력은 `-c core.quotePath=false`와 `-z`를 붙여 받아야 합니다. 그렇지 않으면 한글 파일명이 `\353\251\224...`처럼 이스케이프됩니다.
> - `NoDefaultCurrentDirectoryInExePath`가 설정된 환경에서는 `cmd /c gradlew.bat`이 현재 폴더를 찾지 못합니다. `.\gradlew.bat`처럼 경로를 명시하세요.
> - 프로젝트 경로에 `&`가 있으면 `gradlew.bat` 자체가 동작하지 않습니다.
>
> **알아 둘 것**: Claude는 `Edit/Write` 말고 Bash 명령으로도 파일을 바꿀 수 있습니다. 감사처럼 모든 변경을 잡아야 한다면 `Stop` hook에서 `git status --porcelain`으로 작업 트리를 검사하는 방식을 함께 쓰세요.
>
> **Stop hook으로 테스트를 강제할 때 주의할 점**
> - **커밋된 변경도 잡아야 합니다.** Claude가 수정하고 곧바로 커밋하면 작업 트리가 깨끗해집니다. HEAD 변경도 검증 조건에 넣으세요.
> - **사용자의 미완성 코드도 검증 대상입니다.** 테스트가 깨진 상태로 두고 질문만 해도 막히므로 팀에 미리 알려 두세요.
> - **"실행 실패"와 "테스트 실패"를 구분하세요.** 환경 문제로 빌드 도구가 시작조차 못 했는데 exit 2로 막으면, Claude가 멀쩡한 코드를 "고치려" 듭니다.
> - **재실패 시에는 더 막지 마세요.** 입력의 `stop_hook_active`가 `true`이면 이미 한 번 막은 상태이므로, 사용자에게 알리고 끝내 무한 루프를 막습니다.

### 4.3 권한과 비밀정보 관리

**팀 공통 `.claude/settings.json` 권한 예시**

```json
{
  "permissions": {
    "allow": [
      "Bash(npm run lint)",
      "Bash(npm run test *)",
      "Bash(npm run typecheck)",
      "Bash(git status)",
      "Bash(git diff *)"
    ],
    "ask": [
      "Bash(git push *)"
    ],
    "deny": [
      "Read(/**/.env)",
      "Read(/**/.env.*)",
      "Read(/secrets/**)",
      "Edit(/**/.env)",
      "Edit(/**/.env.*)",
      "Edit(/secrets/**)"
    ]
  }
}
```

- **deny는 팀 설정에, 편의용 allow는 개인 설정에.** 보안 규칙이 가장 먼저, 확실하게 적용되게 합니다.
- **경로 기준을 정확히**

  | 표기 | 기준 |
  |---|---|
  | `/path` | 설정 파일의 위치. 프로젝트 설정이면 프로젝트 루트 |
  | `./path` 또는 `path` | Claude를 실행한 현재 디렉터리 |
  | `//path` | 파일시스템 루트 |
  | `~/path` | 홈 디렉터리 |

  팀 설정에는 `/`로 시작하는 패턴을 쓰세요. `./`를 쓰면 하위 폴더에서 Claude를 실행했을 때 규칙이 다른 위치를 가리킵니다.
- **deny의 적용 범위** (공식 문서)
  - `Read` deny는 같은 경로의 Edit/Write와 새 파일 생성도 막습니다.
  - Claude Code가 알아보는 Bash 파일 명령(`cat`, `sed`, `tee`, `> file` 리다이렉션)에도 적용됩니다.
  - **막지 못하는 것**: NotebookEdit(그래서 `Edit` deny를 함께 둡니다), 파일 이름을 드러내지 않는 명령(`grep -r`), 스크립트가 간접적으로 여는 파일. 이런 경우까지 막으려면 `/sandbox`를 쓰세요.
- **`Bash(git push *)`처럼 끝이 ` *`인 규칙은 인자 없는 `git push`도 잡습니다.** 반면 `git -C . push`처럼 다르게 쓴 명령은 잡지 못합니다.
- **Windows에서는 PowerShell 규칙을 따로** 둡니다. 예: `PowerShell(.\gradlew.bat test *)`, `PowerShell(git push *)`. JSON 문자열 안에서는 백슬래시를 `\\`로 두 번 씁니다. Bash 규칙은 PowerShell 도구에 적용되지 않습니다.
- **실측 결과** (Claude Code 2.1.283, 스타터 레포에서 `claude -p`로 확인)
  - 폴더를 신뢰하기 전에는 프로젝트의 `allow` 규칙이 무시되고("Ignoring 9 permissions.allow entries ... not been trusted"), `deny`는 바로 적용됩니다.
  - `Read` deny 규칙이 있는 `.env`에 대한 Write가 hook보다 먼저 거부되었습니다. 공식 문서의 설명과 같습니다. 비밀 파일 보호는 deny 규칙이 1차, PreToolUse hook이 2차 방어선입니다.
  - (2.1.285, 폴더 신뢰 후) `cd web && npm run verify`처럼 `cd`가 앞에 붙은 명령도 `Bash(npm run verify *)` allow 규칙에 맞아 확인 없이 실행되었습니다.
  - (2.1.285) `--permission-mode acceptEdits`로 편집을 자동 승인해도 `Edit(/**/package.json)` 같은 **ask 규칙이 우선**해 확인을 요구했습니다. 의존성 파일처럼 사람이 봐야 하는 파일은 acceptEdits 모드에서도 ask로 지킬 수 있습니다.
- **권한 모드 운영 기준 (팀 합의)**

  | 상황 | 권장 모드 |
  |---|---|
  | 낯선 코드 탐색, 설계 | Plan mode (읽기만 함) |
  | 일상 개발 | auto mode (위험해 보이는 작업은 분류기가 차단) 또는 Manual과 allowlist 조합 |
  | 격리된 컨테이너·샌드박스 안의 일괄 작업 | `claude -p` + `--allowedTools`로 범위 제한 |
  | 로컬에서 `bypassPermissions` | **금지** |

- **샌드박스**: `/sandbox`로 OS 수준에서 파일시스템과 네트워크를 격리할 수 있습니다.
- **MCP 비밀값**: `.mcp.json`에는 `${GITHUB_TOKEN}`처럼 환경변수 참조만 둡니다. 프로젝트 MCP 서버는 팀원마다 처음 쓸 때 승인 절차를 거칩니다.
- **외부 전송 주의**: 고객 데이터나 운영 DB를 MCP로 연결할 때는 읽기 전용 계정을 쓰고 최소 권한 원칙을 지킵니다.

### 4.4 CI 연동 (선택)

- **GitHub Actions**: Claude Code 안에서 `/install-github-app`을 실행하면 GitHub App, 시크릿, 워크플로 설정이 한 번에 됩니다. 이 명령은 github.com 저장소에서만 동작하고, `gh` CLI 인증이 필요합니다.
  - PR이나 이슈에서 `@claude`를 멘션하면 작업을 맡길 수 있습니다(`anthropics/claude-code-action@v1`).
  - 리뷰 워크플로를 선택하면 PR마다 자동 리뷰가 달립니다.
- **Code Review**: 워크플로 파일을 관리하지 않고 모든 PR에 자동 리뷰를 받고 싶다면 별도 기능인 [Code Review](https://code.claude.com/docs/en/code-review)를 쓸 수 있습니다.
- **Headless**: `claude -p "..." --output-format json`으로 CI 스크립트에 넣을 수 있습니다. 무인으로 실행할 때는 `--allowedTools`와 `--max-turns`로 범위와 비용을 제한합니다.
- 소규모 팀 권장 순서: 로컬 리뷰 습관이 자리 잡은 **다음에** CI에 자동 리뷰를 붙입니다. 너무 일찍 붙이면 노이즈 코멘트에 익숙해져 리뷰를 건성으로 보게 됩니다.

---

## 5. 역할 분담·병렬 작업

### 5.1 사람과 AI의 책임 경계

| 사람이 책임짐 (위임 불가) | AI에 위임 | 함께 함 |
|---|---|---|
| 무엇을 만들지 (우선순위, 범위) | 코드 탐색, 영향 범위 조사 | 스펙 작성 (AI가 인터뷰, 사람이 결정) |
| 아키텍처 결정 (ADR 승인) | 구현, 테스트 작성, 리팩터링 | 설계 대안 비교 |
| 보안·개인정보 판단 | 1차 리뷰, 린트·포맷 수정 | 디버깅 (AI가 가설 제시, 사람이 확인) |
| 머지 승인, 배포 결정 | 문서 초안, 마이그레이션 반복 작업 | 온보딩 (AI에게 코드베이스 질문) |

**2~5명 팀의 역할 예시** (겸직 전제)
- **컨텍스트 담당**: CLAUDE.md, rules, skills의 PR 리뷰와 월간 가지치기 담당 (순환 가능)
- **기능 리드**: 이슈(또는 그 주의 마일스톤) 하나를 스펙부터 PR까지 책임짐. AI 세션 여러 개를 지휘하는 역할
- **리뷰어**: 사람 리뷰를 맡음. AI 리뷰 결과 중 무엇을 반영할지 판단

### 5.2 병렬 작업: worktree 활용

한 사람이 여러 Claude 세션을 동시에 돌리거나 팀원들의 작업이 겹칠 때, **git worktree로 작업 디렉터리를 분리**하면 서로의 수정이 충돌하지 않습니다.

```bash
claude --worktree feat-oauth      # .claude/worktrees/feat-oauth 에 격리된 체크아웃과 브랜치 생성
claude --worktree fix-timeout     # 다른 터미널에서 병렬 작업
```

- worktree에는 gitignore된 파일(`.env` 등)이 없습니다. 필요한 파일은 프로젝트 루트의 `.worktreeinclude`에 적어 두면 새 worktree로 복사됩니다.
  ```
  # .worktreeinclude
  .env
  src/main/resources/application-local.yml
  ```
- `CLAUDE.local.md`도 worktree마다 따로 있습니다. 개인 지시를 모든 worktree에서 쓰려면 홈 디렉터리의 파일을 import하세요.
- 서브에이전트에 `isolation: worktree`를 지정하면 각자 임시 worktree에서 작업합니다.
- 대량 마이그레이션은 `/batch <지시>`를 씁니다. 작업을 5~30개 서브에이전트로 나누고, 각각 독립된 worktree에서 실행합니다.

### 5.3 작성자와 리뷰어 세션 분리

같은 세션에서 자기가 쓴 코드를 리뷰하면 편향이 생깁니다. **새 컨텍스트에서 리뷰**하는 것이 공식 권장 패턴입니다.

| 세션 A (작성) | 세션 B (리뷰, 새 컨텍스트) |
|---|---|
| "`@docs/specs/rate-limit.md` 구현해" | |
| | "`@src/middleware/rateLimiter.ts`를 리뷰해. 엣지 케이스, 경쟁 조건, 기존 미들웨어 패턴과의 일관성 위주로" |
| "리뷰 피드백: [B의 결과]. 반영해" | |

테스트에도 같은 방식을 쓸 수 있습니다. 한 세션이 테스트를 쓰고, 다른 세션이 그 테스트를 통과하는 코드를 씁니다.

### 5.4 브랜치와 PR 전략

- **짧은 브랜치, 작은 PR**: AI는 코드를 빨리 만들어서 PR이 커지기 쉽습니다. **PR 크기 상한**(예: 400줄)을 팀 규칙으로 두고 CLAUDE.md에도 적습니다.
- **스펙 단위로 브랜치**: 스펙 하나가 브랜치 하나이자 PR 하나입니다. 스펙이 크면 스펙 단계에서 나눕니다.
- **충돌 예방**: 같은 모듈을 동시에 건드리는 작업은 스프린트 계획 단계에서 순서를 정합니다. worktree는 로컬 충돌만 막아 줄 뿐, 머지 충돌까지 막지는 못합니다.
- **커밋 메시지**: Claude가 커밋을 작성하게 하되 팀 형식(Conventional Commits 등)을 CLAUDE.md에 명시합니다.

### 5.5 역할 순환 운영 (겸직 모델)

5.1의 세 역할을 순환할 때, 역할을 **본업으로 나누지 말고 "모자"로 겹쳐 쓰는** 방식을 권장합니다.

| 방식 | 구조 | 비용·주의점 |
|---|---|---|
| 전담형 | 이번 주 A는 컨텍스트만, B는 구현만, C는 리뷰만 | 구현하는 사람이 1명뿐이라 처리량이 낮고, 리뷰할 PR도 적어 리뷰어 경험이 얕아짐 |
| **겸직형 (권장)** | 전원이 매주 자기 작업을 구현하고, 역할은 그 주에 추가로 맡는 책임 | 역할 업무 시간(투입 시간의 20~30%, 전일 근무라면 하루 1~2시간)을 따로 확보해야 함 |

- **역할과 권한을 연결합니다.** 역할이 이름뿐이면 흐지부지됩니다. 리뷰어는 일반 PR의 기본 승인자, 컨텍스트 담당은 `.claude/`·`CLAUDE.md` 변경의 필수 승인자로 정합니다. 자기 PR은 다른 역할이 승인합니다.
- **순환 단위는 1~2주**가 적당합니다. 더 짧으면 역할을 익히기 전에 바뀌고, 더 길면 모두가 모든 역할을 경험하기 어렵습니다.
- **인수인계는 문서로 합니다.** 역할이 바뀔 때마다 주간 회고 노트(결과, 지표, 역할별 인수인계 항목)를 남기고, 다음 담당자는 이 노트만 읽고 시작합니다. 대화 세션은 공유되지 않으므로(2.5) 문서가 유일한 연결 수단입니다.
- **작업을 트랙으로 나눕니다.** 같은 주에 여러 명이 구현하므로 트랙 사이 인터페이스를 먼저 정의해 머지한 뒤 병렬로 진행합니다(5.4의 충돌 예방).

> 적용 사례: 3명이 5주 동안 이 방식으로 진행하는 연구회 프로젝트의 [플레이북](study-project/PLAYBOOK.md), [PRD](study-project/PRD.md), [ROADMAP](study-project/ROADMAP.md)

---

## 6. 도입 로드맵

소규모 팀 기준으로, 한 번에 다 도입하지 말고 **단계적으로** 적용합니다.

**1주 차: 기반 만들기**
- [ ] `/init`으로 CLAUDE.md 초안을 만들고, 팀이 함께 200줄 미만으로 다듬어 커밋
- [ ] `.claude/settings.json`에 deny 규칙(비밀 파일) 커밋
- [ ] `.gitignore`에 `CLAUDE.local.md`, `.claude/settings.local.json`, `.claude/worktrees/` 추가
- [ ] 전원이 `/context`로 로드 여부 확인
- [ ] 팀 규칙 합의: "AI 코드도 작성자 책임", PR에 검증 증거 첨부

**2주 차: 프로세스 적용**
- [ ] `docs/specs/_template.md`와 `/spec` 스킬 추가
- [ ] 다음 기능 1~2개를 스펙 → 계획 → 구현 → 리뷰 흐름으로 진행해 보기
- [ ] PR 템플릿에 "AI 사용" 체크리스트 추가

**3주 차: 자동화와 품질**
- [ ] PostToolUse 포맷 hook과 PreToolUse 보호 파일 hook 추가 (크로스플랫폼 스크립트로)
- [ ] `reviewer` 서브에이전트 추가, PR 전에 실행하는 것을 습관화
- [ ] 반복해서 나온 실수를 rules와 skills에 반영

**4주 차 이후: 확장과 회고**
- [ ] 회고: 무엇이 효과 있었나, CLAUDE.md 가지치기
- [ ] (필요하면) worktree 기반 병렬 작업, CI 자동 리뷰, MCP 연동
- [ ] (저장소가 여러 개라면) 팀 플러그인 마켓플레이스로 공통 구성 배포

**성과 측정 지표 (가볍게)**
- PR 리드타임 (생성 → 머지)
- PR 리뷰 반려율, 머지 후 버그 수
- "같은 지적의 반복" 횟수 (rules로 흡수되고 있는가)
- 팀원 주관 만족도 (월 1회, 5점 척도)

> 이 로드맵을 5주짜리 팀 프로젝트에 적용한 계획은 [연구회 ROADMAP](study-project/ROADMAP.md)을 참고하세요. 1주차에 기반을 만들고, 2~4주차에 역할을 순환하며 기능을 만들고, 5주차에 지표로 결과를 발표합니다.

---

## 7. 부록 A: 공통 코어와 스택별 오버레이 구분표

2차 산출물인 **팀 스타터 템플릿 레포**의 설계 입력입니다. Spring Boot(Gradle Kotlin DSL) + Next.js 모노레포 버전 구현은 [이 저장소](../README.md)에 있습니다. 스택별 CLAUDE.md(`backend/`, `web/`), 경로별 rules, 바뀐 스택만 검증하는 Stop hook으로 오버레이를 나눈 예시입니다. "스타터 레포가 개발 환경에 의존하지 않을까?"라는 질문에 대한 답: **일부는 의존합니다.** 그래서 의존하지 않는 부분(코어)과 의존하는 부분(오버레이)을 나눕니다.

| 구성 요소 | 공통 코어 (스택 무관) | 스택별 오버레이 | OS 의존성 |
|---|---|---|---|
| `CLAUDE.md` | 구조와 섹션 (Commands/Architecture/Conventions/Gotchas), 브랜치·커밋·PR 규칙 | Commands 섹션의 실제 명령 (`npm`/`gradle`/`uv`), 스택 특이사항 | 없음 |
| `.claude/rules/` | 테스트 원칙, 리뷰 원칙, 문서화 규칙 | 언어·프레임워크별 규칙 (React, Spring 등)과 `paths` 패턴 | 없음 |
| `.claude/settings.json` 권한 | deny: `.env*`, `secrets/**` / ask: `git push` | allow: 스택 명령 (`Bash(npm run test *)` 등) | 없음 |
| `.claude/settings.json` hooks | 보호 파일 차단 로직, Stop 검증 로직(변경 감지·루프 방지) | 포맷터·린터·테스트 명령 (prettier, black, ktlint, spotless...) | **있음**: 셸 차이, 출력 인코딩. exec form과 스크립트 언어로 해결 |
| 빌드 설정의 포맷터·테스트 로그 | 없음 | 기존 코드베이스는 변경분만 포맷(Spotless `ratchetFrom`), 실패 테스트 상세 출력(Gradle `testLogging`) | 없음 |
| `.claude/skills/` | `/spec`, `/pr-ready`, `/fix-issue`, `/adr` | 스택별 스캐폴딩 스킬 (예: `/new-endpoint`) | 스킬 안의 셸 명령 (`shell: bash \| powershell`) |
| `.claude/agents/` | `reviewer`, `security-reviewer` | 스택 전문 에이전트 (예: `db-migration-reviewer`) | 없음 |
| `.mcp.json` | 이슈 트래커(GitHub) | DB, 클라우드 등 | 실행 명령(`npx`, `uvx`) 설치 여부 |
| `docs/` | `specs/_template.md`, `adr/` 템플릿, PR 템플릿 | 없음 | 없음 |
| `.gitignore` 추가분 | `CLAUDE.local.md`, `.claude/settings.local.json`, `.claude/worktrees/` | 스택 기본 ignore | 없음 |

**템플릿 레포 구조 제안** (여러 스택 조합을 배포할 때의 제안입니다. 현재 스타터는 Spring Boot + Next.js 한 조합을 평면 구조로 담고 있고, hook은 Java로 작성했습니다)

```
claude-team-starter/
├── core/                    # 모든 프로젝트에 복사
│   ├── CLAUDE.md.template
│   ├── .claude/{settings.json, rules/, skills/, agents/, hooks/*.mjs}
│   └── docs/{specs/_template.md, adr/, PULL_REQUEST_TEMPLATE.md}
├── overlays/
│   ├── node-ts/             # 명령, allow 규칙, 포맷 hook, rules
│   ├── python-uv/
│   └── java-gradle/
└── scripts/apply.mjs        # core + 선택한 overlay를 대상 저장소에 병합 (크로스플랫폼)
```

> 대안: 코어를 **플러그인**(스킬, 에이전트, hooks)으로 배포하고, 저장소마다 CLAUDE.md와 settings만 두는 방식. 저장소가 3개 이상이 되면 이쪽이 유지보수하기 더 좋습니다.

---

## 8. 부록 B: 출처

- Best practices for Claude Code: https://code.claude.com/docs/en/best-practices
- How Claude remembers your project (CLAUDE.md, rules, imports): https://code.claude.com/docs/en/memory
- Settings files and precedence: https://code.claude.com/docs/en/settings
- Settings reference: https://code.claude.com/docs/en/settings-reference
- Skills (커스텀 커맨드 통합): https://code.claude.com/docs/en/skills
- Subagents: https://code.claude.com/docs/en/sub-agents
- Hooks guide: https://code.claude.com/docs/en/hooks-guide
- Plugin marketplaces: https://code.claude.com/docs/en/plugin-marketplaces
- MCP: https://code.claude.com/docs/en/mcp
- Worktrees: https://code.claude.com/docs/en/worktrees
- GitHub Actions: https://code.claude.com/docs/en/github-actions
- How Anthropic teams use Claude Code: https://claude.com/blog/how-anthropic-teams-use-claude-code
- Spec-driven development 참고:
  - https://www.datacamp.com/tutorial/spec-driven-development-with-claude-code
  - https://www.augmentcode.com/guides/claude-code-spec-driven-development
  - https://joshmcdonald.medium.com/running-a-small-team-on-a-big-project-spec-driven-development-with-claude-code-9a1b97f58551 (본문 접근 불가, 제목만 참고)
