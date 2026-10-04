# Requirements Document

## Introduction

PR Lens P2(3주차 마일스톤 M2)는 PR이 열리거나 커밋이 추가되면 GitHub App webhook으로 리뷰를 자동 실행하고, 결과를 DB에 저장하고, PR에 요약 코멘트와 라인 코멘트를 게시하는 기능입니다. 범위는 PRD의 FR-7~FR-11, FR-12 일부(조회 REST API 초안과 OpenAPI), 그리고 서버에 해당하는 비기능 요구사항(보안, 응답 시간, 비용 기록, 리뷰 대상 저장소 한정)입니다.

- 기반: P1 스펙(`docs/specs/pr-lens-p1-cli/requirements.md`)의 용어, 공유 타입(PullRequestSnapshot, ReviewContext, ReviewResult, Finding), 라인 판정(Inline_Eligible, Summary_Only), Incomplete_Reason, Configuration, 종료 코드, 비밀정보 치환, 재시도 규칙을 그대로 씁니다. 이 문서에서 "P1 요구사항 N"은 P1 스펙의 요구사항 N을 가리킵니다.
- 리뷰 로직: P1의 Review_Engine을 수정 없이 재사용합니다(P1 요구사항 17.3). P2는 실행 방식(webhook, 비동기), 저장, 게시만 추가합니다.
- 트랙 분할(3주차): T1 A webhook·비동기·중복 방지(요구사항 1~6, `webhook/`), T2 B DB 저장·CLI `--save`·조회 API 초안(요구사항 7~10, `store/`), T3 C GitHub 게시·채택/기각 수집(요구사항 11~15, `publish/`). 공용 GitHub 호출은 `github/`, 조회 컨트롤러는 `query/`, Spring 조립과 서버 시작은 `server/`, 작업 범위 도구(취소, 제한 시간, usage 집계)는 `execution/`에 둡니다. 패키지 경계와 의존 규칙은 요구사항 16에 있고, 패키지 구조의 근거는 [ADR 0003](../../adr/0003-role-based-flat-packages.md)입니다.
- 일정 메모: 월요일에 기능 리드 A가 패키지 경계(`webhook/`, `github/`, `store/`, `publish/`)와 T2의 Review_Store 인터페이스를 먼저 머지합니다. 이는 작업 순서 계획이며 시스템 요구사항이 아닙니다.
- 대체 경로: 3주차에 webhook 수신이 막히면 GitHub Actions에서 CLI를 실행해 코멘트를 게시합니다(요구사항 20, ROADMAP 일정 위험). 이 경우 서버가 필요한 요구사항(1, 2, 4, 6, 10, 17)은 연기합니다. 게시(11~14)는 `--publish`로 유지합니다. 저장(7~9)과 채택/기각 수집(15)은 DB가 필요한데, 호스팅 러너에서는 로컬 DB에 닿지 않으므로 대체 경로에서는 동작하지 않습니다. 중복 방지(5)도 `--save` 없이는 효과가 없습니다. 그래서 대체 경로에서는 FR-7·8뿐 아니라 FR-9, FR-11과 조회 API의 데이터도 비게 됩니다. 대체 경로에서 결과를 남기는 방법은 결정 대기 D-15입니다.
- 범위 밖: 기각 사유 수집(PRD 9장. 요구사항 15는 채택/기각 상태만 모으고, 사유는 PLAYBOOK의 수기 기록으로 남김), 웹 화면(P3, FR-13·FR-14), 통계 집계 API(P3), 조회 API 페이지네이션(P3), 사용자 인증과 멀티 테넌시, 운영 배포와 가용성 보장.

## Glossary

P1 Glossary의 모든 용어(PR_Lens_CLI, PR_Fetcher, Context_Collector, Review_Engine, Basis_Validator, Result_Codec, Output_Formatter, Config_Loader, Config_Printer, Configuration, PullRequestSnapshot, ReviewContext, ReviewResult, Incomplete_Reason, Finding, Basis, Demotion_Record, Inline_Eligible, Summary_Only, Review_Mode, Chunk, Normalized_Repo_Path, Secret_Value 등)는 P1 정의를 그대로 따릅니다. 아래는 P2에서 추가하거나 확장하는 용어입니다.

- **PR_Lens_Server**: webhook 수신, 비동기 리뷰 실행, 게시, 조회 API를 제공하는 Spring Boot 서버 프로세스. Configuration의 서버 바인딩 주소(기본 `127.0.0.1`, 요구사항 10.11)에서 요청을 받는다
- **Webhook_Receiver**: GitHub App의 webhook HTTP 요청을 받아 검증하고 Review_Job을 등록하는 구성 요소 (`webhook/` 패키지, T1)
- **Signature_Verifier**: 요청 원문 바이트와 Webhook_Secret으로 `X-Hub-Signature-256` 헤더를 검증하는 구성 요소 (`webhook/` 패키지, T1)
- **Webhook_Secret**: GitHub App에 설정한 webhook 서명 비밀값. 환경변수 `GITHUB_WEBHOOK_SECRET`으로만 받는다
- **App_Private_Key**: GitHub App의 JWT 서명용 개인 키. 환경변수 `GITHUB_APP_PRIVATE_KEY`로만 받는다
- **Installation_Token**: GitHub App 설치(installation)별로 발급받는 GitHub API 접근 토큰
- **GitHub_Client**: GitHub REST API 호출과 App 인증을 담당하는 공용 구성 요소 (`github/` 패키지). P1 PR_Fetcher, Comment_Publisher, Feedback_Collector가 함께 쓴다
- **Target_Action**: 리뷰를 시작하는 `pull_request` 이벤트 action. `opened`, `synchronize`, `reopened` 중 하나
- **Required_Fields**: Webhook_Receiver가 `pull_request` payload에서 읽는 필드. 저장소 ID, `owner/repo`, PR 번호, head SHA, base SHA, installation ID
- **Review_Job**: 한 Dedupe_Key에 대한 리뷰 실행 작업. Review_Run 하나에 대응한다
- **Job_Runner**: Review_Job을 요청 처리 스레드와 분리된 작업자에서 실행하고, 실행 대기 중인 Review_Job을 Queue_Capacity까지 등록 순서대로 보관하는 구성 요소 (`webhook/` 패키지, T1)
- **Queue_Capacity**: 실행 대기 중인 Review_Job의 최대 수(대기열 상한). Configuration 항목이며 기본값 100, 허용 범위 1~1,000
- **Dedupe_Key**: `(저장소 ID, PR 번호, head SHA)` 조합. 저장소 ID는 GitHub 저장소의 숫자 ID이고, head SHA는 소문자 40자 16진수로 정규화한다
- **Review_Run**: 리뷰 실행 1회의 기록. Review_Run ID(1 이상 2^63−1 이하의 64비트 정수)로 식별한다. 실행 경로(Run_Trigger), 상태(Run_Status), 시도 번호, delivery ID, ReviewResult, 사용된 컨텍스트 파일 경로 목록, usage와 추정 비용, 단계별 시각(webhook 수신, 등록, 실행 시작, 리뷰 완료, 게시 완료, 종료), 오류 정보, Publish_Error를 가진다. 종료 시각은 Run_Status가 `succeeded`, `incomplete`, `failed`, `superseded` 중 하나로 바뀐 시각이다
- **Run_Trigger**: Review_Run의 실행 경로. `webhook`, `cli`, `actions` 중 하나
- **Run_Status**: Review_Run의 상태. `queued`, `running`, `succeeded`(ReviewResult `complete`), `incomplete`(ReviewResult `incomplete`), `failed`(ReviewResult를 만들지 못함), `superseded`(게시 시점에 PR head SHA가 바뀜) 중 하나
- **Active_Run**: Run_Status가 `failed`가 아닌 Review_Run. `superseded`도 Active_Run이다
- **Publish_Error**: Review_Run 게시 실패 기록. Publish_Error_Kind와 GitHub 상태 코드(없으면 null)로 이루어진다
- **Run_Error_Kind**: `failed` Review_Run의 오류 종류. `github_auth`, `github_api`, `llm_api`, `network`, `retry_after_too_long`, `all_chunks_failed`, `timeout`, `interrupted`, `head_moved`, `internal` 중 하나 (설계 제안값. 조회 API와 P3가 이 목록에 의존한다)
- **Publish_Error_Kind**: 게시 오류 종류. `github_auth`, `github_api`, `network`, `retry_after_too_long`, `store` 중 하나 (설계 제안값)
- **Ack_Reason**: Webhook_Receiver가 202 응답에 담는 사유. `event_ignored`, `action_ignored`, `repository_not_allowed`, `duplicate`, `max_attempts_reached` 중 하나 (설계 제안값)
- **Review_Store**: Review_Run과 Finding을 저장하고 조회하는 인터페이스 (`store/` 패키지, T2). 구현은 DB를 쓴다
- **Stored_Finding**: Review_Store에 저장된 Finding. P1 Finding의 모든 필드와 Review_Run 안의 순번, Finding_Fingerprint, 게시된 Line_Comment ID, Publish_Outcome, Feedback_State를 가진다
- **Publish_Outcome**: Stored_Finding의 게시 결과. `not_published`(기본값, 게시 전이거나 게시하지 않은 Review_Run), `line_comment_posted`, `line_comment_reused`, `summary_listed`, `below_threshold`, `duplicate_in_run`, `github_rejected`, `inline_disabled` 중 하나. Finding의 라인 판정(Inline_Eligible, Summary_Only)과 Summary_Only 사유와는 별개의 값이다
- **Comment_Publisher**: Review_Run 결과를 PR에 Summary_Comment와 Line_Comment로 게시하는 구성 요소 (`publish/` 패키지, T3)
- **Summary_Comment**: PR 대화 탭에 PR Lens가 PR마다 하나만 유지하는 이슈 코멘트. 본문에 Summary_Marker를 포함한다
- **Summary_Marker**: Summary_Comment를 식별하는 HTML 주석 문자열 `<!-- prlens:summary -->`
- **Line_Comment**: PR 리뷰 API로 특정 파일의 head 쪽 줄에 게시하는 코멘트. 본문에 Finding_Marker를 포함한다
- **Finding_Fingerprint**: Normalized_Repo_Path로 정규화한 `file`, `category`, `basis.ref`(없으면 빈 문자열), 앞뒤 공백을 제거한 `message`를 구분자와 함께 이어 붙인 문자열의 SHA-256 16진수 값. `line`은 포함하지 않는다
- **Finding_Marker**: Line_Comment를 식별하는 HTML 주석 문자열 `<!-- prlens:finding:{Finding_Fingerprint} -->`
- **Severity_Threshold**: 게시할 Finding의 최소 심각도. 심각도 순서는 `blocker` > `major` > `minor` > `nit`이고 기본값은 `minor`
- **Publishable_Finding**: `severity`가 Severity_Threshold 이상인 Finding
- **Feedback_Collector**: Line_Comment의 리액션을 읽어 Stored_Finding의 Feedback_State를 갱신하는 구성 요소 (`publish/` 패키지, T3)
- **Feedback_State**: Stored_Finding의 채택/기각 상태. `adopted`, `rejected`, `conflicted`, `none` 중 하나
- **Allowed_Repository_List**: 리뷰를 허용하는 `owner/repo` 목록. Configuration 항목이며 대소문자를 무시해 비교한다
- **Query_API**: 저장된 리뷰 데이터를 조회하는 REST API (T2)
- **Standard_Error_Response**: ADR 0002가 정의한 표준 에러 응답 형식 `ErrorResponse { code, message, details }`. `code`는 UPPER_SNAKE_CASE, `details`는 문자열 목록이다 ([ADR 0008](../../adr/0008-keep-api-conventions.md))

