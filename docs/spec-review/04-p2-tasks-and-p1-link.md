# 04. P2 설계 ↔ 작업 목록, P1 ↔ P2 연계

[← 목차](README.md)

대상: `P2-tasks`(377줄), `P2-des`(1,594줄), `P1-des`(910줄), `P1-tasks`(368줄), `P2-req`(417줄), `ROADMAP.md`(121줄)를 전부 읽었습니다. `P1-req`는 P2가 인용한 기준 21개만 확인했습니다.

## 총평

- **(A) P2 설계 ↔ 작업 목록: 조건부 통과.** 참조 무결성은 양호합니다(범위 밖 번호 0건, Property 1~27 모두 대응, 그래프 누락·중복 0건). 다만 그대로 착수하면 막히는 곳이 넷 있습니다: 에러 응답 형식이 스타터 ADR 0002와 다르고, 선머지 인터페이스 `NewRun`으로 DDL을 채울 수 없고, spike와 CI 분리에 task가 없거나 순서가 늦고, wave 그래프에 선후 역전이 4곳 있습니다.
- **(B) P1 ↔ P2: 데이터 계약은 일치, 조립·확장 지점은 불일치.** 패키지 루트, enum 값, 결과 JSON 필드, 재시도·종료 코드 우선순위, `.prlens.yml` 형식, 모델·비용 키는 맞습니다. 반면 P2가 전제하는 P1의 생성자 주입, 예외 타입, 메서드 시그니처, CLI 파이프라인, 패키징은 P1 문서에 그 형태로 없고, 이를 바꾸는 P2 task도 없습니다.
- **전제 변경**: 두 설계 모두 "스타터가 워크스페이스에 없다"고 적었지만 지금은 `claude-team-prlen/`에 있습니다. 아래 A-1, A-8, B-1, B-6은 스타터를 읽어 확인한 내용입니다.

## (A) P2 설계 ↔ 작업 목록

### 치명

**A-1. [모순] 에러 응답 형식이 ADR 0002와 다릅니다.**
- 위치: `P2-des:21`, `P2-des:775`, `P2-des:1108` / `P2-tasks:65`(4.3), `P2-tasks:220-222`(17.2), `P2-tasks:223`(17.3) / `repo/docs/adr/0002-standard-error-response.md`
- 문제
  - 설계는 "Spring의 `ProblemDetail`(RFC 9457) 형식을 가정"하고 task는 "`ProblemDetail(500)`", "400/404/500 `ProblemDetail`"로 고정했습니다.
  - ADR 0002(accepted)는 "모든 에러는 `ErrorResponse { code, message, details }`"이고 ProblemDetail을 기각한 대안으로 적었습니다.
  - 스타터의 `ApiExceptionHandler`에는 이미 `@ExceptionHandler(Exception.class)`가 있어 4.3의 `server` 전역 advice와 겹칩니다.
- 수정안: 4.3, 17.2, 17.3, 9.1을 `ErrorResponse` 기준으로 고치고(`invalidPathVariables`는 `details`로), 기존 핸들러를 확장할지 대체할지 task 1에 적습니다. OpenAPI는 P3에 넘기는 계약이므로 선머지 전에 확정해야 합니다.

### 중요

**A-2. [모순] `NewRun`으로는 `review_run.model NOT NULL`을 채울 수 없습니다.**
- 위치: `P2-des:447-449`, `P2-des:498`, `P2-des:544` / `P2-tasks:28-29`(2.1), `P2-tasks:200-201`(16.1)
- 문제
  - `NewRun(key, repo, title, baseSha, trigger, installationId, deliveryId, receivedAt, registeredAt)`에 모델이 없습니다.
  - 설계는 "`queued`·`running`인 동안은 … 모델 이름은 Configuration의 모델"이라고 하고, `ReviewRun.usage`도 non-null입니다.
