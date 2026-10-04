# 01. Claude Code 활용 가능 여부

[← 목차](README.md)

## 결론

스펙 본문은 일반 Markdown이라 Claude Code가 그대로 읽을 수 있고, `.config.kiro` 외에는 Kiro 전용 문법이 없습니다. 다만 그대로는 쓸 수 없습니다.

- 두 설계 문서가 스타터를 보지 못한 채 쓰여서(`P1-des:15`, `P2-des:20-21`) 패키지 구조, 에러 포맷, 테스트 실행 방식이 저장소의 ADR, rules, Stop hook과 어긋납니다.
- `P2-des`는 추정 4.7만~5.8만 토큰이라 통째로 `@` 참조하면 안 되고, 작업 단위로 잘라 읽어야 합니다.
- 작업 → 요구사항 번호 → 설계 절 제목의 연결은 정확해서 grep으로 잘라 읽기가 가능합니다.
- 위치는 **`claude-team-prlen/docs/specs/pr-lens-p1-cli/`, `docs/specs/pr-lens-p2-automation/`로 옮기는 것**을 권합니다.

## 1. 형식

### Kiro 전용이거나 오독 위험이 있는 것

- **`.config.kiro`**: P1·P2·P3 세 파일이 같은 `specId`를 가진 한 줄 JSON입니다. Claude Code에는 의미가 없으니 옮길 때 지워도 됩니다.
- **`- [ ]*` 선택 작업**: 뜻은 `P1-tasks:8`, `P2-tasks:8`에 글로 적혀 있어 Claude가 이해합니다. 표준 체크박스는 아니므로 완료 표시는 `- [x]*`로 통일한다는 규칙이 필요합니다. 선택 작업은 P1 35개, P2 25개입니다.
- **요구사항 태그가 두 형태**: `_Requirements: 17.3, 22.5_`(`P1-tasks:21`)와 `**Validates: Requirements 17.4, …**`(`P1-tasks:42`, 콜론 없음). `Requirements:`로만 grep하면 뒤의 것을 놓칩니다.
- **요구사항 번호 `N.M`**: `requirements.md`에 문자열로 없습니다. `### Requirement N:` 제목 아래 번호 목록의 M번째 항목입니다(`P1-req:76` 이하). `design.md`에는 "요구사항 2.7"처럼 문자 그대로 있어 grep이 됩니다.
- **용어 표기 차이**: Glossary는 `Diff_Parser` 형식(`P1-req:15`), 설계·코드는 `DiffParser`(`P1-des:84`). 한쪽 이름으로 다른 문서를 grep하면 빈 결과가 나옵니다.
- **그대로 읽히는 것**: EARS 문장(`WHEN/IF … THE X SHALL …`), mermaid 블록(`P1-des:34`, `:100`), 속성 태그 주석(`P1-tasks:9`), 의존 그래프 JSON(`P1-tasks:348`, `P2-tasks:355`). front matter는 없습니다.
- **줄 끝**: `P2-tasks`만 CRLF입니다. `repo/.gitattributes:2`가 커밋 시 LF로 바꿉니다.

### 정합성 검사 결과

- 작업 ID와 의존 그래프가 정확히 일치합니다(누락·중복 없음).
- 없는 요구사항을 가리키는 태그는 0건입니다.
- 설계의 Property(P1 43개, P2 27개)는 모두 작업에 있습니다.
- **선택 작업(PBT)으로만 덮이는 수락 기준**: P1 17개, P2 14개(예: P1 2.9, 3.13, 11.8~11.10). `*` 작업을 미루면 이 기준은 검증되지 않습니다.
- **어떤 작업에도 없는 기준**: P1 9.7, P2 17.1·17.2는 Notes에 수동 측정으로 적혀 있습니다(`P1-tasks:343`, `P2-tasks:350`). P2 11.11, 11.12, 15.1, 18.5는 태그에 없습니다.

### 이미 없는 경로를 가리키는 것