## Requirements

### Requirement 1: webhook 수신 (FR-7)

**User Story:** 개발자로서, PR을 열거나 커밋을 추가하면 리뷰가 자동으로 시작되기를 원한다. 그래야 CLI를 따로 실행하지 않아도 리뷰를 받는다.

#### Acceptance Criteria

1. THE Webhook_Receiver SHALL `POST /webhooks/github` 경로 하나로 GitHub App webhook 요청을 받는다
2. THE Webhook_Receiver SHALL 요청을 본문 크기 검사(3번) → 서명 검증(요구사항 2) → 이벤트·action 판정(4~6번) → payload 필드 검증(7~8번) → 허용 저장소 검사(요구사항 3) → 중복 판정(요구사항 5) → Review_Job 등록(요구사항 4) 순서로 처리하고, 응답이 정해진 첫 단계에서 이후 단계를 수행하지 않고 응답한다
3. IF 요청 본문이 25MB를 넘으면, THEN THE Webhook_Receiver SHALL 본문을 끝까지 읽지 않고 413을 응답한다
4. WHEN 서명 검증을 통과한 요청의 `X-GitHub-Event`가 `ping`이면, THE Webhook_Receiver SHALL payload 필드 검증과 허용 저장소 검사 전에 Review_Job을 등록하지 않고 200을 응답한다
5. WHEN 서명 검증을 통과한 요청의 `X-GitHub-Event`가 `ping`과 `pull_request`가 아니거나, `pull_request`의 `action`이 Target_Action과 `closed`가 아니면, THE Webhook_Receiver SHALL Review_Job을 등록하지 않고 202와 무시 사유를 응답한다
6. WHEN `action`이 `closed`인 `pull_request` 요청이 payload 필드 검증과 허용 저장소 검사를 통과하면, THE Webhook_Receiver SHALL Review_Job을 등록하지 않고 Feedback_Collector에 해당 PR의 Feedback_State 갱신을 요청하고(요구사항 15.3) 202를 응답한다
7. IF 서명 검증을 통과한 `pull_request` 요청의 본문을 JSON으로 해석할 수 없으면, THEN THE Webhook_Receiver SHALL Review_Job을 등록하지 않고 400과 해석 실패 표시를 응답하고 delivery ID와 해석 실패를 로그에 남긴다
8. IF 서명 검증을 통과한 `pull_request` payload에 Required_Fields 중 하나라도 없거나 head SHA가 40자 16진수가 아니면, THEN THE Webhook_Receiver SHALL Review_Job을 등록하지 않고 400과 누락되거나 잘못된 필드 이름을 응답하고 delivery ID와 해당 필드 이름을 로그에 남긴다
9. WHEN Target_Action 요청이 허용 저장소 검사를 통과하면, THE Webhook_Receiver SHALL payload의 Required_Fields로 중복 판정과 Review_Job 등록을 요청한다(요구사항 4, 5)
10. WHEN Target_Action 요청이 허용 저장소 검사를 통과하면, THE Webhook_Receiver SHALL 리뷰 완료를 기다리지 않고 Review_Job 등록 또는 중복 판정을 마친 뒤 수신 후 5초 이내에 202를 응답한다
11. WHEN Review_Job을 등록하면, THE Webhook_Receiver SHALL 요청의 `X-GitHub-Delivery` 값을 새 Review_Run의 delivery ID로 기록하고, 헤더가 없으면 null로 기록한다
12. IF 중복 판정이나 Review_Job 등록 중 Review_Store 오류가 발생하면, THEN THE Webhook_Receiver SHALL 해당 요청의 Review_Run을 남기지 않고 503과 Standard_Error_Response 형식의 본문을 응답하고 delivery ID와 P1 요구사항 19.4로 치환한 오류 메시지를 로그에 남긴다
13. WHILE PR이 draft 상태인 동안, THE Webhook_Receiver SHALL Target_Action 요청을 draft가 아닌 PR과 같은 규칙으로 처리한다 (D-3 제안값)

### Requirement 2: webhook 서명 검증 (FR-7, 보안)

**User Story:** 팀원으로서, GitHub가 보낸 요청만 리뷰를 시작하기를 원한다. 그래야 외부인이 가짜 이벤트로 API 비용을 쓰게 할 수 없다.

#### Acceptance Criteria

1. THE Signature_Verifier SHALL 요청 원문 바이트(문자 인코딩 변환과 JSON 재직렬화 전)에 Webhook_Secret으로 HMAC-SHA256을 계산하고, `sha256=` 접두어 뒤의 16진수 값과 비교한다
2. THE Signature_Verifier SHALL 서명 비교를 입력 길이에만 시간이 좌우되는 상수 시간 비교로 수행한다
3. THE Signature_Verifier SHALL 헤더의 16진수 값을 대소문자를 무시해 해석한다
4. IF `X-Hub-Signature-256` 헤더가 없거나(SHA-1 `X-Hub-Signature` 헤더만 있는 경우 포함), `sha256=` 접두어가 없거나, 16진수 64자가 아니거나, 계산값과 다르면, THEN THE Webhook_Receiver SHALL payload를 해석하지 않고 Review_Job을 등록하지 않고 실패 종류와 관계없이 같은 본문의 401을 응답한다
5. IF 서명 검증에 실패하면, THEN THE Webhook_Receiver SHALL delivery ID(헤더가 없으면 `없음`), 실패 종류(`header_missing`, `prefix_missing`, `malformed`, `mismatch` 중 하나), 수신 시각만 로그에 남긴다
6. THE Webhook_Receiver SHALL 서명 검증을 본문 크기 검사(요구사항 1.3) 다음에, 이벤트·action 판정, payload 해석, 허용 저장소 검사, 중복 판정, Review_Job 등록보다 먼저 수행한다
7. FOR ALL 요청 본문 B와 비밀값 S, S로 계산한 서명을 붙인 B SHALL 검증을 통과한다 (round-trip 속성)
8. FOR ALL 요청 본문 B와 비밀값 S, B의 바이트 하나 이상을 바꾸거나 다른 비밀값 S'(S' ≠ S)로 서명한 요청 SHALL 검증에 실패한다 (오류 조건 속성)

### Requirement 3: 리뷰 대상 저장소 한정 (보안)

**User Story:** 연구회 팀으로서, 연구회 저장소와 허용한 공개 저장소만 자동 리뷰되기를 원한다. 그래야 회사 코드가 외부 LLM으로 보내지지 않는다.

#### Acceptance Criteria

1. THE Webhook_Receiver SHALL payload의 `owner/repo`를 Allowed_Repository_List와 대소문자를 무시해 비교한다
2. IF Target_Action 또는 `closed` 요청의 `owner/repo`가 Allowed_Repository_List에 없으면, THEN THE Webhook_Receiver SHALL Review_Job 등록과 Feedback_Collector 갱신 요청 없이, GitHub API와 Claude API를 호출하지 않고 202와 사유 `repository_not_allowed`를 응답하고 저장소 이름과 delivery ID를 로그에 남긴다
3. WHILE Allowed_Repository_List가 비어 있는 동안, THE Webhook_Receiver SHALL 모든 저장소를 허용하지 않은 것으로 판정한다
4. WHEN PR_Lens_Server가 시작될 때 Allowed_Repository_List가 비어 있으면, THE PR_Lens_Server SHALL 모든 webhook 리뷰가 거부된다는 경고를 로그에 남긴다
5. IF Allowed_Repository_List의 항목이 `owner/repo` 형식(P1 요구사항 1.3의 문자 규칙, 앞뒤 공백 없음)이 아니면, THEN THE Config_Loader SHALL 항목 값과 문제를 담은 오류를 출력하고 THE PR_Lens_Server SHALL 시작하지 않는다
6. IF Allowed_Repository_List의 항목 수가 1,000개를 넘으면, THEN THE Config_Loader SHALL 항목 수와 상한 1,000을 담은 오류를 출력하고 THE PR_Lens_Server SHALL 시작하지 않는다
7. WHEN Allowed_Repository_List에 대소문자만 다른 항목이 둘 이상 있으면, THE Config_Loader SHALL 해당 항목들을 하나의 항목으로 취급한다
8. THE Webhook_Receiver SHALL 허용 판정을 `owner/repo` 이름 비교 결과만으로 정하고, 저장소의 공개·비공개 여부(`private`)와 관계없이 같은 판정을 한다
9. FOR ALL `owner/repo` 문자열 R과 Allowed_Repository_List L, 허용 판정 결과 SHALL R과 L의 각 항목을 소문자로 바꾼 뒤 정확히 일치하는 항목이 있는지 판정한 결과와 같다 (모델 기반 속성)

### Requirement 4: 비동기 실행 (FR-8)

**User Story:** 개발자로서, webhook 응답이 리뷰 시간에 묶이지 않기를 원한다. 그래야 GitHub가 요청 시간 초과로 전달 실패를 기록하지 않는다.

#### Acceptance Criteria

1. WHEN Review_Job이 등록되면, THE Job_Runner SHALL Run_Status `queued`인 Review_Run을 webhook 수신 시각과 등록 시각과 함께 Review_Store에 저장한 뒤 webhook 응답과 분리된 작업자에서 Review_Job을 실행한다
2. WHEN Review_Job 실행을 시작하면, THE Job_Runner SHALL Run_Status를 `running`으로 바꾸고 실행 시작 시각을 기록한다
3. WHEN Review_Job을 실행하면, THE Job_Runner SHALL Installation_Token으로 P1의 PR_Fetcher, Context_Collector, Review_Engine을 Dedupe_Key의 head SHA 기준으로 실행한다. PR_Fetcher가 가져온 base SHA가 payload와 다르면 가져온 값으로 리뷰하고 저장하며 차이를 서버 로그에 남긴다 (G-2 제안)
4. WHEN Review_Engine이 ReviewResult를 만들면, THE Job_Runner SHALL ReviewResult를 저장하고 Run_Status를 ReviewResult가 `complete`이면 `succeeded`로, `incomplete`이면 `incomplete`로 바꾸고 리뷰 완료 시각과 종료 시각을 기록한 뒤 Comment_Publisher에 게시를 요청한다
5. IF Review_Job이 ReviewResult를 만들지 못하면(P1 요구사항 15.11의 설정, 인증, 네트워크, API 오류), THEN THE Job_Runner SHALL Run_Status를 `failed`로 바꾸고 Run_Error_Kind, P1 요구사항 19.4로 치환한 오류 메시지, 종료 시각을 기록하고 Comment_Publisher에 실패 게시를 요청한다(요구사항 11.7, `github_auth`는 요구사항 6.7). PR_Fetcher가 가져온 head SHA가 Dedupe_Key의 head SHA와 다르면 Claude API를 호출하지 않고 오류 종류 `head_moved`로 기록한다 (G-1 제안)
6. THE Job_Runner SHALL Configuration의 작업자 수(기본 2, 허용 1~8)만큼 Review_Job을 동시에 실행하고, 나머지 Review_Job은 등록 순서대로 대기시킨다
7. IF Review_Run이 `running`으로 바뀐 뒤 경과 시간(재시도 대기 시간 포함)이 Configuration의 작업 제한 시간(기본 600초, 허용 60~1,800초)을 넘으면, THEN THE Job_Runner SHALL 진행 중인 GitHub API와 Claude API 호출을 중단하고, Run_Status를 `failed`, 오류 종류를 `timeout`으로 기록하고, 그때까지 받은 usage를 저장하고(요구사항 8.2), Comment_Publisher에 실패 게시를 요청한다
8. WHEN PR_Lens_Server가 시작되면, THE Job_Runner SHALL Run_Status가 `running`인 Review_Run을 `failed`, 오류 종류 `interrupted`로 바꾸고, Run_Status가 `queued`인 Review_Run을 등록 순서대로 다시 실행 대기열에 넣는다 (`interrupted`는 요구사항 5.5의 실패 횟수에 포함)
9. THE Job_Runner SHALL Review_Job 하나의 실패나 예외가 다른 Review_Job의 실행과 Webhook_Receiver의 요청 처리에 영향을 주지 않게 한다
10. THE Job_Runner SHALL GitHub API와 Claude API 재시도에 P1 요구사항 21의 규칙(재시도 대상, 백오프, `retry-after`, 제한 시간)을 적용하고, 재시도 로그를 표준 오류 대신 서버 로그에 남기고, 재시도 대기 시간의 합(ms)을 Review_Run에 기록한다
11. WHEN Comment_Publisher가 Run_Status `succeeded` 또는 `incomplete`인 Review_Run의 게시에 성공하면, THE Job_Runner SHALL 해당 Review_Run의 게시 완료 시각을 기록한다 (실패 코멘트를 게시한 `failed` Review_Run에도 기록할지는 D-13)
12. THE Job_Runner SHALL 실행 대기 중인 Review_Job을 Queue_Capacity(기본 100, 허용 1~1,000)까지 보관한다
13. IF Review_Job 등록 시점에 실행 대기 중인 Review_Job 수가 Queue_Capacity에 이르렀으면, THEN THE Job_Runner SHALL 해당 요청의 Review_Run을 남기지 않고 등록을 거부하고, THE Webhook_Receiver SHALL 503과 Standard_Error_Response 형식의 본문을 응답하고 delivery ID와 현재 대기열 크기를 로그에 남긴다
14. WHEN PR_Lens_Server가 시작되면, THE Job_Runner SHALL Run_Status가 `succeeded` 또는 `incomplete`이고 게시 완료 시각이 null이고 Publish_Error가 없는 Review_Run을 Review_Engine 재실행 없이 게시만 하도록 대기열에 넣는다
15. THE Job_Runner SHALL 오류 종류가 `interrupted`인 Review_Run의 Dedupe_Key에 대한 새 Review_Job을 같은 Dedupe_Key의 webhook 재전송(요구사항 5.4) 또는 새 head SHA의 Target_Action 요청이 도착했을 때만 등록한다