- 수정안: `NewRun`에 `model`(필요하면 `effort`)을 추가하거나 `model`을 nullable로 바꾸고 `ReviewRun.usage` 규칙을 고칩니다. 2.1과 15.1에 반영합니다.

**A-3. [누락] spike 1~13을 수행하는 task, 담당, wave가 없습니다.**
- 위치: `P2-des:1558-1574` / `P2-tasks:348`, `P2-tasks:21`
- 문제
  - Notes는 "spike 1~13에 따라 6.4, 10.1, 10.2, 11.4, 12.1, 15.2, 17.4, 20.2, 22.2의 세부 구현이 달라집니다"라고만 합니다.
  - wave 5의 6.4(spike 3)와 10.1(spike 5)은 결과가 없으면 시작할 수 없고, JWT·PEM·OpenAPI 의존성도 "spike 결과가 나오면 추가"입니다.
  - spike 1, 11, 12, 13은 어떤 task도 참조하지 않습니다. `P2-des:1087`(effort별 응답 시간)과 `P2-des:1185`(설치 산출물 경로)의 spike는 13개 목록에도 없습니다.
- 수정안: wave 0~1에 "spike 수행·기록" task를 트랙별로 넣고 의존 task에 선행 조건으로 겁니다.

**A-4. [순서 오류] Testcontainers 테스트가 CI 분리보다 먼저 들어옵니다.**
- 위치: `P2-tasks:196`(15.4, wave 7), `P2-tasks:210`(16.4), `P2-tasks:225`(17.4, wave 10) / `P2-tasks:338`(26.4, wave 14) / `P1-tasks:333`
- 문제
  - P1 CI는 세 OS에서 `./gradlew test`를 돌리고, 26.4는 "Windows·macOS 러너에는 Docker가 없으므로 … Linux 러너에서만"이라고 적었습니다.
  - 15.4가 머지되는 순간부터 26.4까지 Windows·macOS job이 실패합니다. Docker 테스트를 가르는 방법(태그나 source set)도 task에 없습니다.
- 수정안: 26.4의 분리 부분을 task 1로 옮깁니다(`@Tag("docker")` 같은 구분 포함).

**A-5. [순서 오류] Task Dependency Graph에 선후 역전이 4곳 있습니다.**
- 위치: `P2-tasks:363-368`
- 문제
  - 10.4(wave 8)는 `GitHubApi`를 반환해야 하는데, `HttpGitHubClient`가 이를 구현하는 20.1도 wave 8입니다.
  - 20.2(wave 5)의 `AppPrincipalResolver`는 "`GET /app`(JWT 인증)"이라 10.1(wave 5)이 필요합니다. 또 `PrincipalResolver`를 `HttpGitHubClient` 생성자로 받게 하므로(`P2-des:858`) "10.3을 먼저 머지한 뒤 20.1"(`P2-tasks:13`) 규칙을 사실상 어깁니다.
  - 23.6(wave 9)은 `findPullRequest`와 `listFeedbackTargets`의 DB 구현인 16.3(wave 9)이 필요합니다.
  - 11.4(wave 6, "취소된 뒤 받은 결과는 버리고 `failed`/`timeout`")는 `ReviewJob` 동작인데 11.3은 wave 9입니다.
- 수정안: 20.1 → 10.4, 10.1·10.3 → 20.2, 16.3 → 23.6, 11.3 → 11.4 순서로 wave를 옮깁니다.

