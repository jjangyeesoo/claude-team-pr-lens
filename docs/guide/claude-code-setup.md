# Claude Code 설정: 강제되는 것, 한계, 동작

이 저장소의 `.claude/`(권한 규칙, hooks, 스킬, rules)가 무엇을 강제하고 어디까지 막는지 설명합니다. 설정은 `claude-team-starter`에서 가져와 PR Lens에 맞게 고친 것입니다. 요약은 루트 [README](../../README.md)에 있습니다.

## 자동으로 강제되는 것 vs 권고

| 항목 | 방식 | 위치 |
|---|---|---|
| `.env`, `.env.*`, `application-local.*`, `application-secret.*`, `secrets/` 읽기·수정 금지 (모든 깊이). allow된 명령에 `--output`, `--init-script`, `-I`, `--config`, `-c`를 붙이는 것도 금지 | **강제** (permissions deny) | `.claude/settings.json` |
| 위 파일 + gradle wrapper, `package-lock.json`, `node_modules/`, `.next/`, `.git/`, `build/claude-verify/`(검증 기록과 implementer 표시) 수정 금지. Windows 별칭 경로(`파일:스트림`, `ENV~1` 같은 8.3 이름, `gradlew.`처럼 끝에 점·공백을 붙인 이름)도 차단 | **강제** (PreToolUse hook, 2차 방어선) | `ProtectFiles.java` |
| 코드 변경 후 포맷과 테스트 통과 (바뀐 스택만) | **강제** (Stop hook, 실패 시 한 번 되돌려 보냄. `/task-flow`의 implementer가 일하는 동안은 implementer가 끝날 때 검증, 아래 "구현 중 검증") | `VerifyOnStop.java` |
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

### 구현 중 검증 (`/task-flow`)
`/task-flow`는 구현을 백그라운드 서브에이전트 `implementer`에게 맡깁니다. implementer는 실패 테스트만 담은 `test:` 커밋을 먼저 만들므로 일하는 도중의 트리는 빨갛고, 그동안 매니저(메인 세션)의 턴이 먼저 끝납니다. 이때 메인 Stop이 검증하면 매니저를 막고, 매니저가 implementer와 같은 파일을 고치려 듭니다. 그래서 검증 시점을 implementer가 끝날 때로 옮깁니다. 검증 명령과 판정은 위와 같습니다.

- **implementer 종료 검증**: `settings.json`의 PreToolUse hook(matcher `SubagentHandback`)이 `VerifyOnStop.java handback`을 돌립니다. 서브에이전트는 보고 도구 `SubagentHandback`을 불러야 끝나는데, SubagentStop hook은 그 보고 **뒤에** 와서 exit 2로 붙잡을 수 없기 때문입니다(2026-10-18 이 저장소에서 시험으로 확인. SubagentStop 입력에는 `last_assistant_message`도 없었음). 입력의 `agent_type`이 `implementer`가 아니면 아무것도 하지 않습니다.
  - 보고가 `BLOCKED:`로 시작하면(질문하려고 멈춤) 검증하지 않고 표시 파일을 `blocked`로 둡니다
  - 실패하거나, 통과했어도 `git status --porcelain`이 비어 있지 않으면(포맷터가 고친 파일, 커밋하지 않은 체크박스) exit 2로 보고를 막아 implementer가 고치고 커밋하게 합니다. 같은 implementer가 두 번째로 막히면 놓아주고, 실패였으면 표시를 `failed`로 둡니다. PreToolUse 입력에는 `stop_hook_active`가 없어 횟수를 표시 파일에 셉니다
  - 기록이 없어도 기준선만 잡지 않고 검증합니다(implementer는 커밋하고 끝나 트리가 늘 깨끗하므로). 이 브랜치가 건드리지 않은 스택은 `pass-nothing-to-verify`입니다
  - 결과(스택별 `pass`·`pass-nothing-to-verify`·`fail`·`unavailable`·`blocked`, 내용 해시, `clean`, `agent_id`)를 `build/claude-verify/implementer-result.json`에 남깁니다. 매니저는 `java .claude/hooks/VerifyOnStop.java --check <agentId>`로 이 파일이 그 implementer의 통과 기록이고 지금 내용과 같은지 확인합니다