### Requirement 5: 중복 방지 (FR-8)

**User Story:** 연구회 팀으로서, 같은 커밋이 여러 번 리뷰되지 않기를 원한다. 그래야 webhook 재전송이나 동시 이벤트 때문에 비용과 코멘트가 두 배가 되지 않는다.

#### Acceptance Criteria

1. THE Review_Store SHALL Run_Trigger가 `webhook` 또는 `actions`인 Review_Run에 대해 Dedupe_Key마다 Active_Run을 최대 하나만 저장한다
2. WHEN Target_Action 요청의 Dedupe_Key에 Active_Run이 이미 있으면, THE Webhook_Receiver SHALL 새 Review_Job을 등록하지 않고 202와 사유 `duplicate`, 기존 Review_Run ID를 응답한다
3. WHEN 같은 Dedupe_Key의 Target_Action 요청 여러 개가 동시에 도착하면, THE Review_Store SHALL 저장소 수준의 원자적 제약으로 Review_Run 하나만 생성하고, 생성하지 못한 요청에는 "이미 존재" 결과와 기존 Review_Run ID를 반환한다
4. WHEN Dedupe_Key의 Review_Run이 모두 `failed`이고 `failed` Review_Run 수가 Configuration의 최대 시도 횟수보다 작으면, THE Webhook_Receiver SHALL 같은 Dedupe_Key의 요청(재전송 포함)으로 새 Review_Job을 등록하고 시도 번호를 이전 최대값보다 1 크게 기록한다
5. IF Dedupe_Key의 `failed` Review_Run 수(오류 종류 `timeout`, `interrupted` 포함)가 Configuration의 최대 시도 횟수(기본 3, 허용 1~10) 이상이면, THEN THE Webhook_Receiver SHALL 새 Review_Job을 등록하지 않고 202와 사유 `max_attempts_reached`를 응답한다
6. THE Webhook_Receiver SHALL `opened`, `synchronize`, `reopened`를 구분하지 않고 Dedupe_Key만으로 중복을 판정한다 (예: 같은 head SHA로 PR을 닫았다 다시 열면 다시 리뷰하지 않는다)
7. THE Webhook_Receiver SHALL head SHA를 소문자로 정규화한 뒤 Dedupe_Key를 만든다
8. IF Review_Run의 Run_Trigger가 `cli`이면, THEN THE Review_Store SHALL 해당 Review_Run을 시도 번호 1로 실행마다 새로 저장하고, 중복 판정과 다른 Review_Run의 시도 번호 계산에서 제외한다
9. WHEN Review_Store가 "이미 존재" 결과를 반환하면, THE Webhook_Receiver SHALL 2번과 같이 202와 사유 `duplicate`, 반환된 기존 Review_Run ID를 응답한다
10. WHEN Dedupe_Key에 Run_Trigger가 `webhook` 또는 `actions`인 Review_Run이 없으면, THE Webhook_Receiver SHALL 새 Review_Job을 시도 번호 1로 등록한다
11. THE Review_Store SHALL Run_Status가 `superseded`인 Review_Run을 중복 판정에서 Active_Run으로 취급한다
12. FOR ALL Target_Action 요청 순서열(같은 Dedupe_Key의 반복·동시 전달 포함, `failed` 없음), 처리 후 각 Dedupe_Key의 `webhook`·`actions` Active_Run 수 SHALL 정확히 1이다 (멱등 속성)
13. FOR ALL Target_Action 요청 집합, 도착 순서를 바꿔 처리해도 생성되는 Dedupe_Key 집합 SHALL 같다 (합류 속성)

### Requirement 6: GitHub App 인증

**User Story:** 운영 담당자로서, 서버가 개인 PAT 대신 GitHub App 권한으로 동작하기를 원한다. 그래야 설치한 저장소에만 필요한 권한으로 접근한다.

#### Acceptance Criteria

1. THE GitHub_Client SHALL 환경변수 `GITHUB_APP_ID`와 App_Private_Key로 유효 기간 10분 이하의 JWT를 만들어 payload의 installation ID에 대한 Installation_Token을 발급받는다
2. THE GitHub_Client SHALL Installation_Token을 installation ID별로 보관하고 만료 5분 전까지 재사용한다
3. WHEN GitHub API 요청을 보내기 전에 Installation_Token의 남은 유효 시간이 5분 미만이면, THE GitHub_Client SHALL Installation_Token을 새로 발급받은 뒤 요청을 보낸다
4. IF GitHub API가 401을 반환하면, THEN THE GitHub_Client SHALL Installation_Token을 한 번 다시 발급받아 요청을 한 번 다시 보낸다
5. IF 다시 보낸 요청도 401을 반환하면, THEN THE GitHub_Client SHALL 추가 발급 없이 인증 오류를 반환한다
6. IF Installation_Token 발급이 P1 요구사항 21의 재시도 후에도 실패하거나 GitHub_Client가 이 요구사항 5번(6.5)의 인증 오류를 반환하면, THEN THE Job_Runner SHALL Run_Status를 `failed`, 오류 종류를 `github_auth`로 기록한다
7. WHEN 오류 종류가 `github_auth`인 Review_Run의 실패 게시를 요청받으면, THE Comment_Publisher SHALL 실패 코멘트 게시를 시도하지 않고 Review_Run의 Publish_Error 종류를 `github_auth`로 기록한다
8. IF PR_Lens_Server 시작 시 `GITHUB_APP_ID`, App_Private_Key, Webhook_Secret, `ANTHROPIC_API_KEY`, `PRLENS_DB_URL` 중 하나라도 없거나 빈 문자열 또는 공백 문자로만 이루어져 있거나 App_Private_Key가 PEM 형식이 아니면, THEN THE PR_Lens_Server SHALL 누락되거나 잘못된 환경변수 이름(값 제외)을 모두 담은 오류를 출력하고 0이 아닌 종료 코드로 시작을 중단한다
9. THE PR_Lens_Server SHALL GitHub App에 Pull requests 읽기·쓰기, Contents 읽기, Metadata 읽기 권한과 `pull_request` 이벤트 구독만 요구하고, 필요한 권한과 이벤트 목록을 설치 문서에 적는다

### Requirement 7: 결과 저장 스키마 (FR-9)

**User Story:** 연구회 팀으로서, 모든 리뷰 실행과 지적이 DB에 남기를 원한다. 그래야 P3 화면과 5주차 발표 지표(채택률, 비용)를 집계할 수 있다.

#### Acceptance Criteria

1. THE Review_Store SHALL `repository`, `pull_request`, `review_run`, `finding` 네 테이블에 데이터를 저장한다
2. THE Review_Store SHALL `repository`에 GitHub 저장소 ID, owner, 이름을 저장하고 GitHub 저장소 ID를 유일 키로 둔다
3. WHEN 이미 저장된 GitHub 저장소 ID에 대해 다른 owner 또는 이름으로 저장을 요청받으면, THE Review_Store SHALL 기존 `repository` 행의 owner와 이름을 새 값으로 갱신한다
4. THE Review_Store SHALL `pull_request`에 저장소 참조, PR 번호, 제목, 마지막으로 본 head SHA를 저장하고 `(저장소, PR 번호)`를 유일 키로 둔다
5. WHEN Review_Run을 생성하면, THE Review_Store SHALL 해당 `pull_request` 행의 제목과 마지막으로 본 head SHA를 해당 Review_Run의 값으로 갱신한다
6. THE Review_Store SHALL `review_run`에 Review_Run ID, PR 참조, head SHA, base SHA, Run_Trigger, Run_Status, 시도 번호, delivery ID(없으면 null), installation ID(Run_Trigger가 `webhook`일 때만, 재시작 복구에 사용, G-3 제안), Review_Mode, Changed_Line_Count, 완전성 상태, Incomplete_Reason 목록, 불완전 상세 정보, `summary`, `excludedFiles`, `excludedFileDetails`(P1 요구사항 3.7), 사용된 컨텍스트 파일의 경로와 출처 종류 목록, 모델 이름, effort, 입력·출력·캐시 쓰기·캐시 읽기 토큰 수, 추정 비용(USD, 알 수 없으면 null), webhook 수신·등록·실행 시작·리뷰 완료·게시 완료·종료 시각(UTC, 해당 없으면 null), 재시도 대기 시간 합(ms), 오류 종류와 치환된 오류 메시지, Publish_Error(게시 오류 종류와 GitHub 상태 코드), Summary_Comment ID를 저장한다
7. THE Review_Store SHALL `finding`에 Review_Run 참조, Review_Run 안의 순번(0부터), P1 Finding의 모든 필드(`file`, `line`, `severity`, `category`, `message`, `suggestion`, `basis.type`, `basis.ref`, Demotion_Record, 라인 판정, Summary_Only 사유), Finding_Fingerprint, 게시된 Line_Comment ID(없으면 null), Publish_Outcome(기본 `not_published`), Feedback_State(기본 `none`), Feedback_State 갱신 시각을 저장한다
8. WHEN Stored_Finding의 Publish_Outcome을 기록하면, THE Review_Store SHALL 해당 Stored_Finding의 라인 판정과 Summary_Only 사유를 저장된 원래 값으로 유지한다
9. THE Review_Store SHALL 컨텍스트 파일의 내용과 diff 원문을 저장하지 않는다
10. THE Review_Store SHALL 스키마를 버전 관리되는 마이그레이션 스크립트로 만들고, PR_Lens_Server 시작 시와 `--save` 실행 시작 시(외부 API 호출 전) 적용되지 않은 마이그레이션을 순서대로 적용한다
11. IF PR_Lens_Server 시작 중 마이그레이션이 실패하면, THEN THE Review_Store SHALL 실패한 마이그레이션을 되돌리고 THE PR_Lens_Server SHALL 실패한 마이그레이션 버전과 DB 비밀번호가 치환된 오류를 출력하고 0이 아닌 종료 코드로 시작을 중단한다
12. IF `--save` 실행 중 마이그레이션이 실패하면, THEN THE PR_Lens_CLI SHALL 외부 API 호출 전에 실패한 마이그레이션 버전과 DB 비밀번호가 치환된 오류를 표준 오류에 출력하고 종료 코드 2로 종료한다
13. WHEN ReviewResult와 Stored_Finding을 저장하면, THE Review_Store SHALL `review_run` 한 행과 해당 `finding` 행 전부를 하나의 트랜잭션으로 저장해 일부만 저장된 상태를 남기지 않는다
14. IF DB 연결이나 저장이 실패하면, THEN THE Review_Store SHALL 트랜잭션을 되돌리고 DB 비밀번호가 치환된 저장 오류를 반환한다
15. IF Job_Runner의 ReviewResult 저장이 실패하면, THEN THE Job_Runner SHALL Review_Run을 Run_Status `running`으로 둔 채 치환된 저장 오류를 서버 로그에 남기고, 해당 Review_Run은 다음 서버 시작 시 요구사항 4.8에 따라 `interrupted`로 바뀐다
16. FOR ALL 유효한 ReviewResult와 사용된 컨텍스트 파일 목록, Review_Store에 저장한 뒤 다시 읽은 결과 SHALL Finding 순서, 추정 비용, 완전성 정보, 라인 판정, Demotion_Record, Publish_Outcome을 포함한 모든 필드에서 원래 값과 같다 (round-trip 속성, P1 요구사항 17.8의 값 동등성 사용)
17. FOR ALL 유효한 ReviewResult, 한글, 따옴표, 역슬래시, 줄바꿈, 이모지를 포함한 문자열 필드 SHALL 저장과 조회 후 코드 포인트 단위로 원래 문자열과 같다. 단, DB가 저장할 수 없는 U+0000은 저장할 때 U+FFFD로 바꾸고 경고를 남기며 이 속성의 대상에서 뺀다 (G-4 제안)