| 위치 | 가리키는 곳 | 문제 |
|---|---|---|
| `docs/study-project/ROADMAP.md:5` | `../../claude-team-starter/README.md` | 실제 폴더는 `claude-team-prlen` |
| `docs/claude-code-team-methodology.md:664` | `../claude-team-starter/README.md` | 같음 |
| `repo/.claude/skills/pr-ready/SKILL.md:15` | `.github/pull_request_template.md` | 없음 |
| `repo/README.md:57-59` | `.github/` | 없음 |
| `P2-tasks:332`, `P2-des:1067` | `./gradlew :backend:generateWebhookFixtures` | `repo/backend/settings.gradle.kts:1`은 단일 프로젝트라 `:backend` 경로가 없음 |
| `P1-des:15`, `P2-des:20` | "워크스페이스에 스타터가 없다" | 옮긴 뒤 사실과 다름 |

### 옮기면 깨지는 것

- `P2-req:7`의 `.kiro/specs/pr-lens-p1-cli/requirements.md`(텍스트 경로): `docs/specs/`로 옮기면 깨집니다.
- `../claude-code-team-methodology.md` 링크: `PRD.md:5, 19, 122`, `PLAYBOOK.md:4, 37, 51, 119, 125`, `ROADMAP.md:18, 114`. `docs/` 트리를 구조 그대로 `repo/docs/`에 넣을 때만 유지됩니다. `PRD.md:7`의 계획대로 `docs/PRD.md`로 평탄화하면 전부 깨집니다.
- `claude-code-team-methodology.md:622, 658`의 `study-project/…`도 같은 조건입니다.

### 옮겨도 유지되는 것

- `tasks.md:5`의 `[design.md](design.md)`, `design.md:5`의 `[requirements.md](requirements.md)` (두 스펙 모두)
- `P2-des:9`의 `../pr-lens-p1-cli/design.md` (두 폴더가 같은 이름의 형제로 남을 때)

## 2. 크기와 컨텍스트

토큰은 한글 1자 ≈ 1~1.5토큰, ASCII 3.5자 ≈ 1토큰으로 계산한 추정치이며 실측하지 않았습니다.

| 파일 | 문자 수 | 추정 토큰 |
|---|---|---|
| `P1-req` | 38,530 | 1.9만~2.5만 |
| `P1-des` | 47,388 | 2.1만~2.7만 |
| `P1-tasks` | 21,635 | 0.9만~1.1만 |
| `P2-req` | 43,861 | 2.1만~2.7만 |
| `P2-des` | 107,413 | 4.7만~5.8만 |
| `P2-tasks` | 25,437 | 1.05만~1.3만 |

- 전체 `@` 참조는 권하지 않습니다. P1 세 파일이 약 5만~6만, P2 세 파일이 약 8만~10만 토큰이고, P2 작업은 P1 용어·타입까지 필요합니다(`P2-req:7`).
- 설계의 절 제목에 요구사항 번호가 들어 있어 잘라 읽기가 가능합니다(`P2-des:267` "SignatureVerifier (요구사항 2)", `:933` "Line_Comment 단계 (요구사항 12)").
- 클래스 이름이 몇 곳에만 모여 있습니다. P1 `DiffParser`는 `design.md` 47, 84, 194, 544, 884행, P2 `RejectionParser`는 940행 한 곳입니다.
- Property는 `### Property N:` 제목으로, spike와 공백은 표의 행 번호로 찾습니다(`P2-des:1558`, `:1576`).
- 가장 큰 절은 P2 T2 store·query(`P2-des:392-812`, 약 420줄)입니다. 16.x 작업은 이 절을 다시 `####` 단위로 잘라 읽어야 합니다.

### 권장 작업 방식 (작업 하나당 약 5천~1.2만 토큰)

1. `tasks.md`에서 해당 작업 블록과 Overview(1~11행), 의존 그래프에서 선행 작업 완료 여부를 읽습니다.
2. 태그의 요구사항 번호마다 `### Requirement N` 절만 읽습니다. Glossary는 필요한 용어만 grep합니다.
3. `design.md`를 클래스 이름으로 grep해 그 절만 읽습니다. 레코드를 다루면 Data Models를, PBT 작업이면 해당 Property 절과 "속성 기반 테스트 규칙"을 더합니다.
4. spike나 G-n을 언급한 작업은 해당 표의 행만 읽습니다.

### 분할과 색인

- P1은 분할하지 않습니다.
- `P2-des`는 3주차 킥오프 전에 기존 `###` 경계로 기계적으로 나누기를 권합니다(개요·아키텍처 / T1 / T2 / T3 / 공통·데이터 모델 / 속성 / 오류·테스트·spike).
- 스펙 폴더마다 60줄 이내의 `README.md` 색인이 필요합니다. 내용: 상태, 결정 대기 항목 → ADR 매핑, 트랙 → 패키지 → 작업 번호 범위, 절 제목 목록(줄 번호가 아니라 제목), Glossary 용어 ↔ 클래스 이름 대응, 위 읽기 절차.