- **메인 Stop이 건너뛰는 경우** (건너뛸 때마다 `systemMessage`로 알림)
  - hook 입력의 `background_tasks`에 `agent_type: implementer`, `status: running`인 작업이 있음. harness가 주는 값이라 파일로 꾸밀 수 없습니다. implementer를 멈추면 목록에서 빠지므로 다음 Stop부터 다시 검증합니다(멈출 때 SubagentStop은 오지 않았음)
  - 표시 파일 `build/claude-verify/implementer-marker.json`이 `blocked`·`failed`이고, 같은 세션(`session_id`)에서 쓰였고, 12시간이 지나지 않음. implementer는 끝났지만 트리가 빨간 채 사용자의 답을 기다리는 경우입니다. 표시는 통과하면 지워집니다
- **매니저 명령** (allow에 없어 실행할 때마다 확인 요청이 뜸)
  - `--mark <agentId> [blocked|failed]`: `/clear`나 새 세션 뒤 빨간 트리로 질문을 기다릴 때 표시를 다시 씁니다. 세션 ID를 모르므로 세션 확인 없이 유효합니다(출처 `mark`)
  - `--unmark`: 작업을 중단할 때 표시를 지웁니다
  - `--check <agentId>`: 위의 결과 확인
- **우회로와 대책.** 표시 파일은 일반 파일이라 Bash로 만들면 메인 Stop 검증을 끌 수 있습니다(이 변경은 hook 완화이며, 도입 PR에서 이유를 설명하고 승인받았습니다). 대책은 아래와 같고, 셸로 만드는 것 자체는 막지 못합니다.
  - 형식(`agent_id`, `at`)이 맞지 않거나 `status`가 `blocked`·`failed`가 아닌 표시, 다른 세션의 표시, 12시간이 지난 표시는 무시하고 검증합니다
  - 실행 중 판정은 표시가 아니라 `background_tasks`로 합니다
  - `ProtectFiles`가 Edit·Write로 `build/claude-verify/`를 고치지 못하게 막습니다
  - 건너뛸 때마다 안내가 보입니다. implementer를 띄우지 않았는데 이 안내가 나오면 표시 파일을 확인하세요
- **한계**
  - `SubagentHandback`은 harness 내부 도구 이름이라 Claude Code 버전에 따라 바뀔 수 있습니다. 이름이 바뀌면 종료 검증이 조용히 돌지 않지만, 결과 파일이 없거나 이전 implementer의 것이므로 매니저의 `--check`가 "결과 없음"으로 멈춥니다
  - `claude --continue`로 이어 가면 `session_id`가 그대로입니다(시험으로 확인). 그래서 세션 확인이 막는 것은 새 세션뿐입니다
  - 백그라운드 서브에이전트의 권한 프롬프트가 메인 세션에 뜨는지는 `auto` 모드에서 시험해 확인하지 못했습니다

## 다른 프로젝트에 적용하기: 공통 코어와 스택별 오버레이

이 절은 스타터에서 온 설명입니다. 이 저장소의 Claude Code 설정을 다른 프로젝트에 가져갈 때 참고합니다.

이 저장소는 "공통 코어 + Java/Gradle 오버레이 + Next.js 오버레이"가 합쳐진 상태입니다. 기존 프로젝트나 다른 스택에 가져갈 때는 아래 표를 기준으로 복사하고 수정하세요. **FE가 없는 프로젝트라면 `web/`, `.claude/rules/frontend/`, `skills/new-page`를 지우면 됩니다.** hook은 폴더가 없는 스택을 자동으로 건너뜁니다.