### Requirement 8: 비용과 usage 기록 (PLAYBOOK 지표)

**User Story:** 연구회 팀으로서, 실행마다 토큰과 비용이 DB에 남기를 원한다. 그래야 PR당 비용을 PLAYBOOK 5장 방식으로 집계할 수 있다.

#### Acceptance Criteria

1. WHEN Review_Run이 `succeeded`, `incomplete`, `superseded`로 끝나면, THE Review_Store SHALL P1 요구사항 20.1~20.2의 방식으로 계산한 usage와 추정 비용을 저장하고, 모델 가격을 알 수 없으면 토큰 수를 저장하고 추정 비용을 null로 저장한다
2. WHEN Review_Run이 `failed`로 끝나고 실패 전까지 Claude API 응답을 하나 이상 받았으면, THE Review_Store SHALL 재시도한 호출을 포함해 받은 모든 Claude API 응답의 usage 합과 그 합으로 계산한 추정 비용(모델 가격을 알 수 없으면 null)을 저장하고, 응답을 받기 전에 중단된 호출(`timeout`, `interrupted`)은 합에서 제외한다
3. WHEN Review_Run이 Claude API 응답을 하나도 받지 못하고 `failed`로 끝나면, THE Review_Store SHALL 네 가지 토큰 수와 추정 비용을 0으로 저장한다
4. IF 추정 비용이 Configuration의 1회 리뷰 비용 상한(P1 요구사항 18.3의 항목)을 넘으면, THEN THE Job_Runner SHALL 리뷰와 게시를 중단하지 않고 추정 비용과 상한을 담은 경고를 서버 로그에 남기고, THE Comment_Publisher SHALL Run_Status가 `succeeded`, `incomplete`, `failed`인 Review_Run의 Summary_Comment에 비용 초과 표시, 추정 비용, 상한을 포함한다
5. FOR ALL Review_Run이 100개 이하인 PR(요구사항 10.5의 상한, G-8 제안), Query_API의 Review_Run 목록(요구사항 10.3)이 반환한 추정 비용의 합(null 제외) SHALL Review_Store에 저장된 해당 PR Review_Run 추정 비용의 합(null 제외)과 같고, 저장된 모든 토큰 수와 추정 비용은 null이거나 0 이상이다 (불변 속성)

### Requirement 9: CLI `--save` (FR-9)

**User Story:** 개발자로서, 로컬 CLI 실행 결과도 DB에 저장하고 싶다. 그래야 webhook이 없는 상황의 리뷰도 지표에 포함된다.

#### Acceptance Criteria

1. WHEN `prlens review <PR URL> --save`로 `--publish` 없이 실행되면(함께 쓰면 요구사항 20.3에 따라 Run_Trigger `actions`로 등록), THE PR_Lens_CLI SHALL ReviewResult를 만든 뒤 Run_Trigger `cli`, 시도 번호 1, delivery ID null, ReviewResult가 `complete`이면 Run_Status `succeeded`이고 `incomplete`이면 `incomplete`, 등록 시각은 CLI 시작 시각, webhook 수신 시각은 null인 Review_Run으로 Review_Store에 저장하고, 저장한 Review_Run ID를 표준 오류에 한 줄로 출력한다
2. THE PR_Lens_CLI SHALL DB 접속 정보를 환경변수 `PRLENS_DB_URL`, `PRLENS_DB_USER`, `PRLENS_DB_PASSWORD`에서만 읽는다
3. IF `--save`가 지정되었고 `PRLENS_DB_URL`이 없거나 빈 문자열이거나 공백 문자로만 이루어져 있으면, THEN THE PR_Lens_CLI SHALL 모든 외부 API 호출과 DB 연결 전에 필요한 환경변수 이름을 담은 오류 메시지를 표준 오류에 출력하고 종료 코드 2로 종료한다
4. WHEN `--save`로 저장하면, THE PR_Lens_CLI SHALL `--save` 없이 실행한 경우와 같은 표준 출력 내용을 출력한다
5. IF `--save` 저장이 실패하면, THEN THE PR_Lens_CLI SHALL 표준 출력에는 ReviewResult를 그대로 출력하고, 저장 오류를 표준 오류에 출력하고, D-5의 종료 코드로 종료한다
6. WHILE `--save` 없이 실행되는 동안, THE PR_Lens_CLI SHALL DB에 연결하지 않는다
7. WHEN `--save` 저장이 성공하면, THE PR_Lens_CLI SHALL `--save` 없이 실행한 경우와 같은 종료 코드(P1 요구사항 15.9~15.10, 16.7~16.8의 규칙)로 종료한다
8. IF `--save` 실행 시작 시 DB 연결이 실패하면, THEN THE PR_Lens_CLI SHALL 모든 외부 API 호출 전에 DB 비밀번호가 치환된 연결 오류를 표준 오류에 출력하고 종료 코드 2로 종료한다 (마이그레이션 실패는 요구사항 7.12)
9. IF `--save`가 지정되었고 ReviewResult를 만들지 못하면(P1 요구사항 15.11), THEN THE PR_Lens_CLI SHALL Run_Status `failed`, 오류 종류, P1 요구사항 19.4로 치환한 오류 메시지, 그때까지 받은 usage(요구사항 8.2~8.3)를 담은 Review_Run을 Review_Store에 저장하고 종료 코드 2로 종료한다. PR 조회 자체가 실패해 저장소 ID와 head SHA를 알 수 없으면 저장하지 않고 그 사실을 표준 오류에 출력한다 (G-6 제안)

### Requirement 10: 조회 REST API 초안 (FR-12 일부)

**User Story:** P3 웹 개발자로서, 저장된 리뷰 데이터를 조회하는 API와 명세를 3주차에 먼저 받고 싶다. 그래야 4주차에 화면 개발을 바로 시작할 수 있다.

#### Acceptance Criteria

1. THE Query_API SHALL `GET /api/v1/repositories`로 저장소 목록(owner, 이름, PR 수)을 소문자로 바꾼 owner 오름차순, 같으면 소문자로 바꾼 이름 오름차순으로 반환한다
2. THE Query_API SHALL `GET /api/v1/repositories/{owner}/{repo}/pulls`로 PR 목록(번호, 제목, 마지막 Review_Run의 Run_Status, 등록 시각, 심각도별 Finding 수)을 마지막 Review_Run 등록 시각 내림차순, 같으면 PR 번호 내림차순으로 반환하고, 심각도별 Finding 수는 Severity_Threshold와 관계없이 마지막 Review_Run의 모든 Stored_Finding으로 센다
3. THE Query_API SHALL `GET /api/v1/repositories/{owner}/{repo}/pulls/{number}/runs`로 PR의 Review_Run 목록(ID, head SHA, Run_Trigger, Run_Status, 시도 번호, Review_Mode, 완전성 상태, Incomplete_Reason, usage, 추정 비용, effort, Run_Error_Kind(`failed`일 때), Publish_Error(있을 때), webhook 수신·등록·실행 시작·리뷰 완료·게시 완료·종료 시각)을 등록 시각 내림차순, 같으면 시도 번호 내림차순으로 반환한다
4. THE Query_API SHALL `GET /api/v1/runs/{runId}/findings`로 Review_Run의 Stored_Finding 목록을 순번 오름차순으로, 각 항목에 P1 Finding 필드, 라인 판정, Demotion_Record, Feedback_State, Line_Comment ID를 담아 반환한다
5. THE Query_API SHALL 목록 응답마다 정렬 순서의 앞에서부터 최대 100개 항목과, 잘라내기 전 전체 항목 수를 반환한다 (페이지네이션은 P3 범위)
6. THE Query_API SHALL `owner`와 `repo`를 대소문자를 무시해 조회하고, 저장소 이름 변경으로 같은 이름(대소문자 무시)의 저장소가 둘 이상이면 가장 최근에 갱신된 저장소를 쓴다 (G-9 제안)
7. IF 조회 대상 저장소, PR, Review_Run이 없으면, THEN THE Query_API SHALL 404와 Standard_Error_Response 형식의 본문을 반환한다
8. IF 경로 변수의 형식이 잘못되면(`owner`나 `repo`가 P1 요구사항 1.3의 문자 규칙을 어김, `number`가 양의 정수가 아님, `runId`가 Review_Run ID 범위(1 이상 2^63−1 이하)의 정수가 아님), THEN THE Query_API SHALL 400과 잘못된 경로 변수 이름을 담은 Standard_Error_Response 형식의 본문을 반환한다
9. THE Query_API SHALL Query_API의 모든 엔드포인트, 요청 변수, 응답 스키마, 오류 응답을 기술한 OpenAPI 3 문서를 저장소의 `docs/api/openapi.yaml`로 두고, CI에서 실제 응답이 OpenAPI 문서의 스키마를 따르는지 검사한다
10. THE Query_API SHALL 응답 JSON의 필드 이름과 열거형 값을 P1 Result_Codec의 JSON 직렬화와 같은 이름으로 쓴다
11. THE PR_Lens_Server SHALL Configuration의 서버 바인딩 주소(기본 `127.0.0.1`)에 바인딩하고, 시작 로그에 Query_API가 인증 없이 동작한다는 경고를 남긴다
12. IF 서버 바인딩 주소가 루프백 주소가 아니면, THEN THE PR_Lens_Server SHALL 시작 로그에 Query_API가 인증 없이 외부에 노출된다는 경고를 추가로 남긴다
13. WHEN Review_Run에 Stored_Finding이 0개이면(`failed` Review_Run 포함), THE Query_API SHALL `GET /api/v1/runs/{runId}/findings`에 200과 빈 목록, 전체 항목 수 0을 반환한다
14. IF 조회 중 Review_Store 오류가 발생하면, THEN THE Query_API SHALL 500과 DB 비밀번호가 치환된 Standard_Error_Response 형식의 본문을 반환한다
15. FOR ALL 저장된 Review_Run, `GET /api/v1/runs/{runId}/findings`가 반환한 Finding 목록 SHALL Review_Store에 저장한 Stored_Finding 목록과 순서와 필드가 같다 (round-trip 속성)