**A-6. [순서 오류] 두 트랙이 같은 파일을 고치는데 선언되지 않았습니다.**
- 위치: `P2-tasks:11-13` / `P2-tasks:235-240`, `P2-tasks:309-320`, `P2-tasks:254`, `P2-tasks:148`
- 문제
  - `cli` 패키지: T2(18.2, 18.3)와 T3(23.6, 24.1)가 P1 `ReviewCommand`, `CliPipeline`, `PrLensMain`을 함께 고칩니다. 18.3과 23.6은 같은 wave 9입니다.
  - `HttpGitHubClient`: 선언된 10.3과 20.1 외에 20.2(생성자)와 11.4/10.4("호출 중에는 중단 훅을 등록", `P2-des:343`)도 수정합니다.
  - 선언되지 않은 트랙 간 의존: 18.3(T2)이 11.2(T1)의 `UsageRecordingLlmClient`와 20.1(T3)의 `repositoryId`를 씁니다. 24.1(T3)은 7.2(T1)의 `AllowedRepositories`를 씁니다.
- 수정안: 선머지에 CLI 옵션·서브커맨드 골격과 `HttpGitHubClient` 생성자 형태를 넣고, "트랙 사이 의존"에 위 세 건을 추가합니다.

**A-7. [모호] LLM 데코레이터의 패키지가 정해지지 않았습니다.**
- 위치: `P2-tasks:5`, `P2-tasks:141`(11.2) / `P2-des:331`, `P2-des:700`, Data Models 표(`P2-des:1196-1222`)
- 문제
  - `CancellableLlmClient`, `UsageRecordingLlmClient`, `UsageAccumulator`, `DeadlineSleeper`, `JobRetryListener`, `JobTimeoutException`, `CancellationToken`이 Data Models 표에 없습니다.
  - `llm`에 두면 "`review`·`llm` 패키지는 수정하지 않습니다"와 CI diff 경고에 걸립니다. `webhook`에 두면 CLI `--save`가 `cli → webhook`에 의존하게 됩니다.
- 수정안: 새 패키지(예: `usage` 또는 `support`)를 정해 2.x 선머지에 인터페이스를 포함합니다.