## 3. 기존 워크플로와의 공존

### 위치: `docs/specs/<spec-name>/`로 옮깁니다

- **옮길 때 얻는 것**
  - repo의 모든 장치가 `docs/specs/`를 봅니다(`repo/CLAUDE.md:8`, `pr-ready/SKILL.md:13`, `agents/reviewer.md:9`).
  - PR Lens 자신의 스펙 수집이 `docs/specs/`만 인정합니다(`P1-req:175`, `P1-des:219`). `.kiro/specs/`에 두면 도그푸딩 때 PR 본문에 링크한 스펙이 경고와 함께 버려집니다.
- **옮길 때 잃는 것**: Kiro가 스펙으로 인식하지 못하게 되고(Kiro 동작은 확인하지 못함), `P2-req:7` 한 줄을 고쳐야 합니다.
- `.kiro/specs/` 유지는 P3 설계·작업을 Kiro로 계속 생성할 때만 의미가 있습니다. 그 경우에도 위 두 문제는 남습니다.

### 이름과 `/spec`의 역할

- 단계 스펙은 폴더(`docs/specs/pr-lens-p1-cli/{requirements,design,tasks}.md`)로 둡니다.
- `/spec`이 만드는 `docs/specs/<날짜>-<slug>.md`는 tasks.md에 없는 추가 기능과 버그용으로 남깁니다.
- `ROADMAP.md:35`의 "2주차 스펙 3건을 `/spec`으로 작성"은 Kiro 스펙 하나(트랙 3개)로 대체된다고 고쳐야 합니다.

### 진실의 원천

- 무엇을 만드는가는 `requirements.md`, 어떻게는 `design.md`, 순서와 진행은 `tasks.md`입니다.
- 결정 대기 항목은 ADR이 확정하고, 확정되면 스펙 문구를 고칩니다(`P1-req:435`의 규칙 그대로).
- spike 결과가 설계와 다르면 설계를 먼저 고칩니다(`P2-tasks:348`).

### 진행 추적과 PR 단위

- 구현 PR이 자기 작업의 체크박스를 같은 PR에서 `[x]`로 바꿉니다.
- PR 제목과 브랜치에 작업 ID를 넣습니다(예: `feat/t2-5.1-diff-parser`).
- "PR 하나 = 스펙 하나"(`repo/CLAUDE.md:22`, `PLAYBOOK.md:121`)는 "하위 작업 하나 또는 붙어 있는 구현 + `*` 테스트 묶음"으로 바꿔야 합니다. P1만 하위 작업이 80개입니다.

### 새 스킬

- 추가를 권합니다. 100번 넘게 반복될 절차이기 때문입니다.
- `/task <spec> <id>`가 할 일: 2장의 읽기 절차 → 선행 작업 확인 → 실패 테스트 먼저 → 범위 밖 변경 금지 → 체크박스 갱신 → Checkpoint 작업이면 테스트만 돌리고 질문.
- `/pr-ready`는 reviewer에게 스펙 폴더와 작업 ID를 넘기도록 한 줄 고칩니다.

## 4. 스펙과 저장소의 충돌