### Requirement 11: 요약 코멘트 게시와 갱신 (FR-10)

**User Story:** 리뷰어로서, PR마다 PR Lens 요약 코멘트가 하나만 있고 커밋을 추가하면 그 코멘트가 갱신되기를 원한다. 그래야 코멘트가 쌓여 대화가 묻히지 않는다.

#### Acceptance Criteria

1. WHEN Review_Run이 `succeeded` 또는 `incomplete`로 끝나면, THE Comment_Publisher SHALL PR의 이슈 코멘트 중 게시 주체(현재 GitHub API 인증 주체. PR_Lens_Server에서는 PR Lens GitHub App, `--publish` 실행에서는 `GITHUB_TOKEN`의 계정)가 작성하고 Summary_Marker를 포함한 코멘트를 찾아, 있으면 본문을 갱신하고 없으면 새로 만든다
2. WHEN 게시 주체가 작성하고 Summary_Marker를 가진 코멘트가 둘 이상이면, THE Comment_Publisher SHALL 가장 먼저 만들어진 코멘트를 갱신하고 나머지는 삭제하지 않는다
3. THE Comment_Publisher SHALL Summary_Comment 본문에 Summary_Marker, 리뷰한 head SHA 앞 7자, Run_Status, `summary`, `blocker`·`major`·`minor`·`nit` 순서의 심각도별 Finding 수, Severity_Threshold 미만이라 게시하지 않은 Finding 수(요구사항 13.2), 라인 밖 지적 목록, 제외 파일 수, 사용된 컨텍스트 파일 수, 입력·출력·캐시 쓰기·캐시 읽기 토큰 수, 추정 비용, 비용 초과 표시(요구사항 8.4, 해당할 때)를 포함한다. 라인 밖 지적 목록은 Summary_Only인 Publishable_Finding과 Publish_Outcome이 `github_rejected` 또는 `inline_disabled`인 Stored_Finding을 순번 오름차순으로 모은 하나의 목록이고, 각 항목에 파일, 줄, 심각도, 메시지, 근거, 사유(Summary_Only 사유 또는 Publish_Outcome)를 담는다
4. WHEN Review_Run이 `incomplete`이면, THE Comment_Publisher SHALL Summary_Comment 본문 첫 부분에 불완전 경고와 모든 Incomplete_Reason을 포함한다 (P1 요구사항 16.3과 같은 정보)
5. WHEN Review_Mode가 요약 전용 모드이면, THE Comment_Publisher SHALL Summary_Comment에 "PR을 나누세요" 안내, Changed_Line_Count, 기준값(3×Size_Limit)을 포함한다
6. THE Comment_Publisher SHALL Summary_Comment ID를 Review_Run에 저장한다
7. WHEN Review_Run이 `failed`로 끝나면, THE Comment_Publisher SHALL Summary_Comment를 1번과 같은 방식으로 만들거나 갱신해 head SHA 앞 7자, 실패 표시, 오류 종류를 담고, 이전 성공 리뷰의 Finding 목록은 담지 않는다
8. IF Summary_Comment 본문이 65,536자를 넘으면, THEN THE Comment_Publisher SHALL 라인 밖 지적 목록에서 순번이 가장 큰 항목부터 빼고 생략한 개수를 표시하고, 목록을 모두 빼도 65,536자를 넘으면 `summary`를 잘라 잘림 표시를 붙여 65,536자 이하로 게시한다
9. IF Summary_Comment 게시가 P1 요구사항 21의 재시도 후에도 실패하면, THEN THE Comment_Publisher SHALL Review_Run의 Run_Status를 바꾸지 않고 게시 오류 종류와 GitHub 상태 코드를 Review_Run의 Publish_Error로 기록한다
10. THE Comment_Publisher SHALL Summary_Comment와 Line_Comment 본문에 Secret_Value를 포함하지 않고, P1 요구사항 19.4의 치환을 적용한다
11. WHEN Review_Run을 게시하면, THE Comment_Publisher SHALL Line_Comment 게시(요구사항 12)를 먼저 마친 뒤 Summary_Comment를 게시해, 게시 거부 결과(요구사항 12.8~12.10)를 라인 밖 지적 목록에 반영한다
12. WHEN Summary_Comment 게시에 성공하면, THE Comment_Publisher SHALL 라인 밖 지적 목록에 속한 Summary_Only Publishable_Finding의 Publish_Outcome을 `summary_listed`로 기록한다
13. THE PR_Lens_Server SHALL 같은 PR에 webhook 경로와 GitHub Actions 경로(요구사항 20)를 함께 쓰면 게시 주체가 달라 Summary_Comment가 둘 생길 수 있다는 제한을 설치 문서에 적는다
14. FOR ALL 같은 PR에 대한 게시 요청 순서열(길이 1 이상, 실제로 Summary_Comment를 게시한 요청이 하나 이상, 처리 전 게시 주체의 Summary_Marker 코멘트가 0개 또는 1개), 처리 후 게시 주체가 작성하고 Summary_Marker를 가진 코멘트 수 SHALL 1이고 그 본문은 마지막으로 게시한 Review_Run의 내용과 같다 (멱등 속성)
15. IF Summary_Comment 생성 또는 PR 리뷰 생성 요청이 GitHub에서 처리됐는지 알 수 없는 방식(5xx, 연결 실패, 제한 시간 초과)으로 실패하면, THEN THE Comment_Publisher SHALL 같은 요청을 그대로 다시 보내지 않고 게시 주체의 마커를 다시 조회해 해당 코멘트가 없을 때만 다시 보낸다 (재시도로 코멘트가 중복되지 않게 한다)

### Requirement 12: 라인 코멘트 게시 (FR-10)

**User Story:** 개발자로서, 줄을 특정할 수 있는 지적은 해당 줄에 코멘트로 보고 싶다. 그래야 어떤 코드에 대한 지적인지 바로 안다.

#### Acceptance Criteria

1. WHERE 라인 코멘트 게시가 켜져 있으면, WHEN Review_Run이 `succeeded` 또는 `incomplete`로 끝나면, THE Comment_Publisher SHALL Inline_Eligible인 Publishable_Finding만 Review_Run의 head SHA와 head 쪽(`RIGHT`) 줄 번호로 Line_Comment를 게시한다
2. THE Comment_Publisher SHALL 한 Review_Run의 Line_Comment를 PR 리뷰 1개(이벤트 `COMMENT`)로 묶어 게시한다
3. THE Comment_Publisher SHALL Line_Comment 본문에 Finding_Marker, 심각도, 카테고리, 메시지, 제안(있을 때), 근거 종류와 경로(있을 때), 채택/기각 리액션 안내(요구사항 15)를 포함한다
4. WHEN PR에 같은 Finding_Fingerprint의 Finding_Marker를 가진 Line_Comment 중 게시 주체(요구사항 11.1)가 작성한 것이 이미 있으면, THE Comment_Publisher SHALL 해당 Finding의 Line_Comment를 새로 게시하지 않고 기존 Line_Comment ID와 Publish_Outcome `line_comment_reused`를 Stored_Finding에 기록한다
5. WHEN 한 Review_Run 안에서 Finding_Fingerprint가 같은 Publishable_Finding이 둘 이상이면, THE Comment_Publisher SHALL 순번이 가장 작은 Finding만 게시(또는 4번에 따라 재사용)하고, 나머지 Stored_Finding에는 순번이 가장 작은 Finding과 같은 Line_Comment ID와 Publish_Outcome `duplicate_in_run`을 기록한다
6. WHEN Line_Comment를 새로 게시하면, THE Comment_Publisher SHALL 게시한 Line_Comment ID와 Publish_Outcome `line_comment_posted`를 해당 Stored_Finding에 저장한다
7. THE Comment_Publisher SHALL 이전 Review_Run이 게시한 Line_Comment를 수정하거나 삭제하지 않는다
8. IF GitHub PR 리뷰 API가 리뷰 전체를 422로 거부하면, THEN THE Comment_Publisher SHALL 응답에서 식별할 수 있는 거부 원인 Finding을 빼고 나머지로 리뷰를 한 번 다시 게시한다
9. IF 422 응답에서 거부 원인 Finding을 식별할 수 없거나 다시 게시한 리뷰도 422로 거부되면, THEN THE Comment_Publisher SHALL 해당 Review_Run의 Line_Comment를 게시하지 않는다
10. WHEN 8~9번 때문에 게시되지 않은 Finding이 있으면, THE Comment_Publisher SHALL 게시되지 않은 모든 Finding의 Publish_Outcome을 `github_rejected`로 기록하고 라인 밖 지적 목록(요구사항 11.3)에 포함한다
11. IF PR 리뷰 게시가 P1 요구사항 21의 재시도 후에도 422가 아닌 오류로 실패하면, THEN THE Comment_Publisher SHALL Run_Status를 바꾸지 않고 게시 오류 종류와 GitHub 상태 코드를 Publish_Error로 기록하고, 해당 Finding의 Line_Comment ID를 null, Publish_Outcome을 `not_published`로 두고, Summary_Comment에 게시하지 못한 Finding 수를 한 줄로 표시하고(G-11 제안), Summary_Comment 게시를 계속한다
12. WHEN Inline_Eligible인 Publishable_Finding이 0개이면, THE Comment_Publisher SHALL PR 리뷰를 만들지 않는다
13. WHERE 라인 코멘트 게시가 꺼져 있으면, THE Comment_Publisher SHALL Inline_Eligible인 Publishable_Finding의 Publish_Outcome을 `inline_disabled`로 기록한다
14. FOR ALL Review_Run, 같은 Review_Run을 두 번 게시한 뒤의 게시 주체 Line_Comment 집합 SHALL 한 번 게시한 뒤의 집합과 같다 (멱등 속성)
15. FOR ALL 게시된 Line_Comment, 대응하는 Finding SHALL Inline_Eligible이고 Publishable_Finding이다 (불변 속성)

### Requirement 13: 심각도 하한 (PRD 9장)

**User Story:** 개발자로서, 사소한 지적 때문에 PR이 코멘트로 덮이지 않기를 원한다. 그래야 도그푸딩을 노이즈 때문에 그만두지 않는다.

#### Acceptance Criteria

1. THE Comment_Publisher SHALL Configuration의 Severity_Threshold(기본 `minor`, 허용 `blocker`, `major`, `minor`, `nit`)를 게시 판정에 사용한다
2. THE Comment_Publisher SHALL Severity_Threshold 미만인 Finding을 Line_Comment와 라인 밖 지적 목록에 게시하지 않고, Summary_Comment에 개수를 0개일 때도 표시하고, 해당 Stored_Finding의 Publish_Outcome을 `below_threshold`로 기록한다
3. THE Review_Store SHALL Severity_Threshold와 관계없이 모든 Finding을 저장한다
4. IF Configuration의 Severity_Threshold가 소문자 허용 값(`blocker`, `major`, `minor`, `nit`) 중 하나와 정확히 일치하지 않으면(대문자 포함, 빈 문자열, 공백 포함 값 거부), THEN THE Config_Loader SHALL 허용 값 목록을 담은 오류를 출력하고 THE PR_Lens_Server SHALL 0이 아닌 종료 코드로 시작을 중단한다
5. FOR ALL Finding 목록 F와 Severity_Threshold T, Publishable_Finding 목록 SHALL F의 부분 목록이고 원래 순서를 유지하며 모든 항목의 심각도가 T 이상이다 (불변 속성)
6. FOR ALL Finding 목록 F와 두 하한 T1 ≥ T2(Glossary의 심각도 순서 `blocker` > `major` > `minor` > `nit` 기준), T1의 Publishable_Finding 수 SHALL T2의 Publishable_Finding 수 이하이다 (메타모픽 속성)