**A-8. [누락] 인프라와 빌드 전제에 task가 없습니다.**
- 상세는 아래 [암묵적 전제 표](#암묵적-전제) 참고.
- 문제
  - 로컬 PostgreSQL(Docker Compose), `spring-jdbc`, 서버 `DataSource`와 풀, GitHub App 등록, smee 실행, 환경변수 안내가 빠졌습니다.
  - 서버 실행 진입점도 없습니다. `PrLensMain`, `PrLensServerApplication`, 기존 `StarterApplication`이 한 Gradle 모듈에 있는데, 어느 것이 실행 jar의 main인지와 서버를 띄우는 명령을 정하는 task가 없습니다.
  - `P2-tasks:332`의 `./gradlew :backend:generateWebhookFixtures`는 경로가 틀립니다. 스타터는 `backend/`가 단일 Gradle 루트(`settings.gradle.kts`: `rootProject.name = "starter"`)라 `:backend` 프로젝트가 없습니다.
- 수정안: task 1에 인프라 준비 하위 작업을 추가하고, 서버 진입점을 정하는 task를 12.x에 둡니다. Gradle 경로를 고칩니다.

**A-9. [모순] G-2의 "가져온 base SHA 저장"을 구현할 길이 없습니다.**
- 위치: `P2-des:389`, `P2-des:1581`, `P2-des:463` / `P2-tasks:144`
- 문제: 설계는 "저장은 가져온 값으로 하고 차이는 경고 로그"인데, `ReviewCompletion`에 baseSha가 없고 11.3은 "base가 다르면 경고 로그(G-2)"만 합니다.
- 수정안: `ReviewCompletion`에 `baseSha`를 추가하거나, 설계를 "payload 값을 유지하고 경고만"으로 고칩니다.

### 경미

**A-10. [누락] 테스트 task가 없는 설계 요소가 있습니다.**
- 설정 예시 테스트(`P2-des:1510`): 3.x에는 Property 27뿐입니다.
- `feedback sync` CLI 종료 코드와 preflight(`P2-des:1508`): 23.6에 대응 테스트가 없습니다.
- `AsyncFeedbackCollector` 합치기와 1,000개 상한(23.4), `UntrustedText.sanitize`(21.3), 전역 500과 `/error`(4.3), 게시 주체 리졸버(20.2).
- 게시 API의 HTTP 수준 페이지네이션과 422 본문(`P2-des:1479`), G-9 이름 충돌 예시(`P2-des:1328`), GitHub 공개 서명 예시 값 비교(`P2-des:1528`).
- 수정안: 각 구현 task에 테스트 하위 작업을 추가합니다.

**A-11. [누락] 선머지 타입 목록에서 빠진 타입이 있습니다.**
- 위치: `P2-tasks:34`, `P2-des:381`, `P2-des:858`, `P2-des:369`
- 문제: `GitHubAuthException`, `PrincipalResolver`, `InstallationToken`이 2.3 목록에 없습니다. `GitHubAuthException`은 P2 Data Models 표에도 없습니다(B-3 참고).
- 수정안: 2.3과 Data Models 표에 세 타입을 추가합니다.

**A-12. [참조 오류] `_Requirements_` 번호가 어긋난 곳이 있습니다.**
- `P2-tasks:234`(18.1 `P2ExitCodes`)는 "9.7, 9.8"인데 9.8은 "DB 연결이 실패하면 … 종료 코드 2"(preflight)입니다. 9.5가 맞습니다.
- `P2-tasks:236`(18.2 preflight)에 9.4, 9.5가 있고 9.8이 없습니다. `P2-tasks:240`(18.3)에는 반대로 9.8이 있고 9.4, 9.5가 없습니다.
- `P2-tasks:287`(22.3 upsert)의 11.13은 설치 문서 항목이고, `P2-tasks:292`(22.4)의 14.4는 Query_API 항목입니다.
- 어떤 task도 참조하지 않는 기준: 11.11, 11.12(22.6 본문에는 있으나 번호 없음), 15.1, 18.5. 17.1과 17.2는 Notes에 수동 측정으로 명시돼 있습니다.
- 수정안: 태그 번호를 고치고 미참조 기준을 해당 task에 추가합니다.

**A-13. [누락] ArchUnit 규칙 관련 세 가지.**
- 위치: `P2-tasks:22`, `P2-des:70`, `P2-des:1062`
- 문제
  - `P2-des:1062`의 "T3가 추가하는 규칙"(`publish`는 `github.GitHubClient`, `GitHubClientFactory`에 의존하지 않음)에 task가 없습니다.
  - "`webhook..`은 `publish`의 인터페이스(`CommentPublisher`, `FeedbackCollector`)에만"인데 `ReviewJob`은 `PublishResult`도 씁니다.
  - 체크포인트 5 시점에는 `webhook`, `query`, `store.db`가 비어 있습니다. ArchUnit은 기본값에서 검사 대상 클래스가 없는 규칙을 실패 처리하므로 `allowEmptyShould(true)`가 필요할 수 있습니다.
- 수정안: 22.x에 규칙 task를 추가하고, 허용 타입에 `PublishResult`를 넣습니다.
- 참고: 세 번째 항목은 문서 근거 없이 검토자의 지식으로 적은 내용이라 확인이 필요합니다.

**A-14. [누락] `--publish` 경로에서 `recordPublished`를 부르는 곳이 없습니다.**
- 위치: `P2-tasks:315-320`, `P2-des:1139-1151`, `P2-des:869`
- 문제
  - `P2-des:869`는 "호출자가 recordPublished(runId, at)"인데 24.1에는 없어 `--publish --save` Review_Run의 `published_at`이 항상 null입니다.
  - `P2-des:211-214` 시퀀스는 등록을 `opt --save`로만 그렸고, 표(`P2-des:1144`)와 24.1은 두 모드 모두 등록합니다.
  - 설계 G-11의 Summary 한 줄("표시하지 못한 N건")이 21.4 본문 구성에 없습니다.
- 수정안: 24.1에 `recordPublished` 호출을 추가하고, 시퀀스와 21.4를 맞춥니다.

**A-15. [모호] 월요일 선머지 분량이 큽니다.**
- 위치: `P2-tasks:19-71`
- 문제: 리드 A 한 명이 task 1~4를 맡습니다. 타입 25종 이상, `ReviewStore`, `testkit` 가짜 구현, `ConfigLoader` 확장과 검증, `SecretRegistry`, Logback 인코더, 전역 오류 처리입니다. T1 본작업도 A 담당입니다.
- 수정안: 3(설정)과 4(비밀정보)를 B·C에 나누거나 4.2~4.4를 선머지에서 뺍니다.

## (B) P1 ↔ P2 연계

### 중요

**B-1. [모순] Spring 애너테이션 ArchUnit 규칙이 서로 충돌합니다.**
- 위치: `P1-tasks:20` / `P2-des:71`, `P2-tasks:22`
- 문제
  - P1은 "Spring 애너테이션은 `cli` 조립 코드에만", P2는 "`server`, `webhook`(컨트롤러·설정), `query`, `store.db`에만 둔다"입니다.
  - P2 task 1은 "여섯 규칙을 추가"만 하므로 P1 규칙이 남아 P2 코드에서 실패합니다. 스타터의 `common/config`, `common/error`, 루트 `StarterApplication`은 어느 목록에도 없습니다.
- 수정안: P2 task 1에 "P1 규칙을 교체"라고 적고 `common`과 Application 클래스의 처리(이동 또는 허용)를 정합니다.

**B-2. [누락] P2가 전제하는 P1 생성자 주입을 P1 task가 보장하지 않습니다.**
- 위치: `P2-des:20`, `P2-des:330-331` / `P1-tasks:116-122`(7.1), `P1-tasks:250`(13.2), `P1-tasks:51-57`(3.1)
- 문제
  - P2는 "공유 `HttpClient`, `InstallationCredentials`, 작업 전용 `RetryExecutor`"와 `RetryingLlmClient(…)` 체인을 작업마다 조립합니다.
  - P1 task에는 `HttpGitHubClient`, `RetryingLlmClient`, `RetryExecutor`가 `HttpClient`, 자격 증명, `Sleeper`, `RetryListener`, 위임 객체를 생성자로 받는다는 문구가 없습니다. P2 설계도 "확인하지 못했습니다"라고 적었습니다.
- 수정안: P1 7.1, 13.2, 3.1에 생성자 시그니처를 적거나, P2에 "P1 조립 지점 리팩터" task를 둡니다.

**B-3. [참조 오류] `GitHubAuthException`은 P1에 없습니다.**
- 위치: `P2-des:381-382`, `P2-tasks:129` / `P1-des:805`, `P1-tasks:119`
- 문제
  - P1의 401은 "`GitHubApiException(401/403/404)`"입니다.
  - `--publish`(PAT)에서 P1의 401을 `github_auth`로 매핑하는 규칙도 없습니다(`P2-des:1149` "`github_auth`면 6.7").
- 수정안: 2.3에 타입과 상속 관계를 정의하고 11.3의 매핑 표에 "`GitHubApiException(401)` → `github_auth`"를 적습니다.

**B-4. [모순] `PrFetcher` 입력 타입이 webhook 경로와 맞지 않습니다.**
- 위치: `P1-des:118`(`fetch(PullRequestUrl)`), `P1-tasks:123-131` / `P2-des:330`, `P2-tasks:144`
- 문제: webhook 경로에는 `RepoRef`와 번호만 있고 URL이 없습니다. `PullRequestUrl`을 값으로 직접 만들 수 있는지 P1에 정의가 없습니다.
- 수정안: P1 7.4를 `fetch(RepoRef, int)`로 하거나 `PullRequestUrl.of(owner, repo, number)`를 P1에 명시합니다.

**B-5. [누락] CLI 확장은 P1 파이프라인을 바꿔야 하는데 P2 task가 그 파일을 지목하지 않습니다.**
- 위치: `P1-tasks:295-304`, `P1-des:838` / `P2-tasks:235-240`, `P2-tasks:309-320`, `P2-des:689`
- 문제: 구조상 `--save`, `--publish`, `feedback sync`는 picocli `review` 명령에 얹을 수 있습니다. 다음 세 가지가 걸립니다.
  - P1 `SecretMasker`는 "두 Secret_Value로 만들고" 고정이라, P2의 "DB 비밀번호 SecretMasker 등록"은 `MaskingPrintStream`이 `SecretRegistry`를 보도록 바꿔야 합니다.
  - P1 preflight는 `GITHUB_TOKEN`과 `ANTHROPIC_API_KEY`를 둘 다 요구합니다. `feedback sync`는 Anthropic 키가 필요 없습니다.
  - `ConfigLoader` 반환형이 `LoadedConfiguration`으로 바뀌어(`P2-des:1130`) P1 호출부와 P1 Property 37 테스트가 바뀝니다.
- 수정안: task 3과 4에 "P1 `CliPipeline.preflight`, `MaskingPrintStream`, `ConfigLoader` 호출부 수정"을 넣습니다.

**B-6. [모순] 패키징 방식이 다릅니다.**
- 위치: `P1-tasks:313`(18.7) / `P2-des:1176-1177`
- 문제
  - P1은 "실행 jar와 래퍼 스크립트 `prlens`(sh), `prlens.cmd`"입니다.
  - P2 워크플로 예시는 `./gradlew :backend:installDist`와 `backend/build/install/prlens/bin/prlens`입니다.
  - 스타터 `build.gradle.kts`에는 `application` 플러그인이 없어 `installDist`가 없고, `:backend` 프로젝트도 없습니다.
- 수정안: P1 18.7의 산출물 경로를 기준으로 예시와 26.5를 맞춥니다.

**B-7. [누락] `Severity.rank()`가 P1에 없습니다.**
- 위치: `P2-des:918`, `P2-tasks:261` / `P1-des:439`(`enum Severity { BLOCKER, MAJOR, MINOR, NIT }`)
- 문제: T3의 21.1이 P1 `model`을 암묵적으로 고치게 됩니다.
- 수정안: 선머지(2.x)에서 `model`에 추가하거나 `publish` 안의 순수 함수로 둡니다.

### 경미

**B-8. [누락] owner/repo 문자 규칙을 검사하는 독립 함수가 P1에 없습니다.**
- 위치: `P2-des:774`, `P2-des:255`, `P2-tasks:90`, `P2-tasks:220` / `P1-des:179-185`, `P1-tasks:123`
- 문제: P2는 "P1 `PullRequestUrl`의 문자 규칙 검사 함수를 재사용"인데 P1은 URL 전체 정규식 하나뿐입니다.
- 수정안: P1에 `isValidOwner`/`isValidRepo`를 공개하는 내용을 P1 7.2나 P2 2.x에 적습니다.

**B-9. [모순] 422 예외가 P1 예외 계층에 얹힐 수 있는지 불명확합니다.**
- 위치: `P2-des:843` / `P1-des:798`, `P1-des:808`, `P1-tasks:54`
- 문제: `GitHubUnprocessableException extends NonRetryableApiException`인데 P1은 "`PrLensException`(sealed) 하위 타입"이고, `NonRetryableApiException`의 위치가 설계는 `github`·`llm`, task는 `support`로 다릅니다.
- 수정안: 상속이 가능한지(non-sealed 여부), `StoreException`과 `JobTimeoutException`이 계층 밖이어도 되는지 정합니다. P1에서는 처리했습니다(`PrLensException`을 sealed가 아닌 추상 클래스로 바꾸고 재시도 예외를 `support`에 둠).

**B-10. [모순] `ReviewEngine` 등록 방식이 다릅니다.**
- 위치: `P1-des:75` / `P2-des:14`
- 문제: P1은 "P2에서 `@Bean`으로 등록", P2는 "Review_Job마다 … 새 인스턴스"입니다.
- 수정안: P1 문구를 고칩니다.

**B-11. [참조 오류] 이름과 출처가 맞지 않는 재사용 두 건.**
- `P2-des:905`의 `normalizeRepoPath(file)`은 P1에서 `RepoPaths.normalize`(`P1-tasks:32-33`, `model`)입니다.
- `P2-des:618`은 context_files 원소가 "P1 Result_Codec의 JSON 이름을 따릅니다"라고 하지만 P1 ResultCodec JSON(`P1-des:466-489`)에는 컨텍스트 파일 목록이 없습니다.
- 수정안: 이름을 P1에 맞추고, context_files의 JSON 형식을 P2에서 직접 정의합니다.

**B-12. [모호] 실패 Review_Run의 비용 계산 경로.**
- 위치: `P2-des:798` / `P1-des:238`, `P1-des:344`
- 문제
  - P2는 "합으로 `CostCalculator` 계산"인데 P1 설계의 호출 형태는 `CostCalculator.price(partial, config)`(엔진 내부 타입)입니다.
  - P1 분할 모드는 "Chunk별로 반올림한 값을 더해"이므로, 총합에 한 번 반올림하는 P2 방식과 넷째 자리가 달라질 수 있습니다.
- 수정안: 토큰 네 개, 모델, 단가를 받는 공개 함수를 P1 11.6에 명시합니다.

**B-13. [모호] 재시도 대기 단위.**
- 위치: `P1-des:827-829` / `P2-des:12`
- 문제: P1 `onRetry(…, wait)`는 초 단위이고 P2는 ms 합으로 집계합니다. `DeadlineSleeper`가 대기를 잘라도 리스너에는 원래 값이 전달됩니다.
- 수정안: 단위와 실제 대기 시간을 P1 인터페이스 Javadoc에 적습니다.

### 일치를 확인한 것

패키지 루트 `com.prlens`(스타터는 아직 `com.example.starter`, ROADMAP 1주차에 변경), severity·category·basis·verdict·mode·사유 enum 값과 DDL CHECK, `RunDto`·`FindingDto`의 P1 필드, 종료 코드 우선순위 2 → 1 → 4 → 3 → 0, 재시도 규칙 인용, `.prlens.yml` 형식과 탐색 순서, 비용 상한 키, 모델 `claude-opus-5-5`, jqwik 태그·tries 규칙, 트랙 담당(T1 A, T2 B, T3 C).

## 암묵적 전제

| 전제 | 설정하는 task | 판정 |
|---|---|---|
| P1 코드 머지 완료 | `P2-tasks:5`에 전제로만 명시 | 가정 |
| Spring Web | task 1 | 중복: 스타터에 `spring-boot-starter-webmvc`가 이미 있음 |
| PostgreSQL 드라이버, Flyway 2종, Testcontainers | task 1 | 있음. 단 D-2/ADR-0006이 "결정 대기"인 채 진행 |
| `JdbcClient`/`TransactionTemplate`(spring-jdbc), 서버 `DataSource`와 풀 | 없음 | 누락 |
| 로컬 PostgreSQL(Docker Compose) | 없음. `P2-des:1477`에 언급만 | 누락 |
| 개발 PC의 Docker(Testcontainers) | 없음 | 가정 |
| CI 워크플로 파일 | P1 task 21. P2는 task 1과 26.4에서 수정 | 워크스페이스에 `.github/`가 없음. Docker 분리는 26.4로 늦음(A-4) |
| JWT·PEM 라이브러리 | "spike 5 후 추가"(`P2-tasks:21`) | spike task 없음 |
| OpenAPI 검증 도구 | "spike 8 후 추가" | spike task 없음 |
| GitHub App 생성, 권한, 설치, 개인 키, webhook secret | 26.5는 "App 권한" 문서만 | 등록 작업 누락 |
| smee.io 채널과 클라이언트 | 없음. `P2-des:1541` 수동 절차에만 | 누락 |
| 서버 환경변수 안내(`GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_WEBHOOK_SECRET`, `ANTHROPIC_API_KEY`, `PRLENS_DB_URL`·`USER`·`PASSWORD`) | 12.2는 검증만, 26.5 목록에 없음 | 문서 누락. 서버 시작 검증 대상에 `ANTHROPIC_API_KEY`와 `PRLENS_DB_URL`이 있는지도 불명(`P2-des:383`) |
| Actions 저장소 비밀 `ANTHROPIC_API_KEY` | 26.5 예시 문서 | 문서만 |
| 서버 실행 진입점과 패키징 | 없음 | 누락 |
| Gradle 경로 `:backend:` | 26.1, `P2-des:1176` | 오류: 단일 프로젝트 |
| PR 템플릿, 브랜치 보호 | task 1(체크 항목 추가) | 템플릿 파일은 워크스페이스에 없음 |
| 도그푸딩 PR(400줄 이하 5개) | Notes 수동 항목 | 명시됨 |
| jqwik, ArchUnit | P1 task 1 | 있음. spike 13(ArchUnit 버전)은 중복 |

참고: 현재 개발 PC에는 Docker가 설치되어 있지 않습니다(2026-10-04 확인).

## 커버리지 요약

- **스타터**: `build.gradle.kts`, `settings.gradle.kts`, `backend/CLAUDE.md`, ADR 0001·0002, `ApiExceptionHandler`, `ErrorResponse`, backend rules 2개를 읽었습니다.
- **요구사항 참조**: P2 기준 205개 중 task가 가리키는 번호는 범위 밖 0건입니다. `_Requirements_`와 `Validates`를 합쳐 199개가 참조되고, 미참조는 6개(11.11, 11.12, 15.1, 17.1, 17.2, 18.5)입니다.
- **Property**: 27개 모두 task가 있고, `Validates` 목록 22개 줄이 설계의 목록과 전부 같습니다.
- **Dependency Graph**: leaf task 95개가 wave 0~16에 누락·중복 없이 들어 있습니다. 체크포인트 5, 13, 19, 25, 27의 위치는 맞습니다. 선후 역전은 A-5의 4건입니다.
- **P1 요구사항 인용**: P2 문서의 인용이 모두 존재하고 의미가 맞습니다.
- **설계 구성 요소**: 패키지 표, 인터페이스, DDL, 설정 키, 오류 표를 task와 대조했습니다. 구현 task가 없는 것은 A-8, A-11, A-13이고 테스트 task가 없는 것은 A-10입니다.

## 확인하지 못한 것

- P1 구현 코드가 없어 실제 생성자와 예외 계층은 문서로만 판단했습니다(B-2, B-9).
- `.github/workflows/ci.yml`과 PR 템플릿의 실제 내용은 워크스페이스에 없어 보지 못했습니다.
- PLAYBOOK, PRD, ADR-0003~0006, P3 requirements는 읽지 않았습니다.
- CLI 실행 시 `logback-spring.xml`이 적용되는지는 문서에 근거가 없습니다. P1 CLI는 Spring 컨텍스트를 띄우지 않고(`P1-des:362`) P2 4.2는 `logback-spring.xml`만 만들어, CLI 로그가 stdout으로 샐 위험이 있습니다. 실행해서 확인해야 합니다.
- spike 대상(GitHub 422 본문, `GET /user` 403, Flyway API, Boot 4 Jackson 3 공존 등)의 사실 여부는 이 검토에서 검증하지 않았습니다. [05번 문서](05-external-facts.md)를 보세요.