| 파일 | 구분 | 다른 프로젝트에 적용할 때 |
|---|---|---|
| `.claude/skills/spec`, `pr-ready`, `adr` | 공통 코어 | 그대로 복사. `pr-ready`의 스택별 검증 명령만 수정 |
| `.claude/agents/reviewer.md` | 공통 코어 (+스택 항목) | backend/web 규칙 항목을 프로젝트 구조에 맞게 수정. frontmatter의 `model: opus`는 리뷰 품질이 세션 모델에 좌우되지 않도록 둔 것이다(별칭이라 최신 Opus를 따른다). 팀 사정에 맞게 조정 |
| `.claude/agents/context-reviewer.md`, `.claude/rules/context/editing.md` | 공통 코어 | 그대로 복사. 서로 맞물린 파일 목록과 승인 규칙이 있는 문서 경로만 프로젝트에 맞게 수정. `model: opus`는 `reviewer`와 같은 이유로 둔 것이다 |
| `.claude/skills/task-flow`, `.claude/agents/planner.md`, `implementer.md` | 공통 코어 (+스택 항목) | implementer의 포맷 명령(`spotlessApply`, `npm run format`)과 테스트 실행 명령, 매니저의 테스트 파일 판정 패턴(`*/src/test/*`, `*.test.ts`)을 스택에 맞게 수정. 종료 검증은 `settings.json`의 PreToolUse(`SubagentHandback`) 항목과 함께 가져갈 것 |
| `docs/specs/_template.md`, `docs/adr/`, PR 템플릿 | 공통 코어 | 그대로 복사 (ADR 내용만 새로 작성) |
| `.claude/hooks/ProtectFiles.java` | 공통 코어 | 보호 패턴(`protectedReason`)만 수정 |
| `.claude/hooks/VerifyOnStop.java` | 코어 로직 + 스택 설정 | `STACKS` 목록만 수정 (폴더, 명령, 실행 전 조건, 실패 판정 문자열). Maven이면 `mvnw`, Python이면 `uv run pytest` 등 |
| `.claude/settings.json` deny/ask | 공통 코어 | 그대로. 보호 대상이 바뀌면 `ProtectFiles`, `.gitignore`와 함께 수정 |
| `.claude/settings.json` allow | **스택별** | 빌드 명령에 맞게 교체 (Bash와 PowerShell 둘 다) |
| 루트 `CLAUDE.md` | 공통 코어 | 구조와 스택 간 규칙만 프로젝트에 맞게 수정 |
| `backend/CLAUDE.md`, `web/CLAUDE.md` | **스택별** | Commands/Architecture/Gotchas를 새로 작성 (`/init`으로 초안을 만든 뒤 병합) |
| `.claude/rules/backend/*`, `.claude/rules/frontend/*`, `skills/new-endpoint`, `skills/new-page` | **스택별** | 프레임워크에 맞게 새로 작성. rules의 `paths`를 폴더 구조에 맞출 것 |
| `backend/build.gradle.kts`의 `spotless {}` | **스택별** | 팀 포맷터로 교체. **기존 코드베이스라면 `ratchetFrom("origin/main")` 주석을 풀어** 첫 실행에 전체 파일이 재포맷되지 않게 함 |
| `backend/build.gradle.kts`의 `testLogging {}` | **스택별 (필수)** | Stop hook이 Claude에게 넘기는 실패 정보가 이 설정에 달려 있음. 빼지 말 것 |
| `web/package.json`의 `verify` 스크립트 | **스택별 (필수)** | Stop hook이 실행하는 진입점. 포맷 → lint → 타입 → 테스트 순서를 유지 |
| `.github/workflows/ci.yml` | 스택별 | job을 스택 구성에 맞게 수정. action 버전은 도입 시점의 최신으로 확인 |
| `.gitignore`의 Claude Code·Secrets 항목, `.worktreeinclude` | 공통 코어 | deny 규칙, ProtectFiles와 같은 목록을 유지 |
| `.gitattributes` | 공통 코어 | `* text=auto eol=lf` 유지. 빼면 Windows(`core.autocrlf=true`)에서 포맷터가 LF로 바꾼 파일이 전부 "수정됨"으로 보이고 CI의 `format:check`가 실패함. 기존 저장소에 넣을 때는 `git add --renormalize .` 커밋을 따로 만들 것 |
| `gradlew` 실행 권한 | **스택별** | 복사 후 `git update-index --chmod=+x backend/gradlew` 확인 (hook은 `sh gradlew`로 실행해 권한이 없어도 동작) |

기본 브랜치 이름: `reviewer`, `context-reviewer` 에이전트와 `/pr-ready`, `/task-flow`는 `origin/main`(원격이 없으면 `main`)을 기준으로 diff를 보고, `VerifyOnStop.java`의 `changedOnBranch`도 같습니다. 다른 이름이면 이 파일들을 수정하세요.

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
- 의존성 조합(jqwik 1.10.1, ArchUnit 1.5.1, picocli, Anthropic SDK와 JUnit 6.0.3): [`docs/spikes/2026-10-04-build-stack.md`](../spikes/2026-10-04-build-stack.md)
- GitHub Actions의 CI(`.github/workflows/ci.yml`): PR과 main push마다 실행되어 통과하고, ruleset `Protect-main`이 `backend`·`web`을 필수 상태 체크로 요구합니다(2026-10-16 확인)
- **확인하지 못한 것**: macOS/Linux 실행, `Edit(/.claude/**)` ask 규칙이 실제로 승인을 묻는지

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