### Requirement 14: head 변경 중 게시 순서 (FR-10, 신뢰성)

**User Story:** 리뷰어로서, 이전 커밋의 리뷰가 늦게 끝나 최신 리뷰를 덮어쓰지 않기를 원한다. 그래야 요약 코멘트가 항상 최신 커밋 기준이다.

#### Acceptance Criteria

1. WHEN Comment_Publisher가 게시를 시작하면, THE Comment_Publisher SHALL GitHub API로 PR의 현재 head SHA를 조회해 소문자로 정규화한 뒤 Review_Run의 head SHA와 비교한다
2. IF Run_Status가 `succeeded` 또는 `incomplete`인 Review_Run의 head SHA가 현재 head SHA와 다르면, THEN THE Comment_Publisher SHALL Summary_Comment와 Line_Comment를 게시하지 않고 Run_Status를 `superseded`로 바꾸고, ReviewResult, usage, 추정 비용, Stored_Finding을 유지하고, 게시 완료 시각을 null로 둔다
3. THE Comment_Publisher SHALL PR_Lens_Server 프로세스 안에서 같은 PR에 대한 게시를 게시 요청 도착 순서대로 한 번에 하나씩 수행하고, 다른 PR의 게시는 병렬로 수행할 수 있다
4. WHEN Review_Run이 `superseded`가 되면, THE Query_API SHALL 해당 Review_Run을 Run_Status `superseded`로 조회 결과에 포함한다
5. IF Run_Status가 `failed`인 Review_Run의 head SHA가 현재 head SHA와 다르면, THEN THE Comment_Publisher SHALL 실패 코멘트를 게시하지 않고 Run_Status `failed`와 오류 종류를 유지한다
6. IF 현재 head SHA 조회가 P1 요구사항 21의 재시도 후에도 실패하면, THEN THE Comment_Publisher SHALL Summary_Comment와 Line_Comment를 게시하지 않고 Run_Status를 바꾸지 않고 게시 오류 종류와 GitHub 상태 코드를 Publish_Error로 기록한다
7. THE PR_Lens_Server SHALL CLI `--publish` 실행과 PR_Lens_Server 사이의 게시, 그리고 head SHA 조회와 게시 사이에는 직렬화가 보장되지 않는다는 제한을 설치 문서에 적는다
8. FOR ALL 같은 PR의 Review_Run 완료 순서(PR_Lens_Server 안에서만 게시하고 head SHA 조회와 게시 사이에 head가 바뀌지 않은 경우), 모든 게시가 끝난 뒤 Summary_Comment의 head SHA SHALL 게시한 Review_Run 중 게시 시점의 PR head SHA와 같은 Review_Run의 head SHA이다 (불변 속성)

### Requirement 15: 채택/기각 수집 (FR-11)

**User Story:** 연구회 팀으로서, 지적마다 채택/기각이 자동으로 모이기를 원한다. 그래야 지적 채택률 지표(목표 50%)를 수기 기록 없이 집계할 수 있다.

#### Acceptance Criteria

1. THE Feedback_Collector SHALL D-1에서 정한 수집 방식을 사용하고, D-1 결정 전까지는 Line_Comment의 리액션을 수집 방식으로 사용한다
2. WHEN Feedback_Collector가 Line_Comment의 리액션을 읽으면, THE Feedback_Collector SHALL 리액션 목록의 모든 페이지를 읽고, 게시 주체(요구사항 11.1)의 리액션과 `+1`·`-1`이 아닌 리액션을 제외한 뒤, `+1` 리액션만 있으면 `adopted`, `-1` 리액션만 있으면 `rejected`, 둘 다 있으면 `conflicted`, 둘 다 없으면 `none`으로 Feedback_State를 정한다
3. WHEN `pull_request` 이벤트(Target_Action 또는 `closed`)가 서명 검증과 payload 필드 검증(요구사항 1, 2)과 허용 저장소 검사(요구사항 3)를 통과하면, THE Feedback_Collector SHALL webhook 응답과 분리된 작업에서 해당 PR의 Line_Comment ID가 있는 모든 Stored_Finding의 Feedback_State를 비동기로 갱신한다
4. WHEN `prlens feedback sync <PR URL>`로 실행되면, THE PR_Lens_CLI SHALL 해당 PR의 Stored_Finding Feedback_State를 2번 규칙으로 갱신하고 `adopted`, `rejected`, `conflicted`, `none` 네 상태의 개수를 0개인 상태까지 모두 표준 오류에 출력한다
5. WHEN Feedback_State가 바뀌면, THE Review_Store SHALL 새 Feedback_State와 갱신 시각을 저장하고, 바뀌지 않으면 갱신 시각을 바꾸지 않는다
6. THE Review_Store SHALL 같은 Line_Comment ID를 가진 Stored_Finding(요구사항 12.4의 재사용, 12.5의 `duplicate_in_run`)의 Feedback_State를 같은 값으로 저장하고, Line_Comment를 재사용한 새 Stored_Finding에는 게시 결과를 기록할 때 기존 Stored_Finding의 Feedback_State와 갱신 시각을 복사한다
7. IF 리액션 조회가 404(코멘트 삭제)로 실패하거나 P1 요구사항 21의 재시도 후에도 다른 오류로 실패하면, THEN THE Feedback_Collector SHALL 해당 Stored_Finding의 Feedback_State, 갱신 시각, Line_Comment ID를 바꾸지 않고, Line_Comment ID와 오류 종류를 로그(PR_Lens_CLI에서는 표준 오류)에 남기고, 나머지 Stored_Finding의 갱신을 계속한다
8. WHEN Stored_Finding에 Line_Comment ID가 없으면(Summary_Only, 하한 미만, 게시 거부, 라인 코멘트 게시 꺼짐), THE Feedback_Collector SHALL 해당 Finding의 Feedback_State를 `none`으로 유지하고, 해당 Finding은 PLAYBOOK 5장의 PR 설명 수기 기록으로 집계한다
9. WHEN `prlens feedback sync`로 실행되면, THE PR_Lens_CLI SHALL DB 접속 정보를 요구사항 9.2의 환경변수에서, GitHub API 인증 정보를 환경변수 `GITHUB_TOKEN`에서 읽는다
10. IF `prlens feedback sync` 실행 시 `PRLENS_DB_URL`이 없거나 빈 문자열이거나 공백 문자로만 이루어져 있거나, 지정한 PR이 Review_Store에 없으면, THEN THE PR_Lens_CLI SHALL GitHub API를 호출하지 않고 원인(필요한 환경변수 이름 또는 저장되지 않은 PR)을 담은 오류를 표준 오류에 출력하고 종료 코드 2로 종료한다
11. WHEN `prlens feedback sync`에서 실패한 리액션 조회가 없으면, THE PR_Lens_CLI SHALL 종료 코드 0으로 종료한다
12. IF `prlens feedback sync`에서 리액션 조회 중 일부가 7번에 따라 실패하면, THEN THE PR_Lens_CLI SHALL 나머지 갱신 결과를 저장한 뒤 D-5의 종료 코드 4로 종료한다
13. FOR ALL 리액션 목록(여러 페이지, 게시 주체의 리액션, `+1`·`-1` 이외의 리액션 포함), Feedback_Collector가 정한 Feedback_State SHALL 2번 규칙을 그대로 옮긴 참조 구현의 결과와 같다 (모델 기반 속성)
14. FOR ALL PR, 리액션이 바뀌지 않은 동안 Feedback_Collector를 연속으로 두 번 실행한 결과 SHALL 한 번 실행한 결과와 같고, 두 번째 실행은 Feedback_State 갱신 시각을 바꾸지 않는다 (멱등 속성)

### Requirement 16: 패키지 경계와 공유 인터페이스 (트랙 병렬화)

**User Story:** 3주차 기능 리드로서, 세 트랙이 GitHub 연동 코드를 동시에 고치지 않기를 원한다. 그래야 T1과 T3가 같은 파일에서 충돌하지 않는다.

#### Acceptance Criteria

1. THE PR_Lens_Server SHALL webhook 수신·서명 검증·Review_Job 등록을 `webhook/` 패키지에, GitHub REST 호출과 App 인증을 `github/` 패키지에, Review_Store 인터페이스와 구현을 `store/` 패키지에, Comment_Publisher와 Feedback_Collector를 `publish/` 패키지(T3)에, Query_API 컨트롤러를 `query/` 패키지에, Spring 조립과 서버 시작 검증을 `server/` 패키지에, 서버와 CLI가 함께 쓰는 작업 범위 도구(취소, 제한 시간, usage 집계)를 `execution/` 패키지에 둔다
2. THE PR_Lens_Server SHALL `webhook/`과 `publish/` 패키지가 Review_Store 인터페이스에만 의존하고 `store/`의 구현 클래스와 DB 라이브러리에 의존하지 않게 하고, 이 규칙을 위반하면 CI의 자동 아키텍처 검사가 빌드를 실패시킨다
3. THE PR_Lens_Server SHALL Comment_Publisher와 Feedback_Collector가 GitHub API를 GitHub_Client로만 호출하게 하고, 이 규칙을 위반하면 CI의 자동 아키텍처 검사가 빌드를 실패시킨다
4. THE PR_Lens_Server SHALL P1 Review_Engine을 수정 없이 호출하고, 트랙 사이 데이터를 P1 공유 타입과 Review_Run, Stored_Finding 타입으로 주고받으며, P2 PR마다 P1 Review_Engine 소스에 변경이 없음을 PR 체크리스트로 확인한다
5. THE PR_Lens_Server SHALL Review_Run과 Stored_Finding 타입을 P1 요구사항 17.4~17.8과 같이 불변 타입으로 정의한다
6. THE PR_Lens_Server SHALL Review_Store 인터페이스의 메모리 구현을 테스트용으로 제공해 DB 없이 webhook부터 게시까지의 흐름을 실행할 수 있게 하고, 메모리 구현은 동시 호출에서도 요구사항 5.3의 원자성(Dedupe_Key마다 Review_Run 하나만 생성, 나머지 요청에 "이미 존재"와 기존 Review_Run ID 반환)을 지킨다
7. THE PR_Lens_Server SHALL GitHub_Client를 인터페이스 뒤에 두어, 테스트에서 네트워크 없이 고정 응답으로 게시와 채택 수집을 실행할 수 있게 한다
8. THE PR_Lens_Server SHALL 서명된 webhook payload 샘플(`opened`, `synchronize`, `reopened`, `closed`, `ping`, 서명 불일치, 허용되지 않은 저장소, Required_Fields 누락, 같은 Dedupe_Key의 재전송)을 테스트 픽스처로 포함하고, 픽스처의 비밀값과 개인정보를 가짜 값으로 둔다
9. FOR ALL 연산 순서열(Review_Run 저장, 같은 Dedupe_Key의 중복 등록, Run_Status 변경, Stored_Finding 저장, Publish_Outcome과 Feedback_State 갱신, 요구사항 10의 조회), Review_Store의 메모리 구현과 DB 구현 SHALL 같은 조회 결과와 같은 중복 거부 결과("이미 존재" 여부와 반환된 기존 Review_Run ID)를 반환한다 (모델 기반 속성)