| # | 충돌 항목 | 스펙 쪽 | 저장소 쪽 | 고칠 곳 |
|---|---|---|---|---|
| 1 | 패키지 루트 | `com.prlens` (`P1-des:17`, `P1-tasks:17`) | `com.example.starter`, `group = "com.example"` (`build.gradle.kts:8`), 이름 `starter` (`settings.gradle.kts:1`, `application.properties:1`) | 소스 이동과 세 파일 수정 (`ROADMAP.md:28`의 1주차 작업) |
| 2 | 패키지 구조 | 기술 단위 평면 패키지 14개 + P2 7개, `domain`/`api` 없음 (`P1-des:77-94`, `P2-des:45-57`) | `<feature>.{domain,api}` (`docs/adr/0001…md:8`, `backend/CLAUDE.md:11-13`, `reviewer.md:14`) | ADR 0001을 대체하는 새 ADR. `backend/CLAUDE.md` Architecture, `reviewer.md:14`, `docs/adr/README.md:4` 수정 |
| 3 | Spring 애너테이션 위치 | `cli` 조립 코드에만 (`P1-tasks:20`), P2는 `server`·`webhook`·`query`·`store.db`에만 (`P2-des:71`) | `common.config.TimeConfig`, `common.error.ApiExceptionHandler`, `memo.*`, 루트의 `StarterApplication`. 메모는 2주차 첫 기능 PR까지 유지 (`ROADMAP.md:28`) | P1 작업 1에서 메모와 `common`을 지우거나 옮길지, ArchUnit 예외를 둘지 결정. `backend/CLAUDE.md:14-15` 수정 |
| 4 | 에러 포맷 | `ProblemDetail` 가정 (`P2-des:21`, `:775`, `:1108`, `P2-tasks:65`, `:223`) | `ErrorResponse { code, message, details }`, `ProblemDetail`은 기각한 대안 (`docs/adr/0002…md:8, 11`). web도 이 형식을 파싱 (`web/src/lib/api/http.ts:36-40`) | 권장: 스펙 5곳을 `ErrorResponse`로 수정. P2 요구사항은 원래 ADR 0002를 따르라고 함 (`P2-req:51`). 바꾸려면 ADR 0002 대체 + web 수정 |
| 5 | API 규칙의 적용 범위 | 컨트롤러가 `webhook`, `query`, `server`에 있음 | `paths: backend/src/main/java/**/api/**/*.java` (`rules/backend/api-design.md:3`)라 로드되지 않음 | `paths`를 `**/webhook/**`, `**/query/**`, `**/server/**`로 변경 |
| 6 | API 규칙의 내용 | `/api/repositories`, `/webhooks/github`, `limit = 100` 고정, 경로 변수 수동 검증, `query` 전용 오류 처리 (`P2-des:718-721`, `P2-tasks:219-221`) | `/api/v1/<복수형>`, `page`·`size`, `@Valid`, `ApiExceptionHandler` 매핑 (`api-design.md:6-11`) | 규칙을 스펙에 맞춰 다시 쓰거나 스펙 URL에 `v1`을 넣을지 결정 (OpenAPI 작성 전) |
| 7 | 테스트 규칙 | jqwik PBT, ArchUnit, 골든 픽스처, 가짜 구현, Testcontainers (`P1-des:865-898`, `P2-des:1471-1523`) | JUnit 5 + AssertJ, `@WebMvcTest`, 예시가 `MemoServiceTest` (`rules/backend/testing.md:6-7`) | `testing.md`에 추가: 속성 하나 = `@Property` 하나, 태그 주석, `testkit` 위치, 픽스처 경로, 반례를 예시 테스트로 고정. 메모 예시 교체 |
| 8 | 의존성 추가 | P1 작업 1에 5종 (`P1-tasks:18`), P2 작업 1에 5종 + spike 뒤 추가 (`P2-tasks:21`) | `Edit(/**/build.gradle.kts)`는 ask (`settings.json:46`), 사람 승인 (`CLAUDE.md:23`) | 충돌 아님. 다만 승인자가 없는 세션은 멈춤. ADR-0004·0005와 버전이 정해진 뒤 리드가 직접 진행 |
| 9 | Gradle 명령 허용 | `./gradlew check` (`P2-tasks:22, 71, 338`), `generateWebhookFixtures` | allow에 `test`, `build`, `spotless*`만 있음 (`settings.json:4-7, 20-23`) | allow에 `check` 추가 (Bash, PowerShell 둘 다) |
| 10 | deny / ProtectFiles | 픽스처는 `snapshot.json` 등 JSON과 `webhook/` 본문 (`P1-tasks:325`). `.env`, `secrets/`는 문자열 패턴으로만 등장 (`P1-req:99`). 설정은 `.prlens.yml`과 환경변수 | `.env*`, `secrets/`, `application-local.*` 읽기·쓰기 차단 (`settings.json:52-61`, `ProtectFiles.java:69-72`). `.github`는 막지 않음 | 수정 불필요. "픽스처에 실제 `.env`나 `secrets/` 경로의 파일을 만들지 않는다"를 `testing.md`에 추가 |
| 11 | 줄 끝 정규화 | 원문 바이트를 보존하는 서명 픽스처 `crlf_unicode` (`P2-tasks:331`, `P2-des:1066-1067`), 골든 파일 바이트 비교 (`P1-tasks:327`) | `* text=auto eol=lf` (`.gitattributes:2`) | 픽스처 경로에 `-text` 지정 추가. 없으면 CRLF 픽스처의 서명이 깨짐 |
| 12 | Stop hook 범위와 시간 | Testcontainers 통합 테스트(`JdbcReviewStoreIT`, `QueryApiContractIT`), 16스레드 × 50회 동시성 테스트, jqwik 기본 1,000회 (`P2-des:1486, 1521`). Docker 테스트는 Linux 러너에서만 (`P2-tasks:338`) | `spotlessApply test`를 응답이 끝날 때마다 실행, 제한 540초, 시간 초과는 알림만 하고 통과 기록 없음 (`VerifyOnStop.java:66-70, 92, 210`) | ADR-0006에서 Docker 테스트를 태그나 별도 태스크로 분리. hook은 빠른 `test`만, CI는 `check`. 스펙에 분리 방법이 없으므로 P2 작업 1에 추가 |
| 13 | Stop hook 동작 조건 | 작업마다 검증을 전제 (`P1-tasks:69`) | git 저장소가 아니면 아무것도 안 함 (`VerifyOnStop.java:106`). 지금은 `.git`이 없음 | `git init`과 첫 커밋 |
| 14 | Gradle 프로젝트 경로 | `:backend:generateWebhookFixtures` | 서브프로젝트 없음 | 스펙 2곳을 `backend/`에서 `./gradlew generateWebhookFixtures`로 수정 |
| 15 | 브랜치 이름 | `feat/<track>-<slug>` (`PLAYBOOK.md:122`, `P1-des:96`) | `feat/<issue>-<slug>` (`CLAUDE.md:21`) | `CLAUDE.md:21` |
| 16 | 워크플로 문구 | tasks.md 작업 단위 구현 | "`/spec`으로 스펙을 만들고 새 세션에서 구현", "PR 하나 = 스펙 하나" (`CLAUDE.md:11, 22`) | `CLAUDE.md` Workflow와 Conventions, `PLAYBOOK.md:121` |
| 17 | DTO와 `types.ts` | 3주차에 조회 DTO만 추가 (`P2-tasks:216`), web 타입은 4주차 (`ROADMAP.md:86`) | "API가 바뀌면 같은 PR에서 `types.ts`도" (`CLAUDE.md:17`, `reviewer.md:16`) | P3 전까지의 예외를 `CLAUDE.md`에 명시. 아니면 reviewer가 매번 지적 |
| 18 | 메모 샘플 서술 | PR Lens | `CLAUDE.md:1-2`, `backend/CLAUDE.md:6, 11`, `README.md` 전체, `new-endpoint/SKILL.md:8, 10`, `testing.md:6`, `api-design.md:6` | 각 파일 수정. `new-endpoint`는 `query` 패키지 기준으로 다시 쓰거나 P3까지 제거 |
| 19 | CI와 PR 템플릿 | 세 OS 매트릭스 (`P1-tasks:332-333`), `review`·`llm` diff 경고 단계와 PR 체크 항목 (`P2-tasks:23`), 지적·반영 건수 칸 (`ROADMAP.md:36`) | `.github` 없음. `pr-ready/SKILL.md:15`가 템플릿을 참조 | `.github/workflows/ci.yml`, `.github/pull_request_template.md`를 다시 만듦 |