### Requirement 17: 응답 시간 (ROADMAP 완료 조건)

**User Story:** 개발자로서, PR을 열면 2분 안에 리뷰 코멘트를 보고 싶다. 그래야 리뷰를 기다리며 흐름이 끊기지 않는다.

#### Acceptance Criteria

1. WHILE 대기 중이거나 실행 중인 Review_Job이 없는 동안, WHEN Changed_Line_Count 400줄 이하인 PR의 `opened` 이벤트를 받으면, THE PR_Lens_Server SHALL webhook 수신 시각부터 Line_Comment 게시와 Summary_Comment 게시가 모두 끝난 시각까지의 시간에서 기록된 재시도 대기 시간 합을 뺀 값을 120초 이내로 한다 (5회 측정 모두 충족해야 통과)
2. WHILE 대기 중이거나 실행 중인 Review_Job이 없는 동안, WHEN Changed_Line_Count 400줄 이하인 PR에 커밋이 추가되어 `synchronize` 이벤트를 받으면, THE PR_Lens_Server SHALL 1번과 같은 방식으로 측정한 시간을 120초 이내로 하고 Summary_Comment를 새 head SHA 기준으로 갱신한다 (5회 측정 모두 충족해야 통과)
3. THE Review_Store SHALL Review_Run마다 요구사항 7.6의 시각(webhook 수신, 등록, 실행 시작, 리뷰 완료, 게시 완료, 종료, UTC)과 재시도 대기 시간 합(ms)을 저장해 1~2번의 시간을 측정할 수 있게 한다
4. IF Review_Run의 1번 방식으로 측정한 시간이 120초를 넘으면, THEN THE Job_Runner SHALL Review_Run ID, Changed_Line_Count, 단계별 소요 시간(대기: 등록→실행 시작, 리뷰: 실행 시작→리뷰 완료, 게시: 리뷰 완료→게시 완료)을 담은 경고를 서버 로그에 남긴다

### Requirement 18: 비밀정보 보호 (보안)

**User Story:** 팀원으로서, webhook secret, App 개인 키, DB 비밀번호가 로그나 코멘트로 새지 않기를 원한다. 그래야 공개 저장소에서 비밀정보가 노출되지 않는다.

#### Acceptance Criteria

1. THE PR_Lens_Server SHALL Webhook_Secret, App_Private_Key, DB 비밀번호를 환경변수(`GITHUB_WEBHOOK_SECRET`, `GITHUB_APP_PRIVATE_KEY`, `PRLENS_DB_PASSWORD`)에서만 읽고, 설정 파일과 명령행 인자에서는 읽지 않는다
2. THE PR_Lens_Server SHALL P1의 Secret_Value에 Webhook_Secret, App_Private_Key(실제 줄바꿈 형태와 `\n` 이스케이프 형태 모두), App JWT, Installation_Token, DB 비밀번호, `PRLENS_DB_URL`에 포함된 비밀번호를 추가해 P1 요구사항 19.4~19.5의 치환을 로그, 예외 스택, 오류 응답, DB에 저장하는 오류 메시지, GitHub 코멘트에 적용한다
3. IF 설정 파일에 webhook secret, App 개인 키, DB 비밀번호에 해당하는 항목이 있으면, THEN THE Config_Loader SHALL 값을 출력하지 않고 항목 이름과 대신 쓸 환경변수 이름을 담은 오류를 출력하고, THE PR_Lens_Server SHALL 0이 아닌 종료 코드로 시작을 중단하고, THE PR_Lens_CLI SHALL 외부 API 호출 전에 종료 코드 2로 종료한다 (P1 요구사항 18.8의 확장)
4. THE Webhook_Receiver SHALL webhook 요청 본문 전체와 `X-Hub-Signature-256` 값을 로그에 남기지 않고, THE GitHub_Client SHALL 보내는 요청의 `Authorization` 헤더 값을 로그에 남기지 않는다
5. THE PR_Lens_Server SHALL Webhook_Secret, App_Private_Key, App JWT, Installation_Token, DB 비밀번호를 DB에 저장하지 않는다
6. IF 요청 처리 중 처리되지 않은 예외가 발생하면, THEN THE PR_Lens_Server SHALL 스택 트레이스와 클래스 이름 없이 Standard_Error_Response 형식의 500 본문을 응답하고, 2번으로 치환한 스택 트레이스를 서버 로그에만 남긴다
7. FOR ALL Secret_Value S(2번의 모든 형태 포함)와 S를 포함한 로그, 오류 메시지, 오류 응답, 코멘트 본문, 치환 후 텍스트 SHALL S를 부분 문자열로 포함하지 않는다 (불변 속성, P1 요구사항 19.7의 확장)

### Requirement 19: 설정 확장

**User Story:** 개발자로서, 허용 저장소, 심각도 하한, 작업자 수 같은 P2 값을 코드 수정 없이 바꾸고 싶다. 그래야 도그푸딩 중 노이즈와 부하를 조정할 수 있다.

#### Acceptance Criteria

1. THE Configuration SHALL P1 항목(P1 요구사항 18.3)에 Allowed_Repository_List, Severity_Threshold, 작업자 수, 작업 제한 시간, 최대 시도 횟수, 라인 코멘트 게시 사용 여부, Queue_Capacity, 서버 바인딩 주소를 추가하고, 1회 리뷰 비용 상한(요구사항 8.4)은 P1 항목(P1 요구사항 18.3)을 그대로 쓴다
2. THE Config_Loader SHALL 추가 항목의 기본값으로 Allowed_Repository_List 빈 목록, Severity_Threshold `minor`, 작업자 수 2, 작업 제한 시간 600초, 최대 시도 횟수 3, 라인 코멘트 게시 `true`, Queue_Capacity 100, 서버 바인딩 주소 `127.0.0.1`을 쓴다
3. WHERE 라인 코멘트 게시가 꺼져 있으면, THE Comment_Publisher SHALL PR 리뷰를 만들지 않고, Inline_Eligible인 Publishable_Finding을 Publish_Outcome `inline_disabled`(요구사항 12.13)로 Summary_Comment의 라인 밖 지적 목록(요구사항 11.3)에 포함하고, 요구사항 11.8의 길이 제한을 적용한다
4. IF 추가 항목의 값이 형식 규칙(요구사항 3.5, 3.6)을 어기거나, 정수·불리언 항목에 정수·불리언이 아닌 값이 있거나, 허용 범위(요구사항 4.6, 4.7, 4.12, 5.5, 13.4) 밖이면, THEN THE Config_Loader SHALL P1 요구사항 18.7과 같이 모든 문제 항목을 한 번에 담은 오류를 출력하고 THE PR_Lens_Server SHALL 0이 아닌 종료 코드로 시작을 중단한다
5. WHEN PR_Lens_Server가 시작되면, THE Config_Loader SHALL Configuration을 한 번 읽고, THE PR_Lens_Server SHALL 다음 재시작 전까지 같은 Configuration을 사용한다 (설정 변경은 재시작 후 반영)
6. FOR ALL 추가 항목을 포함한 유효한 Configuration, Config_Printer로 출력한 뒤 Config_Loader로 해석한 결과 SHALL 원래 Configuration과 같다 (round-trip 속성, P1 요구사항 18.11의 확장)

### Requirement 20: GitHub Actions 대체 경로 (ROADMAP 일정 위험)

**User Story:** 3주차 기능 리드로서, webhook 수신이 막혀도 PR 자동 리뷰를 계속하고 싶다. 그래야 3주차부터 도그푸딩을 시작할 수 있다.

#### Acceptance Criteria

1. WHEN `prlens review <PR URL> --publish`로 실행되면, THE PR_Lens_CLI SHALL ReviewResult를 만든 뒤 `GITHUB_TOKEN`의 계정을 게시 주체(요구사항 11.1)로 해 요구사항 11~14와 같은 규칙으로 Comment_Publisher를 실행한다
2. WHEN `--publish`로 실행되면, THE PR_Lens_CLI SHALL GitHub API 인증에 P1과 같은 환경변수 `GITHUB_TOKEN`(Actions가 제공하는 토큰 포함)을 쓴다
3. WHEN `--publish`와 `--save`가 함께 지정되면, THE PR_Lens_CLI SHALL Review_Engine 실행 전에 요구사항 5의 중복 판정을 적용하고, 새로 실행하는 Review_Run을 Run_Trigger `actions`로 저장한다
4. IF `--publish`로 실행되었고 PR의 `owner/repo`가 Allowed_Repository_List에 없으면, THEN THE PR_Lens_CLI SHALL 외부 API를 호출하지 않고 사유 `repository_not_allowed`를 담은 오류를 표준 오류에 출력하고 종료 코드 2로 종료한다
5. IF `--publish` 게시가 P1 요구사항 21의 재시도 후에도 실패하면, THEN THE PR_Lens_CLI SHALL 표준 출력에는 ReviewResult를 그대로 출력하고 게시 오류를 표준 오류에 출력하고 D-5의 종료 코드로 종료한다
6. THE PR_Lens_CLI SHALL `pull_request` 이벤트(Target_Action)에서 `prlens review --publish`를 실행하는 GitHub Actions 워크플로 예시를 설치 문서에 포함하고, 예시에 필요한 권한(`pull-requests: write`, `contents: read`), 워크플로가 쓰는 비밀정보의 환경변수 이름, fork에서 연 PR에서는 게시할 수 없다는 제한을 적는다
7. IF `--publish`가 지정되었고 `GITHUB_TOKEN`이 없거나 빈 문자열이거나 공백 문자로만 이루어져 있으면, THEN THE PR_Lens_CLI SHALL 모든 외부 API 호출 전에 환경변수 이름 `GITHUB_TOKEN`을 담은 오류를 표준 오류에 출력하고 종료 코드 2로 종료한다
8. WHEN `--publish` 게시 시점에 Review_Run이 `superseded`가 되면(요구사항 14.2), THE PR_Lens_CLI SHALL 게시 실패로 취급하지 않고 `superseded` 안내만 표준 오류에 출력한다
9. WHEN `--publish --save`의 중복 판정 결과가 `duplicate` 또는 `max_attempts_reached`이면, THE PR_Lens_CLI SHALL Claude API를 호출하지 않고 사유(`duplicate`이면 기존 Review_Run ID 포함)를 표준 오류에 출력하고 종료 코드 0으로 종료한다
10. WHEN 중복 판정 시점에 Run_Trigger가 `actions`이고 Run_Status가 `queued` 또는 `running`인 Review_Run의 등록 시각이 작업 제한 시간(요구사항 4.7)보다 오래됐으면, THE Review_Store SHALL 해당 Review_Run을 `failed`, 오류 종류 `interrupted`로 바꾼 뒤 중복을 판정한다 (중단된 Actions 실행이 같은 커밋의 리뷰를 영구히 막지 않게 한다. G-7 제안)

## 결정 대기 항목

PRD가 정하지 않은 항목은 설정 가능하게 두고, 결정 전까지 아래 기본값으로 진행합니다. 결정되면 이 문서와 Configuration 기본값을 함께 고칩니다.

| ID | 항목 | 결정 주체 | 이 스펙의 처리 |
|---|---|---|---|
| D-1 | 채택/기각 자동 수집 방식 (FR-11) | 3주차 T3 스펙 검토 | 후보인 Line_Comment의 👍/👎 리액션으로 진행(요구사항 15). GitHub는 리액션 webhook을 보내지 않으므로 PR 이벤트 수신 시와 `prlens feedback sync`로 수집. 여러 사람이 다르게 누르면 `conflicted`. 범위에서 빠지면 요구사항 15를 연기하고 PLAYBOOK 수기 기록 유지 |
| D-2 | DB와 테스트 전략 | ADR-0006 | 제안: PostgreSQL(로컬 Docker Compose), 저장 테스트는 Testcontainers, 빠른 단위 테스트는 Review_Store 메모리 구현(요구사항 16.6). H2 사용 여부와 마이그레이션 도구(Flyway 제안)는 ADR에서 확정. ADR-0006은 아직 없고(ROADMAP 1주차의 B 담당), 작업 1.2(의존성 추가)가 이 결정을 기다리므로 킥오프 전에 작성·승인(작업 0.1). 승인에는 작성자를 뺀 2명이 필요함(PLAYBOOK) |
| D-3 | draft PR 자동 리뷰 여부 | 3주차 T1 스펙 검토 | 제안: draft도 리뷰(P1 요구사항 1.1과 같은 기준). 비용이 문제되면 draft는 건너뛰고 `ready_for_review` action을 Target_Action에 추가 |
| D-4 | 비동기 실행 방식 | 3주차 T1 | 제안: 서버 프로세스 안 작업자 풀 + DB에 저장한 `queued` 상태로 재시작 복구(요구사항 4.8). 외부 큐는 쓰지 않음 |
| D-5 | `--save`/`--publish`/`feedback sync` 실패 시 CLI 종료 코드 | 3주차 T2·T3 스펙 검토 | 제안값 4. 우선순위: 결과 생성 전 실패(DB 연결·마이그레이션 실패 포함) 2 → `blocker` 있음 1 → 리뷰 후 저장·게시·feedback 조회 일부 실패 4 → `incomplete` 3 → 정상 0. `--publish --save`에서 중복으로 건너뛰면 0(요구사항 20.9). P1 D-4의 순서에 4를 추가 |
| D-6 | CLI 단독 실행(`--save`, `--publish` 없음)에 Allowed_Repository_List 적용 여부 | 3주차 킥오프 | P1 호환을 위해 미적용. PRD 6장의 "리뷰 대상 한정"은 사람이 지키는 규칙(PLAYBOOK 6장)으로 유지 |
| D-7 | Finding_Fingerprint 구성 | 3주차 T3 | 제안: `file`, `category`, `basis.ref`, `message`(줄 번호 제외). 커밋 추가로 줄이 밀려도 같은 지적을 다시 달지 않기 위함. 메시지가 조금만 바뀌어도 새 코멘트가 달리는 한계는 도그푸딩 후 재검토. 줄 번호가 없어서 생기는 다른 한계: 같은 파일에서 같은 메시지로 여러 줄을 지적하면 순번이 가장 작은 하나만 게시되고 나머지(`duplicate_in_run`)는 PR에서 보이지 않음. 실행 안의 중복 판정에만 줄 번호를 넣거나, 대표 코멘트 본문에 다른 줄 번호를 나열하는 방안을 함께 정함 |
| D-8 | Query_API 노출 범위 | 3주차 킥오프 | 인증이 범위 밖이므로 `127.0.0.1` 바인딩 기본(요구사항 10.11). webhook은 smee.io 클라이언트가 로컬로 전달. 데모 환경에서 외부 노출이 필요하면 별도 결정. 함께 정할 것: 도그푸딩용 서버는 한 대만 띄워야 함(중복 방지와 게시 직렬화가 "DB 하나, 프로세스 하나" 기준). 누구의 PC에서 띄우고 DB를 어디에 둘지. 데이터가 그 한 대의 DB에만 쌓이므로, 4주차에 나머지 두 사람이 실제 데이터를 볼 방법도 정함: `pg_dump` 파일을 주고받기(제안. 절차를 설치 문서에 추가) 또는 세 사람이 닿는 공용 DB |
| D-9 | `superseded`를 Active_Run에 포함할지 | 3주차 T1 스펙 검토 | 현재 포함(Glossary Active_Run, 요구사항 5.11). 한계: force-push로 이전 head SHA로 돌아가면 해당 SHA는 다시 리뷰되지 않고 Summary_Comment도 그 SHA 기준으로 갱신되지 않음. 대안은 `superseded`를 중복 판정에서 빼는 것(비용 증가) |
| D-10 | `superseded` Review_Run과 게시되지 않은 Finding(Publish_Outcome `not_published`, `below_threshold`, `github_rejected`, `inline_disabled` 등)을 채택률 분모에 넣는 방식 | 3주차 T3·PLAYBOOK 지표 담당 | 결정 전까지 Review_Store는 모든 Finding과 Publish_Outcome을 저장하고(요구사항 7.7, 13.3) 집계 기준은 정하지 않음. P3 통계 API 스펙 전까지 확정 |
| D-11 | 저장소 이름(`owner/repo`) 기준 허용 판정 유지 여부 | 3주차 킥오프 | 현재 이름 기준(요구사항 3.1, 3.8). 한계: 저장소 이름 변경·이전 시 Allowed_Repository_List를 갱신하고 재시작해야 하며(요구사항 19.5) 그 전까지 리뷰가 거부됨. 대안은 GitHub 저장소 ID 기준 판정 |
| D-12 | Publish_Error를 Review_Run당 하나만 둘지 | 3주차 T2·T3 스펙 검토 | 현재 하나(요구사항 7.6, 11.9, 12.11, 14.6). 한계: 라인 코멘트 리뷰 게시와 Summary_Comment 게시가 모두 실패하면 나중에 기록한 Summary_Comment 오류 하나만 남음. 대안은 게시 단계별 Publish_Error 목록 |
| D-13 | 게시 실패 뒤의 복구 | 3주차 T1·T3 스펙 검토 | 지금은 복구 경로가 없음: Publish_Error가 기록된 Review_Run은 재시작 때 다시 게시하지 않고(요구사항 4.14), 같은 커밋의 재전송은 `duplicate`가 됨. 실패 코멘트를 게시해도 게시 완료 시각이 기록되지 않음(요구사항 4.11의 대상이 `succeeded`·`incomplete`뿐). 제안: 재전송된 요청의 Active_Run이 게시되지 않은 상태면 Review_Engine 재실행 없이 게시만 다시 하고, 실패 코멘트 게시 성공도 게시 완료 시각으로 기록 |
| D-14 | 유실된 이벤트의 재시작 수단, `interrupted`의 시도 횟수 포함 여부 | 3주차 T1 스펙 검토 | GitHub는 실패한 전달을 자동으로 다시 보내지 않으므로 503 응답, 서버 중단 중 이벤트, `interrupted`는 사람이 GitHub에서 재전송해야 함. 현재 `interrupted`는 시도 횟수에 포함(요구사항 4.8, 5.5)이라 재시작이 잦은 로컬 개발에서 3회 만에 `max_attempts_reached`가 됨. 제안: 설치 문서에 재전송 방법을 적고 `interrupted`는 횟수에서 뺌 |
| D-15 | Actions 대체 경로에서 결과를 저장하는 방법 | 3주차 킥오프 | 호스팅 러너는 로컬 DB에 닿지 않아 대체 경로에서는 저장(FR-9), 채택/기각 수집(FR-11), 조회 API 데이터가 모두 비고, 4주차 완료 조건인 "3주차부터 쌓인 실제 리뷰 데이터"도 채워지지 않음. 제안: 러너가 `--format json` 결과를 아티팩트로 남기고 로컬에서 DB로 가져오는 명령을 둠. 다만 P1 결과 JSON에는 저장소 ID, PR 번호, head·base SHA, 컨텍스트 파일 목록, Line_Comment ID가 없어 그대로는 Review_Run을 만들 수 없으므로 아티팩트에 담을 항목도 정해야 함. 다른 선택지: 대체 경로를 쓰면 4주차는 CLI `--save`로 쌓은 데이터로 시연. 함께 정할 것: (1) 대체 경로로 바꾸는 기준(제안: 수요일의 작업 10.7 스모크나 12.2의 첫 종단 확인이 실패하면 전환) (2) 대체 경로의 완료 확인. 지금은 워크플로가 설치 문서의 예시뿐이고(작업 26.5), 팀 저장소에 워크플로·`ANTHROPIC_API_KEY` 비밀·`repositories.allowed`를 넣는 작업과 "2분"을 재는 방법이 없음(요구사항 17과 작업 26.6은 webhook 경로 전용) |
| D-16 | webhook 수신 경로와 조회 API의 페이지네이션·검증 규칙 | 3주차 킥오프 ([ADR 0008](../../adr/0008-keep-api-conventions.md)의 미결 항목) | 조회 API는 `/api/v1/`과 `ErrorResponse`를 따름(확정). 미결: `POST /webhooks/github`은 GitHub가 호출하는 엔드포인트라 `/api/v1/` 규칙 밖으로 둠(제안). 목록은 앞 100개와 전체 수만 반환하고 `page`·`size`는 P3에서 추가(요구사항 10.5 그대로). 경로 변수는 Bean Validation 대신 직접 검사해 잘못된 이름을 모두 모음(요구사항 10.8 그대로). `query`의 예외는 전역 핸들러가 아니라 `query` 전용 처리기가 매핑(ADR 0002는 한곳에 매핑하라고 함). webhook도 같은 예외: `WebhookProcessor`가 413·401·400·503의 `ErrorResponse`를 예외를 거치지 않고 직접 만들고, `code` 값 넷(`PAYLOAD_TOO_LARGE`, `INVALID_SIGNATURE`, `QUEUE_FULL`, `STORE_UNAVAILABLE`)을 새로 씀(설계 "요청 처리 순서와 응답"). ADR 0002의 예외로 인정할지, 예외를 던져 `ApiExceptionHandler`가 매핑하게 바꿀지 정함 |
| D-17 | P3가 P2에 요구하는 추가·변경을 P2에 먼저 넣을지 | 3주차 킥오프 | P3 요구사항 검토(`docs/spec-review/06-p3-requirements.md` "P3가 P2에 요구하는 추가·변경")가 페이지네이션, DTO 필드, `ReviewStore` 시그니처 등 16항목을 꼽음. 이 스펙에는 아직 반영하지 않음. P2 구현 전에 반영하면 재작업이 줄어듦. 현재 P3 요구사항과 직접 대조해 확인한 차이: (1) 목록 봉투가 P2는 `{items, total}`과 앞 100개, P3는 `page`·`size`·`totalCount`·`totalPages`. 이름이 바뀌면 OpenAPI, DTO, Property 12, `ReviewStore.list*` 시그니처를 다시 씀 (2) `RunDto`에 `baseSha`, `summary`, `summaryCommentId`가 없음 (3) PR 단건과 Review_Run 단건 조회가 없음 (4) 실패한 Review_Run의 오류 메시지를 P2는 내보내지 않고 P3는 치환된 메시지를 요구함 (5) 게시하지 못한 지적의 `publishOutcome`이 P2는 `not_published`, P3는 null (6) PR 번호의 앞자리 0을 P2는 허용하고 P3는 거부. 정할 시점은 작업 17.1과 17.3을 시작하기 전(수요일) |
| D-18 | `feedback sync`에서 리액션을 제외할 주체 | 3주차 T3 스펙 검토 | 현재 "게시 주체"의 리액션을 뺌(요구사항 15.2). 개인 PAT로 `feedback sync`를 실행하면 실행한 사람의 👍/👎가 빠져 채택률이 왜곡됨. 제안: 제외 대상을 "해당 Line_Comment의 작성자"로 바꿈(설계 G-12) |

G-n으로 표시한 인수 기준은 설계가 요구사항 공백을 메우려고 제안한 내용을 옮긴 것입니다(설계 "요구사항 공백 요약"). 스펙 승인 때 함께 확정합니다.