## 5. P1 작업 1을 시작하기 전에 없는 것

- git 저장소, 원격, `.github`(CI와 PR 템플릿)
- ADR 0003~0006. 번호가 붙은 결정이 스펙에 "대기"로 남아 있습니다(`P1-des:23-25`, `P2-des:31`). 작업 1이 picocli와 SDK 버전을 고정하므로 0004·0005가 먼저입니다.
- ADR 0001·0002와 스펙의 차이에 대한 결정 (4장 2~4행)
- spike 기록 `docs/spikes/` (`ROADMAP.md:31`). 13.1, 15.1, 18.7이 여기에 의존합니다(`P1-tasks:342`).
- PR Lens 기준의 `CLAUDE.md`, `backend/CLAUDE.md`, rules, reviewer
- 스펙 색인과 `/task` 스킬
- 스펙의 "작업 환경 확인 결과" 절 갱신 (`P1-des:13-17`, `P2-des:18-22`)

## 착수 전 체크리스트

1. `claude-team-prlen`에서 `git init`과 첫 커밋을 하고 GitHub 저장소를 만든 뒤, `/hooks`와 `/context`로 Stop hook이 실제로 도는지 확인합니다.
2. `.github/workflows/ci.yml`(backend, web)과 `.github/pull_request_template.md`를 다시 만듭니다. 템플릿에 AI 리뷰 지적·반영 건수 칸과 작업 ID 칸을 넣습니다.
3. 스펙을 `docs/specs/pr-lens-p1-cli/`, `docs/specs/pr-lens-p2-automation/`로 옮기고 `.config.kiro`는 뺍니다. `docs/` 트리는 구조 그대로 `repo/docs/`에 넣습니다.
4. 링크를 고칩니다: `P2-req:7`, `ROADMAP.md:5`, `claude-code-team-methodology.md:664`, `README.md:4`.
5. ADR을 결정합니다.
   - 0001 대체: 평면 패키지 + ArchUnit 의존 규칙, 메모와 `common` 처리 시점
   - 0002: 유지하고 스펙을 고칠지, `ProblemDetail`로 바꿀지
   - 0003 저장소 구조, 0004 picocli, 0005 모델·effort·최대 출력 토큰
   - 0006 DB·테스트: Docker 테스트 분리 방식과 Stop hook 범위 포함
6. 결정에 맞춰 스펙을 고칩니다: "작업 환경 확인 결과" 두 절, `ProblemDetail` 5곳, `:backend:` 2곳, 결정 대기 표의 상태.
7. 패키지를 `com.prlens`로 바꾸고 `build.gradle.kts:8`, `settings.gradle.kts:1`, `application.properties:1`을 고친 뒤 `./gradlew test`가 통과하는지 확인합니다.
8. 컨텍스트 파일을 다시 씁니다.
   - `CLAUDE.md`: 프로젝트 설명, 워크플로, 브랜치, PR 단위, `types.ts` 예외
   - `backend/CLAUDE.md`: 패키지 표 요약, 단일 테스트 예시
   - `README.md`
   - `reviewer.md:14-16`
9. rules를 고칩니다: `testing.md`(jqwik, ArchUnit, 픽스처, testkit), `api-design.md`(`paths`와 URL·오류 규칙). `ROADMAP.md:57`의 모듈별 rules(`backend/github`, `backend/review`)는 2주차 중에 추가합니다.
10. `settings.json` allow에 `gradlew check`를 추가하고, `.gitattributes`에 픽스처 경로 `-text`를 추가합니다.
11. 스펙 폴더마다 `README.md` 색인을 쓰고, `/task` 스킬을 추가하고, `/pr-ready`와 `new-endpoint`를 고칩니다.
12. spike 3건의 결과를 `docs/spikes/`에 기록하고, 1주차 완료 조건(`ROADMAP.md:42`)을 확인한 뒤 `/task pr-lens-p1-cli 1`을 시작합니다.

## 확인하지 못한 것

- 토큰 수는 문자 수로 계산한 추정치입니다. 큰 파일을 `@`로 참조할 때 Claude Code가 잘라 내는지, Read 한 번의 출력 상한에 걸리는지는 시험하지 않았습니다.
- `requirements.md`와 `design.md`의 본문 전체는 읽지 않았습니다. 읽은 범위는 개요, Glossary, 아키텍처·패키지, 오류 처리, 테스트 전략, 결정 대기, 요구사항 공백, spike 표와 grep 결과입니다. 읽지 않은 본문에 다른 충돌이 있을 수 있습니다.
- `claude-code-team-methodology.md`는 제목 목록과 링크 3줄만 봤습니다. 문서 간 앵커는 대상 제목이 있는 것만 확인했고, 렌더러가 만드는 슬러그와 일치하는지는 확인하지 않았습니다.
- 빌드와 테스트는 돌리지 않았습니다. "Gradle `test`가 `*IT` 클래스도 실행한다"와 "Docker가 없으면 Stop hook이 막는다"는 Gradle 기본 동작에 근거한 판단입니다.
- Kiro가 `.kiro/specs/` 밖의 스펙이나 `.config.kiro` 없는 폴더를 어떻게 다루는지는 확인하지 못했습니다.
- `web/`은 `CLAUDE.md`, `AGENTS.md`, `src/lib/api/http.ts`만 읽었습니다.
