# Implementation Plan: PR Lens P2 자동화·저장

## Overview

[design.md](design.md)를 구현하는 작업 목록입니다. 순서는 "환경 준비(킥오프 전)와 사전 확인 → 공유 경계 선머지(리드 A, 월요일) → T1·T2·T3 병렬 → 통합·CI"입니다. P1 코드(`model`, `github`, `pullrequest`, `context`, `review`, `llm`, `codec`, `config`, `support`, `cli`)가 머지된 상태를 전제로 하며, `review`·`llm` 패키지는 수정하지 않습니다(요구사항 16.4).

- 월요일 선머지는 작업 1.1과 2.1~2.4, 2.6, 2.7(패키지 골격, 공유 타입, 경계 인터페이스, CLI 골격, `execution` 인터페이스)까지입니다. 각 트랙은 이것이 머지되면 시작합니다. 리드 A는 T1도 맡아 말단 작업의 40% 이상이 몰리므로, 선머지가 아닌 공유 작업은 나눕니다: 1.3(Docker 분리, CI)과 4(비밀정보)는 C, 3(설정)은 B, 1.2(의존성)와 2.5(`testkit`)는 A. 킥오프에서 확정합니다
- 언어와 도구: Java 17, Spring Boot 4, PostgreSQL + Flyway, JDBC(`JdbcClient`), Testcontainers, JUnit Jupiter 6, jqwik, ArchUnit (D-2·D-4는 결정 대기, 설계의 권고안으로 진행)
- `*`가 붙은 하위 작업은 속성 기반 테스트·보조 테스트로, 일정이 밀리면 뒤로 미룰 수 있습니다. 요구사항이 테스트나 CI 검사 자체를 요구하는 작업(OpenAPI 계약 검사, 서명된 webhook 픽스처, ArchUnit 규칙)은 필수로 두었습니다
- 속성 테스트에는 `// Feature: pr-lens-p2-automation, Property N: <이름>` 태그 주석을 답니다
- Docker가 필요한 테스트(Testcontainers)는 `@Tag("docker")`를 붙입니다. 기본 `./gradlew test`와 Stop hook은 이 태그를 빼고 실행하고, Linux CI가 `./gradlew check`로 함께 실행합니다(작업 1.3). 개발 PC에 Docker가 없으면 이 테스트는 CI에서만 확인됩니다
- 명령은 `backend/`에서 실행합니다. 서브프로젝트가 없으므로 `:backend:` 접두어를 쓰지 않습니다
- 트랙 담당은 ROADMAP 3주차 기본안(T1 A, T2 B, T3 C)을 따릅니다
- 트랙 사이 의존:
  - T1·T3의 흐름 테스트는 T2의 `InMemoryReviewStore`(14.3)를 씁니다. T2는 14.1과 14.3을 화요일까지 먼저 머지합니다
  - T1(10.3, 401 재발급)과 T3(20.1, 게시 메서드와 생성 요청 처리)는 둘 다 `HttpGitHubClient`를 고칩니다. 10.3을 먼저 머지한 뒤 20.1을 시작합니다. 생성자 형태(`PrincipalResolver` 인자)는 2.6에서 먼저 고정하므로 20.2는 생성자를 고치지 않습니다
  - T1의 10.4(`GitHubClientFactory`)는 T3의 20.1·20.2가 끝나야 완성됩니다. 다만 2.6에서 `HttpGitHubClient implements GitHubApi`를 스텁으로 먼저 선언하므로 10.4의 골격과 11.3은 20.1을 기다리지 않고 진행할 수 있습니다
  - T2의 18.3(`--save` 저장 흐름)은 T1의 11.2(`UsageRecordingLlmClient`)와 T3의 20.1(`repositoryId`)을 씁니다
  - T3의 23.6(`feedback sync`)과 24.1(`--publish`)은 T2의 15.2(`DbMigrator`, `DbConnector`), 16.3(`findPullRequest`, `listFeedbackTargets`), 18.1(`P2ExitCodes`)을 쓰고, 24.1은 T1의 7.2(`config.AllowedRepositories`)와 11.2(`UsageRecordingLlmClient`)도 씁니다
  - `cli` 패키지는 T2(18.2, 18.3)와 T3(23.6, 24.1)가 함께 고칩니다. 옵션과 서브커맨드의 자리는 2.6에서 먼저 만들고, 각 트랙은 자기 명령의 처리 클래스만 고칩니다
- PR 단위는 P1과 같습니다: 상위 작업 하나가 PR 하나이고, "PR 경계"가 적힌 작업은 그 경계대로 나눕니다. 크기 목표는 `src/main` 변경 400줄 이하입니다(루트 `CLAUDE.md`)
- 브랜치는 `feat/<track>-p2-<번호>-<slug>`입니다(플레이북 6장). 선머지와 공유 작업(1~4)은 트랙 자리에 `shared`를 쓰고(`feat/shared-p2-<번호>-<slug>`), 컨텍스트 작업(28)은 `chore/p2-28-<slug>`입니다

## Tasks

### 공유 경계 (리드 A, 월요일 선머지)

- [ ] 0. 환경 준비와 사전 확인 (0.1은 킥오프 전에, 0.2~0.4는 각자 막는 작업보다 먼저 끝냄. 결과는 `docs/spikes/`와 설치 문서에 기록)
  - [ ] 0.1 환경 준비 (사람이 수행. **이것이 없으면 T2 검증과 금요일 완료 조건이 막힘**)
    - GitHub 원격 저장소와 CI 첫 실행(아직 원격이 없어 `.github/workflows/ci.yml`이 한 번도 돈 적이 없음)
    - DB: B의 PC와 도그푸딩 서버를 띄울 PC에 Docker 또는 같은 메이저 버전의 PostgreSQL 설치. 저장소 루트에 `docker-compose.yml`(PostgreSQL, Testcontainers와 같은 메이저 버전). 서버는 DB 없이는 시작하지 않고, `JdbcReviewStore`를 검증하는 테스트는 모두 Docker가 필요함
    - GitHub App 등록: 권한과 `pull_request` 이벤트 구독(요구사항 6.9), webhook URL과 secret, content type JSON, 개인 키 발급, 대상 저장소에 설치
    - smee.io 채널과 클라이언트로 로컬에서 webhook 이벤트를 받아 보기(ROADMAP 1주차 spike). 한글 제목 PR의 이벤트로 HMAC을 다시 계산해 `X-Hub-Signature-256`과 같은지 확인(spike 15. 다르면 서명 검증이 모두 실패하므로 다른 터널 도구나 대체 경로를 정함)
    - 서버 환경변수 준비(`GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_WEBHOOK_SECRET`, `ANTHROPIC_API_KEY`, `PRLENS_DB_URL`·`PRLENS_DB_USER`·`PRLENS_DB_PASSWORD`)
    - 도그푸딩용 서버를 누구의 PC에서 한 대만 띄울지, 나머지 두 사람이 쌓인 데이터를 어떻게 볼지 정함(요구사항 D-8)
    - ADR-0006(DB와 테스트 전략, 요구사항 D-2) 작성과 승인. ROADMAP 1주차의 B 담당. 1.2와 1.3이 이 결정을 기다림
    - _Requirements: 6.9_
  - 설계 "3주차 spike 체크리스트"에서 "실험 필요"인 항목을 아래 셋에서 합니다. "문서로 확인"인 항목은 구현하면서 한 번 실행해 확인합니다
  - [ ] 0.2 T1 spike (A): 2(싱글턴 초기화와 포트 열기 순서), 3(Tomcat 본문 상한, 413 동작, 읽기 제한 시간), 4(진행 중 HTTP 호출 중단), 5(JWT·PEM 라이브러리 선택과 의존성 승인 요청), 14(P1 측정값으로 effort별 응답 시간 판단)
    - 막는 작업: 12.1, 6.4, 11.4, 10.1, 10.3(호출 중단)
  - [ ] 0.3 T2 spike (B): 7(Flyway 실패 버전 API, 동시 migrate), 8(OpenAPI 검증 도구의 3.1 지원), 16(jqwik 속성 안에서 MockMvc와 Testcontainers 사용), Testcontainers가 JUnit 6·Spring Boot 4.1에서 도는지
    - 0.1의 DB가 필요. Testcontainers 확인은 1.2의 의존성이 있어야 하므로 1.2 뒤에(또는 1.2 브랜치에서) 함. 막는 작업: 15.2, 15.4, 17.4, 16.4, 17.5
  - [ ] 0.4 T3 spike (C): 9(리뷰 422 본문 형식, 리뷰당 코멘트 수), 10(게시 주체의 `user.id`·`user.login`), 11(코멘트 길이 단위), 12(Actions 토큰의 이슈 코멘트 권한)
    - 0.1의 GitHub App이 필요. 막는 작업: 22.2, 20.2, 21.4, 24.1. spike 10은 T3 구현 전에 끝냅니다

- [ ] 1. 패키지 골격, 의존성, 아키텍처 규칙, CI
  - PR 경계: 1.1 / 1.2 / 1.3
  - [ ] 1.1 패키지 골격과 아키텍처 규칙 (A, 월요일 선머지. 새 의존성 없음)
    - `backend/src/main/java/com/prlens/` 아래 `webhook`, `store`, `store.db`, `store.memory`, `query`, `publish`, `server`, `execution` 패키지 생성
    - 스타터 `common` 정리: `ErrorResponse`는 `support`로, `ApiExceptionHandler`와 `TimeConfig`는 `server`로 옮김. `StarterApplication`은 이름만 `PrLensServerApplication`으로 바꾸고 루트 패키지 `com.prlens`에 그대로 둠(다른 패키지의 Spring 테스트가 설정 클래스를 찾을 수 있게. 설계 "서버 실행"). ADR 0003의 미결 항목, 설계 "작업 환경 확인 결과"
    - 스타터의 `StarterApplicationTests`(`@SpringBootTest`)는 루트 패키지에 두고 이름을 `PrLensServerApplicationTests`로 바꿈. 바꾼 뒤 `./gradlew test`가 통과하는지 확인
    - `ArchitectureTest`에 설계 "의존 규칙" 표의 규칙을 모두 추가(`publish` 규칙 포함). P1의 Spring 애너테이션 규칙("`cli` 조립 코드에만")은 이 표의 규칙으로 **교체**(루트 패키지의 `PrLensServerApplication` 허용 포함). 대상 클래스가 아직 없는 규칙은 `allowEmptyShould(true)`
    - _Requirements: 16.1, 16.2, 16.3, 16.4_
  - [ ] 1.2 의존성 추가 (A. 선머지 대상 아님. ADR-0006 결정과 사람의 승인 후 진행)
    - `backend/build.gradle.kts`에 버전 고정으로 추가하고 PR 설명에 이유를 적음: `spring-boot-starter-jdbc`(`JdbcClient`, `TransactionTemplate`, HikariCP), `postgresql` 드라이버, `flyway-core`, `flyway-database-postgresql`, Testcontainers PostgreSQL. Spring Web은 스타터에 이미 있음
    - JWT·PEM 라이브러리(spike 5)와 OpenAPI 검증 도구(spike 8)는 0.2, 0.3 결과가 나오면 별도 PR로 추가(이 작업은 spike를 기다리지 않음)
    - 기존 `@SpringBootTest` 테스트(1.1에서 이름을 바꾼 `PrLensServerApplicationTests`) 처리: JDBC 스타터가 들어오면 DataSource 자동 설정이 DB URL 없이 실패할 수 있음. 이 테스트를 `@Tag("docker")`로 옮기거나 지우고, 기본 `./gradlew test`가 DB 없이 통과하는지 확인
    - _Requirements: 7.10_
  - [ ] 1.3 Docker 테스트 분리, CI, PR 템플릿 (C. 1.1과 병렬. Testcontainers 테스트 15.4보다 먼저 머지)
    - `test` 태스크가 `docker` 태그를 제외하고, `dockerTest` 태스크가 이 태그만 실행하고, `check`가 둘 다 실행하게 `build.gradle.kts`를 고침(사람의 승인 필요). 1.2와 같은 파일을 고치므로 먼저 머지된 쪽 위에 다른 쪽을 rebase
    - jqwik 속성에도 제외가 먹는지 확인: jqwik은 Jupiter의 `@Tag`가 아니라 자체 `@Tag`(`net.jqwik.api.Tag`)를 볼 수 있음. 속성 하나로 실행해 보고, 필요하면 Docker가 필요한 속성에 두 애너테이션을 모두 붙이는 규칙을 `testing.md`에 적음
    - CI: Linux 러너에서만 `check`, Windows·macOS는 `test`. 현재 `ci.yml`은 ubuntu 단일 job이므로, P1 작업 21의 OS 매트릭스가 아직 없으면 그 job의 명령만 `check`로 바꿈. `review`·`llm` 패키지 diff 경고 단계 추가(`actions/checkout`에 `fetch-depth: 0`. 기본 얕은 복제에서는 `origin/main...HEAD`를 계산할 수 없음). `check`가 15분 제한 안에 드는지 확인
    - PR 템플릿(`.github/pull_request_template.md`)에 "`review`·`llm` 패키지 diff 없음" 체크 항목과 Docker 테스트 결과(CI 링크) 칸 추가
    - _Requirements: 16.2, 16.4_

- [ ] 2. 공유 타입과 경계 인터페이스 정의
  - PR 경계: 2.1~2.4 / 2.5 / 2.6~2.7 (2.5를 뺀 나머지가 월요일 선머지)
  - [ ] 2.1 `store` 레코드와 열거형 작성
    - `RunTrigger`, `RunStatus`(`isActive()`), `PublishOutcome`, `FeedbackState`, `DedupeKey`(head SHA `^[0-9a-f]{40}$` 아니면 거부), `PrKey`, `NewRun`(`model`, `effort` 포함), `CliRun`(`model`, `effort` 포함), `ReviewCompletion`(가져온 `baseSha` 포함), `RunFailure`, `RegisterOutcome`, `DedupeStatus`, `ReviewRun`, `RunTimestamps`, `RunError`, `PublishError`, `ContextFileRef`, `StoredFinding`, `FindingOutcome`, `Page<T>`, `RepositorySummary`, `PullRequestSummary`, `RunSummary`(필드는 설계 "ReviewStore 인터페이스"), `StoreException`, `StoreErrorKind`
    - 불변식 검사: `NewRun`은 `trigger == WEBHOOK ⇔ installationId != null`이고 `CLI`는 거부, `CliRun`은 completion과 failure 중 정확히 하나. 목록은 `List.copyOf`, 시각은 마이크로초로 자름
    - _Requirements: 7.6, 7.7, 16.5_
  - [ ] 2.2 `ReviewStore` 인터페이스 작성 (설계 T2 절의 메서드 전체, 상태 전이 가드와 반환값 의미는 Javadoc에 적음)
    - _Requirements: 5.3, 16.5, 16.6_
  - [ ] 2.3 `github` 확장 인터페이스 작성
    - `RefreshableCredentials`, `InstallationToken`, `PublishGitHubClient`, `GitHubApi`, `PublishGitHubClientProvider`, `PrincipalResolver`, `GitHubPage`, `GitHubPrincipal`, `AuthorRef`, `ReviewCommentRef`, `IssueCommentRef`, `ReactionRef`, `ReviewDraft`, `DraftComment`, `CreatedReview`, `GitHubUnprocessableException`, `GitHubAuthException`(P1 `GitHubApiException`의 하위 타입. P1 쪽 클래스가 final이면 final을 뗌), `PostOutcomeUnknownException`
    - `pullrequest.RepoNames.isValidOwner`/`isValidRepo`: P1 `PullRequestUrl`의 정규식에서 문자 규칙을 함수로 뽑고 `PullRequestUrl`도 이 함수를 쓰게 함(7.1, 17.2, 3.1이 사용)
    - P1 `GitHubClient`는 수정하지 않음. 설계는 T3가 추가한다고 적었지만, T1의 `GitHubClientFactory`가 `GitHubApi`를 반환하므로 병렬 작업을 위해 월요일에 인터페이스만 먼저 머지
    - _Requirements: 16.3_
  - [ ] 2.4 `publish` 인터페이스 작성: `CommentPublisher`, `PublishResult`, `PublishSettings`, `FeedbackCollector`, 아무것도 하지 않는 `FeedbackCollector.NOOP`(23이 끝나기 전의 서버 조립과, 23을 미룰 때 씀)
    - _Requirements: 16.2_
  - [ ] 2.5 `testkit` 가짜 구현과 공용 생성기 기본형 작성
    - 선머지 대상이 아님. 인터페이스(2.1~2.4)가 머지되면 리드가 트랙과 병렬로 작성
    - `FakeGitHubClient`(`GitHubApi` 구현: PR 상태, 이슈·리뷰 코멘트, 리액션 페이지, 게시 주체, 호출 기록, 401·404·422·5xx·지연 주입)
    - `FakeLlmClient`(응답 스크립트, 요청 기록), 가변 `Clock`, 기록하는 `Sleeper`
    - 생성기: Dedupe_Key(대소문자 변형 head SHA), ReviewRun·StoredFinding(U+0000 제외, 마이크로초 시각), 리액션 페이지
    - _Requirements: 16.6, 16.7_

  - [ ] 2.6 CLI 골격과 P1 조립 지점 정리 (월요일 선머지. T2·T3가 같은 파일을 동시에 고치지 않게 자리를 먼저 만듦)
    - `review`의 `--save`, `--publish` 옵션과 `feedback sync`, `server` 서브커맨드를 등록하고, 각 처리는 빈 클래스(`SaveFlow`, `PublishFlow`, `FeedbackSyncCommand`, `ServerCommand`)로 위임. 본문은 `UnsupportedOperationException`
    - `HttpGitHubClient` 생성자를 `(HttpClient, GitHubCredentials, RetryExecutor, PrincipalResolver, CancellationToken)`으로 바꾸고 `implements GitHubApi`를 선언(게시 메서드 본문은 `UnsupportedOperationException`, 20.1이 채움). P1 조립 코드와 P1 테스트(7.6)는 `TokenPrincipalResolver` 스텁과 `CancellationToken.NONE`을 넘기도록 고침. P1의 `BoundarySignatureTest.adaptersTakeCollaboratorsThroughConstructors`가 이 생성자의 인자 3개를 리플렉션으로 고정하고 있으므로 그 단언도 새 시그니처로 함께 고침
    - P1 `CliPipeline.preflight`를 명령별로 필요한 환경변수 목록을 받는 형태로 바꿈(`feedback sync`는 `ANTHROPIC_API_KEY` 불필요)
    - _Requirements: 9.1, 15.4, 15.9, 20.1_
  - [ ] 2.7 작업 범위 도구의 타입 작성 (월요일 선머지)
    - `support`: `CancellationToken`(`NONE` 포함), `SecretRegistry`의 공개 시그니처(구현은 4.1)
    - `execution`: `JobTimeoutException`(P1 `PrLensException`의 하위 타입), `UsageAccumulator`, `RunErrorKinds.of(Throwable)`(설계의 예외 → Run_Error_Kind 대응표. 서버와 CLI가 함께 씀), `DeadlineSleeper`·`JobRetryListener`·`UsageRecordingLlmClient`·`CancellableLlmClient`의 생성자 시그니처(구현은 11.1, 11.2)
    - _Requirements: 4.5, 4.7, 8.2_

- [ ] 3. 설정 확장 (B. 선머지 대상 아님. 2.6이 머지된 뒤 시작: 둘 다 P1 `CliPipeline`을 고침)
  - [ ] 3.1 `AutomationSettings`, `LoadedConfiguration`와 `ConfigLoader` 확장
    - `repositories.allowed`, `jobs.*`, `publish.*`, `server.address` 구역과 범위 검증(정수 스칼라만, 불리언 리터럴만, IP 리터럴만), 모든 문제를 모아 한 번에 보고
    - 허용 목록은 대소문자 무시로 중복을 제거하고 처음 나온 표기를 유지, 1,000개 상한
    - 비밀 항목 키(`webhooksecret`, `privatekey`, `password`, `dburl` 등, `db`·`database` 구역)는 값을 읽지 않고 대신 쓸 환경변수를 안내
    - P1 `Configuration`은 수정하지 않음. `ConfigLoader`의 반환형이 `LoadedConfiguration`으로 바뀌므로 P1 호출부(`CliPipeline`)와 P1 Property 37 테스트를 함께 고침
    - _Requirements: 3.5, 3.6, 3.7, 13.4, 18.3, 19.1, 19.2, 19.4_
  - [ ] 3.2 `ConfigPrinter`에 P2 항목 출력 추가 (P1 항목 뒤, 문자열은 큰따옴표)
    - _Requirements: 19.6_
  - [ ]* 3.3 Property 27: 설정 round-trip 테스트와 설정 예시 테스트(기본값, 범위 밖 값을 한 번에 보고, Severity_Threshold 대문자 거부, 설정 파일의 비밀 항목 거부)
    - **Validates: Requirements 19.6**

- [ ] 4. 서버 비밀정보 치환 (C. 선머지 대상 아님. 트랙과 병렬)
  - [ ] 4.1 `support.SecretRegistry` 구현 (불변 `SecretMasker`를 `volatile`로 보관하고 추가할 때마다 교체, 빈 값·공백 값 제외). P1 `MaskingPrintStream`이 고정 `SecretMasker` 대신 이 레지스트리의 현재 마스커를 보게 바꿈
    - App 개인 키 등록 형태: 환경변수 원문(`\n` 이스케이프), `\n` 변환본, `\r\n` 변환본, 16자 이상인 base64 줄 각각
    - `PRLENS_DB_URL` 비밀번호 추출: `user:pass@`와 `password=` 값, 각각 원문과 URL 디코딩본
    - Installation_Token은 최근 10,000개까지 보관
    - _Requirements: 18.1, 18.2_
  - [ ] 4.2 `MaskingLayoutEncoder`와 서버용 `logback-server.xml` 작성 (메시지와 스택 전체를 문자열로 만든 뒤 치환, 모든 appender에 적용), Spring·Tomcat 요청 로깅 끄기, `System.out`/`err` 감싸기. P1의 CLI용 `logback.xml`도 같은 인코더를 쓰게 고침
    - Spring Boot는 클래스패스에 `logback.xml`이 있으면 그것을 먼저 쓰므로 서버 설정을 `logback-spring.xml`로 두지 않음. `ServerCommand`가 `logging.config=classpath:logback-server.xml`을 지정(12.2). 서버를 띄워 Review_Run ID가 든 info 로그가 나오는지 확인
    - _Requirements: 18.2, 18.4_
  - [ ] 4.3 `server` 전역 오류 처리 구현 (1.1에서 옮긴 `ApiExceptionHandler`를 확장. 새 advice를 만들지 않음. 처리되지 않은 예외는 `ErrorResponse(INTERNAL_ERROR)` 500, 클래스 이름·예외 메시지·스택 없음, `server.error.include-*` 끄기, `message`는 레지스트리 치환)
    - 예시 테스트: 전역 500과 `/error` 응답이 `ErrorResponse` 형식이고 내부 정보를 담지 않음
    - _Requirements: 18.6_
  - [ ]* 4.4 Property 26: 치환 후 비밀값이 남지 않는지 테스트
    - **Validates: Requirements 11.10, 18.2, 18.7**

- [ ] 5. Checkpoint: 공유 부분(작업 1~4) 완료 확인
  - `./gradlew test`(ArchUnit 포함)가 통과하는지 확인하고, 소요 시간을 기록합니다(Stop hook 제한 540초. 넘으면 느린 속성의 `tries`를 낮춤). 질문이 있으면 사용자에게 묻습니다. 트랙 시작 조건은 이 Checkpoint가 아니라 선머지(1.1, 2.1~2.4, 2.6, 2.7)의 머지입니다.

### T1 webhook (A)

- [ ] 6. 서명 검증과 본문 읽기
  - [ ] 6.1 `SignatureVerifier` 구현
    - 원문 `byte[]`만 사용, `HEADER_MISSING` → `PREFIX_MISSING` → `MALFORMED`(64자 16진수, 대소문자 무시) → `MISMATCH` 판정, `Mac`은 호출마다 생성하거나 `ThreadLocal`에 둠, 비교는 `MessageDigest.isEqual`
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 2.6_
  - [ ]* 6.2 Property 2: 서명 round-trip 테스트
    - **Validates: Requirements 2.1, 2.3, 2.7**
  - [ ]* 6.3 Property 3: 변조와 다른 비밀값 거부 테스트
    - **Validates: Requirements 2.4, 2.8**
  - [ ] 6.4 `BoundedBodyReader` 구현 (25MB, `Content-Length` 초과 시 본문을 읽지 않음, 없거나 작게 표시되면 상한 + 1바이트까지만 읽음)
    - spike 3(Tomcat `max-swallow-size`, 컨테이너 요청 크기 상한, content type) 결과 반영
    - 예시 테스트: 413(`Content-Length` 있음·없음). Property 1은 본문을 읽은 뒤의 처리만 보므로 요구사항 1.3은 여기서 검증
    - _Requirements: 1.3_

- [ ] 7. payload 해석과 허용 저장소
  - [ ] 7.1 `PullRequestPayloadParser` 구현
    - Required_Fields 경로(`repository.id`, `repository.full_name`, `pull_request.number`, `pull_request.head.sha`, `pull_request.base.sha`, `installation.id`) 검사 후 누락 필드 이름을 모두 반환
    - head·base SHA는 `^[0-9a-fA-F]{40}$` 검사 후 소문자로, `title`이 없으면 `""`, `draft`는 읽지 않음, `full_name`은 `RepoNames`(2.3)로 검사
    - _Requirements: 1.7, 1.8, 1.13, 5.7_
  - [ ] 7.2 `config.AllowedRepositories` 구현 (`Locale.ROOT` 소문자 집합, 빈 집합이면 항상 거부, `private` 값 무시. 서버와 CLI `--publish`가 함께 쓰므로 `config` 패키지에 둠)
    - _Requirements: 3.1, 3.2, 3.3, 3.7, 3.8, 3.9_
  - [ ]* 7.3 Property 4: 허용 저장소 판정이 소문자 비교 모델과 같은지 테스트
    - **Validates: Requirements 3.1, 3.3, 3.7, 3.8, 3.9**

- [ ] 8. `JobRunner` 등록과 실행기
  - [ ] 8.1 `JobRunner.register` 구현
    - `inspectDedupe(key, maxAttempts, staleAfter)`로 먼저 판정(`Duplicate`, `MaxAttemptsReached`면 반환) → CAS로 대기 수 카운터를 올림(가득 차면 `QueueFull`) → `registerAutomatedRun` → `Created`이면 실행기에 제출, 그 외 결과나 예외면 카운터를 내림. 중복 요청이 대기열 용량을 차지하지 않게 판정을 먼저 함
    - 실행기에 넣을 작업은 `ReviewJobFactory` 인터페이스로 받음(구현은 11.3). 그래야 8.1이 11.3을 기다리지 않음
    - 저장 예외는 `StoreUnavailableException`으로 감쌈. `NewReviewJob`, `RegisterResult` 작성
    - _Requirements: 1.2, 1.11, 1.12, 4.1, 4.12, 4.13, 5.1, 5.2, 5.4, 5.5, 5.8, 5.10_
  - [ ] 8.2 실행기 구현 (`ThreadPoolExecutor` + 무제한 FIFO 큐, 실행 시작 시 카운터를 내림, `try/catch (Throwable)`로 격리하고 `failed`/`internal` 기록, 복구 전용 `enqueueRecovered`·`enqueuePublishOnly`는 상한 검사 없음)
    - _Requirements: 4.6, 4.9, 4.12_
  - [ ]* 8.3 Property 5(Dedupe_Key마다 Active_Run 하나), Property 6(도착 순서 무관), Property 7(시도 번호와 최대 시도 횟수) 테스트 (`InMemoryReviewStore` 사용)
    - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 5.13, 16.6**

- [ ] 9. webhook 요청 처리
  - [ ] 9.1 `WebhookProcessor.handle` 구현
    - 설계 "요청 처리 순서와 응답" 표의 순서를 따름: 크기 → 서명 → 이벤트 → JSON → action → 필드 → 허용 저장소 → `closed`/Target_Action 분기 → `FeedbackCollector.requestRefresh` → `JobRunner.register`
    - 응답 형식은 `WebhookAck(status, reason, runId)`, 오류는 `ErrorResponse`(설계 표의 `code`). 서명 실패 로그에는 delivery ID(없으면 `없음`), 실패 종류, 수신 시각만 남기고 서명 헤더와 본문은 남기지 않음
    - _Requirements: 1.2, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.11, 1.12, 2.5, 3.2, 4.13, 5.2, 5.5, 5.6, 5.9, 15.3, 18.4_
  - [ ] 9.2 `WebhookController` 구현 (`POST /webhooks/github`, `HttpServletRequest`에서 원문 바이트를 직접 읽음, 진입 시 `Clock`으로 수신 시각 기록)
    - 요청 전체 데드라인 3초: DB 문장을 보내기 전마다 남은 시간으로 `statement_timeout`을 다시 설정(최대 1초), 커밋 직전에 마지막 검사, **커밋 뒤에는 데드라인을 넘겨도 항상 202**. 본문 읽기는 읽기 호출마다 남은 시간 확인
    - _Requirements: 1.1, 1.10, 2.1_
  - [ ]* 9.3 Property 1: 첫 결정 단계에서 응답하는지 테스트
    - **Validates: Requirements 1.2, 1.4, 1.5, 1.6, 1.7, 1.8, 2.4, 2.6, 3.2**
  - [ ]* 9.4 webhook 예시 테스트
    - `ping` 200, 각 무시 사유, 400 필드 이름 목록, 413(`Content-Length` 있음·없음), 503(대기열 가득·저장소 오류) 뒤 Review_Run 없음, delivery ID null, draft PR, 서명 실패 로그 필드(로그 캡처). 서명된 픽스처(26.1)를 씀
    - _Requirements: 1.3, 1.4, 1.5, 1.8, 1.12, 1.13, 2.5, 4.13_

- [ ] 10. GitHub App 인증
  - PR 경계: 10.1~10.3 / 10.4 / 10.5~10.7 (10.3이 머지돼야 T3의 20.1이 시작)
  - [ ] 10.1 `AppJwtSigner` 구현 (RS256, `iat = now − 60s`, `exp = now + 9m`, `iss = GITHUB_APP_ID 값`, 개인 키의 `\n` 이스케이프 변환, 서명한 JWT는 `SecretRegistry`에 등록)
    - spike 5(PEM 종류, JWT 라이브러리) 결과 반영
    - _Requirements: 6.1, 18.2_
  - [ ] 10.2 `InstallationTokenProvider` 구현
    - installation ID별 캐시, 남은 유효 시간이 5분 이상이면 재사용하고 아니면 ID별 잠금 안에서 다시 확인한 뒤 발급
    - `invalidate`는 캐시의 토큰이 거부된 토큰과 같을 때만 지움. 발급 호출에 P1 `RetryExecutor` 적용, 발급한 토큰은 `SecretRegistry`에 등록
    - spike 6(응답 필드, 기본 유효 기간) 결과 반영
    - _Requirements: 6.2, 6.3, 6.6, 18.2_
  - [ ] 10.3 `InstallationCredentials`와 `HttpGitHubClient`의 401 한 번 재발급 구현 (`RefreshableCredentials`일 때만 동작, 두 번째 401은 `GitHubAuthException`, P1 PAT 동작은 그대로. PAT 경로의 `GitHubApiException(401)`도 오류 종류 `github_auth`로 매핑)
    - 생성자로 받은 `CancellationToken`(2.6) 처리도 이 PR에 넣음: 호출 전에 토큰을 확인하고 호출 중에는 중단 훅을 등록. 11.4의 제한 시간 취소가 이 토큰으로 GitHub 호출을 끊음(spike 4 결과 반영)
    - _Requirements: 4.7, 6.4, 6.5_
  - [ ] 10.4 `GitHubClientFactory` 구현 (`forInstallation(installationId, cancellationToken, retryExecutor)`로 `GitHubApi` 반환: 공유 `HttpClient`, `InstallationCredentials`, `AppPrincipalResolver`, `PublishGitHubClientProvider` 구현. `webhook.JobScope`를 받지 않음. 골격은 2.6의 스텁으로 먼저 만들 수 있고 20.1·20.2가 끝나면 완성)
    - _Requirements: 6.2, 16.3_
  - [ ]* 10.5 Property 8: Installation_Token 재사용 판정 테스트
    - **Validates: Requirements 6.2, 6.3**
  - [ ]* 10.6 GitHub App HTTP 수준 테스트 (JWT `iat`/`exp`/`iss`, 401 한 번 재발급 뒤 성공과 재실패, 동시 401에서 발급 1회)
    - _Requirements: 6.1, 6.4, 6.5_
  - [ ] 10.7 실제 GitHub App 스모크 (수요일. 환경변수가 있을 때만 도는 수동 실행. CI에서는 실행하지 않음)
    - 실제 App으로 Installation_Token을 발급받고, 테스트 PR에 이슈 코멘트를 하나 만들고 수정. 게시 주체의 `user.id`·`user.login`이 spike 10의 결과와 같은지 확인
    - 결과를 PR 설명이나 `docs/spikes/`에 기록
    - _Requirements: 6.1, 11.1_

- [ ] 11. `ReviewJob` 실행
  - PR 경계: 11.1~11.2 / 11.3~11.5 (T2의 18.3이 11.2를 씀)
  - [ ] 11.1 `JobScope`와 `execution` 구현 (제한 시각, 종료 상태 CAS `RUNNING → FINISHING | CANCELLED`, `JobRetryListener`(P1 `onRetry`의 `Duration`을 ms로 합산), `DeadlineSleeper`, `UsageAccumulator`). `webhook`에는 `JobScope`, `JobReviewListener`(P1 `ReviewListener` 구현, Review_Run ID를 담은 서버 로그), 경고를 서버 로그로 보내는 `WarningSink`
    - _Requirements: 4.7, 4.10, 17.3_
  - [ ] 11.2 작업 전용 LLM 데코레이터 구현(`execution` 패키지): `CancellableLlmClient`, `UsageRecordingLlmClient`. 체인은 `RetryingLlmClient(UsageRecordingLlmClient(CancellableLlmClient(공유 클라이언트)))`. 실패한 Review_Run의 비용은 응답마다 P1 `CostCalculator.estimate`로 계산해 반올림한 값을 더함
    - _Requirements: 4.7, 8.2, 8.3_
  - [ ] 11.3 `ReviewJob` 구현 (설계 실행 순서 1~7단계)
    - `markRunning`이 false면 종료 → PR·컨텍스트 조회(`PrFetcher.fetch(repo, number)`) → head가 Dedupe_Key와 다르면 Claude 호출 전에 `failed`/`head_moved`(G-1), base가 다르면 경고 로그를 남기고 가져온 값을 `ReviewCompletion.baseSha`로 저장(G-2) → 작업별 `ReviewEngine` → `completeRun`
    - 비용 상한 초과 경고, 저장이 실패하면 제한 시간 타이머를 취소하고 `running` 그대로 두고 로그 → `CommentPublisher.publish` → `recordPublished` → 120초 초과 경고
    - 실패하면 설계의 예외 대응표로 Run_Error_Kind를 정해 `failRun`을 부르고 실패 게시를 요청. 게시만 하는 복구 작업은 6단계만 실행
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.11, 4.14, 7.15, 8.2, 8.3, 8.4, 17.4_
  - [ ] 11.4 제한 시간과 취소 구현 (`ScheduledExecutorService`로 `scope.cancel(TIMEOUT)` 예약. 11.3 뒤에 구현)
    - 작업자는 `completeRun`과 `failRun` 양쪽 모두 앞에서 `FINISHING` CAS를 거침. 타이머가 `CANCELLED` CAS에 성공하면 토큰 표시 → 중단 훅 → 인터럽트를 하고, **타이머 쪽이** `failRun(timeout)`과 실패 게시 요청을 수행. 작업자는 CAS 실패를 보고 결과나 예외를 버림
    - GitHub 호출의 취소는 `HttpGitHubClient`가 받은 `CancellationToken`으로 처리(구현은 10.3). Claude 호출을 끊으려고 `llm` 패키지를 고치지 않음(요구사항 16.4). 데코레이터로 끊을 수 없으면 응답을 버림(G-10)
    - spike 4(진행 중 HTTP 호출 중단) 결과 반영. 호출을 끊을 수 없으면 G-10으로 올림
    - _Requirements: 4.7_
  - [ ]* 11.5 `ReviewJob` 테스트
    - Property 10(usage는 받은 응답의 합)
    - 예시 테스트: 작업자 1개 FIFO, 예외 격리, 제한 시간(짧은 값 + 가짜 `Clock`), `head_moved`, 120초 초과 경고
    - **Validates: Requirements 8.1, 8.2, 8.3**
    - _Requirements: 4.6, 4.7, 4.9, 17.4_

- [ ] 12. 재시작 복구와 서버 시작
  - PR 경계: 12.1 / 12.2~12.3
  - [ ] 12.1 `StartupRecovery` 구현 (`SmartInitializingSingleton`: `markRunningAsInterrupted` → `listQueuedRuns` → `enqueueRecovered` → `listUnpublishedCompletedRuns` → `enqueuePublishOnly`)
    - spike 2(웹 서버 커넥터보다 먼저 끝나는지) 결과 반영
    - _Requirements: 4.8, 4.14, 4.15_
  - [ ] 12.2 `ServerStartupValidator`, `PrLensServerApplication` 빈 조립, `prlens server` 명령(2.6의 `ServerCommand`가 `SpringApplication.run` 호출)
    - 시작 순서: 설정 검증 → 환경변수 검증(`GITHUB_APP_ID`, `GITHUB_APP_PRIVATE_KEY`, `GITHUB_WEBHOOK_SECRET`, `ANTHROPIC_API_KEY`, `PRLENS_DB_URL`의 누락·빈 값·공백 값·PEM 오류를 모아 이름만 출력, 0이 아닌 코드로 종료) → 비밀값 등록 → `DbMigrator` → 경고(빈 허용 목록, 인증 없는 Query_API, 루프백이 아닌 주소) → 복구 → 포트 열기
    - `flyway-core`만 쓰고 자동 설정 모듈은 넣지 않음(spike 1), `WebServerFactoryCustomizer`로 `server.address` 설정, 설정 파일은 시작할 때 한 번만 읽음
    - DB 풀: HikariCP, 최대 크기 = 작업자 수 + 4. 커넥션 획득 1초 제한은 webhook 요청 경로에만. 포트 8080. 종료 시 진행 중 작업은 기다리지 않음(다음 시작 때 `interrupted`)
    - `ServerCommand`가 `logging.config=classpath:logback-server.xml`을 지정(4.2)
    - 조립이 끝나면 서버를 띄워 smee로 전달된 실제 `pull_request` 이벤트 하나가 서명 검증을 통과하고 202를 받는지 확인(사람이 실행. 26.6 리허설 전의 첫 종단 확인)
    - _Requirements: 2.1, 3.4, 6.8, 7.10, 7.11, 10.11, 10.12, 19.5_
  - [ ]* 12.3 복구 테스트 (세 경로, 게시만 하는 복구가 LLM을 부르지 않음, 복구가 `interrupted`를 다시 등록하지 않음, 시작 검증 이름 목록, `\n` 이스케이프 키, 빈 허용 목록 경고와 비루프백 주소 경고)
    - _Requirements: 3.4, 4.8, 4.14, 4.15, 6.8, 10.12_

- [ ] 13. Checkpoint: T1 완료 확인
  - T1 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### T2 store·query (B)

- [ ] 14. 메모리 구현과 지문 (14.1과 14.3은 화요일까지 먼저 머지, T1·T3가 사용)
  - [ ] 14.1 `store.FindingFingerprint.of(Finding)` 구현
    - 필드: 정규화 `file`, `category` JSON 값, `basis.ref ?? ""`, `message.strip()`. 각 필드의 U+0000을 U+FFFD로 바꾸고 `\u0000`으로 이어 붙인 뒤 SHA-256 소문자 16진수
    - D-7 결정 대기, 제안 구성으로 진행
    - _Requirements: 12.4, 12.5_
  - [ ]* 14.2 Property 17: Finding_Fingerprint 테스트
    - **Validates: Requirements 12.4, 12.5**
  - [ ] 14.3 `store.memory.InMemoryReviewStore` 구현
    - 모든 공개 메서드를 `ReentrantLock` 하나 안에서 실행, DB와 같은 상태 전이 가드·CHECK 검사·정렬 비교자(마지막 동점 키 `id`)
    - `toLowerCase(Locale.ROOT)`, 마이크로초 절삭, U+0000 치환과 경고(G-4), 오래된 `actions` 실행 정리(요구사항 20.10), `line_comment_reused` 기록 때 Feedback_State 복사(요구사항 15.6), 테스트 훅 `failNextCall`
    - 운영 코드(`--publish`)에서도 쓰므로 `src/main`에 둠
    - _Requirements: 5.3, 7.13, 7.17, 15.5, 15.6, 16.6_
  - [ ]* 14.4 `ReviewStoreContract` 추상 테스트와 Property 9(저장 round-trip)의 메모리 구현 적용
    - **Validates: Requirements 7.8, 7.16, 7.17, 17.3**

- [ ] 15. DB 스키마와 마이그레이션
  - [ ] 15.1 `db/migration/V1__init.sql` 작성 (설계 DDL: 테이블 4개(`excluded_file_details` 포함), CHECK 제약, 부분 유일 인덱스 `ux_review_run_active`·`ux_review_run_attempt`, 조회·복구 인덱스)
    - _Requirements: 5.3, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.9, 18.5_
  - [ ] 15.2 `store.db.DbMigrator`, `DbConnector` 구현 (Flyway API 직접 호출, 서버·CLI 공용, 연결 제한 10초, 실패 시 실패 버전과 치환한 메시지)
    - spike 7(실패 버전 API, 동시 migrate) 결과 반영
    - _Requirements: 7.10, 7.11, 7.12_
  - [ ] 15.3 DB 예외 변환 구현 (`SQLException`·`DataAccessException`·Flyway 예외 → `StoreException(kind, maskedMessage)`, 원인 예외를 붙이지 않음, 원래 예외는 debug 로그로만 남김)
    - _Requirements: 7.14, 18.2_
  - [ ]* 15.4 마이그레이션 통합 테스트 (`@Tag("docker")`. 빈 DB 적용, 재실행해도 변경 없음, 깨진 `V2`의 실패 버전 출력과 되돌림, 서버·CLI 동시 migrate)
    - _Requirements: 7.10, 7.11_

- [ ] 16. `JdbcReviewStore`
  - PR 경계: 16.1 / 16.2 / 16.3~16.4
  - [ ] 16.1 `registerAutomatedRun`, `inspectDedupe` 구현
    - 한 트랜잭션(READ COMMITTED, `lock_timeout`·`statement_timeout` 1초): `repository` upsert → `pull_request` 삽입 후 `FOR UPDATE` → 작업 제한 시간보다 오래된 `actions`의 `queued`/`running`을 `failed`/`interrupted`로 → Active_Run 조회 → `failed` 수와 최대 시도 번호 조회(`cli` 제외) → `queued` 삽입과 `pull_request` 갱신
    - `23505`가 나면 되돌린 뒤 Active_Run 조회만 다시 실행
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.8, 5.10, 5.11, 7.3, 7.5, 20.10_
  - [ ] 16.2 쓰기 메서드 구현 (`markRunning`, `completeRun`(결과·usage·시각과 Finding 전부를 1 트랜잭션에), `failRun`, `markRunningAsInterrupted`, `saveCliRun`, `recordPublished`, `markSuperseded`, `recordSummaryComment`, `recordPublishError`, `recordFindingOutcomes`, `updateFeedback`)
    - 조건부 갱신 가드, 트랜잭션 경계 표, U+0000 치환(G-4), 비용은 `NUMERIC`으로 scale 보존, `completeRun`은 가져온 base SHA로 `base_sha` 갱신(G-2), `recordFindingOutcomes`는 재사용한 Line_Comment의 기존 Feedback_State 복사
    - JSONB 컬럼 네 개(`excluded_files`, `excluded_file_details`, `incomplete_reasons`, `incomplete_details`)는 P1 `ResultCodec`으로 `ReviewResult` 전체를 직렬화한 트리에서 해당 노드를 꺼내 저장하고, 읽을 때 다시 끼워 역직렬화
    - _Requirements: 4.2, 4.4, 4.5, 4.8, 7.8, 7.13, 7.17, 8.1, 8.2, 8.3, 9.1, 13.3, 14.2, 15.5, 15.6_
  - [ ] 16.3 조회 메서드 구현 (`findRun`, `findFindings`, `findPullRequest`, `listRepositories`, `listPullRequests`(`JOIN LATERAL`), `listRuns`, `listFindings`, `listFeedbackTargets`, 복구용 목록)
    - `count(*) OVER ()`로 전체 수, 대소문자 무시 저장소 조회는 `updated_at`이 가장 최근인 행(G-9)
    - 예시 테스트: 이름 변경으로 같은 소문자 이름의 저장소가 둘일 때 최근 행을 고름(`@Tag("docker")`)
    - _Requirements: 4.8, 4.14, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  - [ ]* 16.4 DB 통합 테스트 (`@Tag("docker")`)
    - Property 9(DB 구현, `ReviewStoreContract` 상속), Property 14(메모리 구현과 DB 구현의 동등성)
    - 예시 테스트: 동시성(스레드 16개로 같은 Dedupe_Key 등록 50회 반복, `23505` 경로), 트랜잭션 되돌림, CHECK 제약, 비밀번호가 든 잘못된 URL로 `StoreException` 치환, 오래된 `actions` 실행 정리(`inspectDedupe`가 Active로 치지 않고 `registerAutomatedRun`이 `failed`로 바꿈, 요구사항 20.10)
    - **Validates: Requirements 5.3, 7.8, 7.16, 7.17, 16.6, 16.9, 17.3**
    - _Requirements: 20.10_

- [ ] 17. Query_API
  - [ ] 17.1 조회 DTO 작성 (`RepositoryDto`, `PullRequestDto`, `RunDto`, `FindingDto`, 목록 봉투. camelCase 필드, 열거형은 소문자 snake_case, 시각은 ISO-8601 UTC)
    - _Requirements: 10.10_
  - [ ] 17.2 `QueryController`와 `query` 전용 오류 처리 구현
    - 엔드포인트 4개(`/api/v1/` 아래), `limit = 100`
    - 경로 변수를 `String`으로 받아 검사하고 잘못된 이름을 모두 모아 400(`ErrorResponse` `INVALID_INPUT`, `details`에 이름)
    - 404는 `NOT_FOUND`와 `details`에 대상 종류, 500은 `INTERNAL_ERROR`와 고정 문구 "저장소 조회 오류", 조회 제한 시간 5초. Stored_Finding 0개면 빈 목록
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.13, 10.14, 14.4_
  - [ ] 17.3 `docs/api/openapi.yaml` 작성 (OpenAPI 3.1, 엔드포인트 4개, 경로 변수 패턴, DTO 스키마 `additionalProperties: false`, 400/404/500 `ErrorResponse`와 `code` 값, Run_Error_Kind·Publish_Error_Kind 열거)
    - _Requirements: 10.9_
  - [ ] 17.4 `QueryApiContractIT` 작성 (`@Tag("docker")`. 문서 파싱, 모든 엔드포인트의 200/400/404/500 응답을 스키마로 검증, 매핑 경로 집합과 `paths` 비교)
    - spike 8(검증 도구) 결과 반영
    - _Requirements: 10.9_
  - [ ]* 17.5 Property 11(비용 합), Property 12(목록 정렬과 전체 수), Property 13(Finding 조회 round-trip) 테스트 (`InMemoryReviewStore` + MockMvc로 기본 `test`에서 실행. DB 구현과의 동등성은 Property 14가 맡음)
    - **Validates: Requirements 8.5, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.10, 10.13, 10.15**

- [ ] 18. CLI `--save`
  - [ ] 18.1 `cli.P2ExitCodes.resolve(p1Code, sideEffectFailed)` 구현 (`p1Code`가 2나 1이면 그대로, 부수 작업이 실패하면 4, 아니면 `p1Code`)
    - D-5 결정 대기, 제안값으로 진행
    - _Requirements: 9.5, 9.7_
  - [ ] 18.2 `--save` preflight 구현 (`PRLENS_DB_URL` 검사 → DB 비밀번호 등록 → 연결·마이그레이션 → 연결 닫기, 실패하면 외부 API 호출 없이 종료 코드 2. `--save`가 없으면 DB 코드를 만들지 않음)
    - _Requirements: 7.12, 9.2, 9.3, 9.6, 9.8_
  - [ ] 18.3 저장 흐름 구현
    - CLI 조립에 `UsageRecordingLlmClient`를 끼움, `repositoryId` 조회(G-5), `saveCliRun`(새 연결)
    - stderr에 Review_Run ID 또는 저장 오류 출력. 결과 생성 실패면 `failure`로 저장하고, PR 스냅샷 전 실패는 저장하지 않음(G-6)
    - _Requirements: 9.1, 9.4, 9.5, 9.7, 9.9_
  - [ ]* 18.4 CLI `--save` 테스트 (설계 종료 코드 표 전 행, stdout이 `--save` 유무와 관계없이 같음, `--save`가 없으면 DB 연결 없음, `P2ExitCodes` 전수 표)
    - _Requirements: 9.2, 9.6, 9.7, 9.8, 9.9_

- [ ] 19. Checkpoint: T2 완료 확인
  - T2 테스트(Testcontainers 포함)가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### T3 publish (C)

- [ ] 20. GitHub 게시 호출
  - PR 경계: 20.1 / 20.2~20.3
  - [ ] 20.1 `HttpGitHubClient`에 `PublishGitHubClient` 메서드 구현 (10.3 머지 후 시작)
    - 2.6의 스텁을 채움: `principal()`(`PrincipalResolver`에 위임), `headSha`, `repositoryId`, 리뷰·이슈 코멘트 목록(`per_page=100`, `Link rel="next"`), `createReview`(event `COMMENT`, side `RIGHT`), 이슈 코멘트 생성·수정, 리액션 목록
    - 422는 `GitHubUnprocessableException`(치환한 본문, 최대 8KB). 요청 로그에는 메서드·경로·상태만 남김
    - 생성 요청(`createIssueComment`, `createReview`)은 rate limit 응답만 재시도하고, 5xx·연결 실패·제한 시간 초과는 다시 보내지 않고 `PostOutcomeUnknownException`
    - 예시 테스트(HTTP 수준): 페이지네이션, 422 본문 보존, 생성 요청이 5xx에서 재전송되지 않음
    - _Requirements: 11.1, 11.2, 11.15, 12.1, 12.2, 15.2, 18.4_
  - [ ] 20.2 게시 주체 식별 구현 (`AppPrincipalResolver`: `GET /app`의 slug와 `GET /users/{slug}[bot]`의 ID, 프로세스 캐시 / `TokenPrincipalResolver`: `GET /user`의 ID와 login, 403이고 `GITHUB_ACTIONS=true`이면 `github-actions[bot]`). 작성자 비교는 숫자 ID가 기본, 없으면 login. 10.1(JWT)과 10.3 뒤에 구현하고 생성자는 2.6에서 정한 형태를 그대로 씀
    - spike 10 결과 반영. 비교 기준이 바뀌면 `GitHubPrincipal.isAuthor`만 수정
    - 예시 테스트: 숫자 ID 비교, ID가 없을 때 login 비교, Actions 토큰의 403 처리
    - _Requirements: 11.1, 12.4, 15.2_
  - [ ] 20.3 `GitHubPages.readAll(fn)` 헬퍼 구현 (`hasNext`가 거짓일 때까지 모든 페이지 읽기)
    - _Requirements: 11.2, 12.4, 15.2_

- [ ] 21. 게시 계획과 본문 렌더링
  - PR 경계: 21.1~21.2 / 21.3~21.6
  - [ ] 21.1 `PublishPlanner.plan`과 `OutcomeResolver.resolve` 구현 (`SeverityOrder.rank`, 역할 판정 표, Inline_Eligible Publishable_Finding 안에서 같은 지문 묶음 만들기, 422 외 실패는 `not_published`(G-11))
    - _Requirements: 11.3, 12.5, 12.10, 12.11, 12.12, 12.13, 13.1, 13.2, 13.5, 19.3_
  - [ ]* 21.2 Property 15(Publishable_Finding 부분 목록), Property 16(하한을 올리면 게시 대상이 늘지 않음) 테스트
    - **Validates: Requirements 13.1, 13.2, 13.5, 13.6**
  - [ ] 21.3 `UntrustedText.sanitize` 구현 (멘션 앞 `@` 뒤에 U+200B, `<!--`를 `<\u200B!--`로, 닫히지 않은 코드 펜스 보완, 목록 항목의 `message`는 한 줄로)
    - 예시 테스트: 멘션, 마커 위조, 닫히지 않은 펜스
    - _Requirements: 11.10, 12.3_
  - [ ] 21.4 `SummaryRenderer` 구현
    - 본문 구성: 마커 → 불완전·비용 경고 → 제목 → 요약(요약 전용 안내) → 지적 수와 하한 미만 수, 게시 실패로 표시하지 못한 수(G-11) → 라인 밖 지적 → 사용량. `failed`이면 실패 본문
    - 65,536자 제한: 라인 밖 지적 개수를 이진 탐색으로 줄이고, 부족하면 서로게이트 쌍을 나누지 않게 `summary`를 자름
    - 필드 치환 → sanitize → 조립 → 본문 전체 재치환. 줄바꿈 `\n`, 숫자는 `Locale.ROOT`
    - _Requirements: 8.4, 11.3, 11.4, 11.5, 11.7, 11.8, 11.10_
  - [ ] 21.5 Line_Comment 본문 렌더러 구현 (첫 줄 Finding_Marker, 심각도·카테고리·메시지, 값이 있을 때 제안·근거, 리액션 안내, 같은 길이 제한)
    - _Requirements: 12.3, 11.10_
  - [ ]* 21.6 Property 21(코멘트 본문 필수 항목), Property 22(Summary_Comment 길이 상한) 테스트
    - **Validates: Requirements 8.4, 11.3, 11.4, 11.5, 11.8, 12.3**

- [ ] 22. `CommentPublisher`
  - PR 경계: 22.1~22.2 / 22.3 / 22.4~22.7
  - [ ] 22.1 `PrPublishLocks` 구현 (PR별 공정 `ReentrantLock`과 사용자 수 카운트, 쓰는 스레드가 0이 되면 항목 삭제)
    - _Requirements: 14.3_
  - [ ] 22.2 Line_Comment 단계 구현
    - 게시 주체의 기존 마커로 `지문 → 가장 작은 ID` 맵을 만들어 재사용, 나머지는 `createReview` 한 번, 새 코멘트 ID는 리뷰의 코멘트 목록에서 마커로 대응
    - 422는 `RejectionParser`로 원인을 식별해 한 번만 다시 보냄(식별하지 못하면 모두 `github_rejected`), 422 외 실패는 `recordPublishError` 후 Summary 단계를 계속
    - `createReview`가 `PostOutcomeUnknownException`이면 마커를 다시 조회해 생겼으면 성공으로 처리, 없을 때만 한 번 더 보냄. 새 코멘트 ID 조회가 실패하면 PR 전체 코멘트 목록의 마커로 다시 대응
    - 이전 Line_Comment는 수정·삭제하지 않음
    - spike 9(422 본문 형식, 리뷰당 코멘트 수 상한) 결과 반영
    - _Requirements: 11.15, 12.1, 12.2, 12.4, 12.6, 12.7, 12.8, 12.9, 12.10, 12.11, 12.12, 12.14_
  - [ ] 22.3 Summary upsert 구현 (게시 주체가 쓰고 마커가 있는 코멘트 중 가장 먼저 만들어진 것을 수정, 없으면 생성, 수정이 404면 한 번 새로 생성, 생성이 `PostOutcomeUnknownException`이면 마커를 다시 조회해 없을 때만 한 번 더 생성, 재시도 후 실패하면 `recordPublishError`)
    - _Requirements: 11.1, 11.2, 11.6, 11.9, 11.12, 11.14, 11.15_
  - [ ] 22.4 `DefaultCommentPublisher.publish` 구현 (설계 순서 1~8단계)
    - 잠금 안에서 다시 조회 → `github_auth` 실패면 코멘트 없이 오류만 기록
    - 현재 head 조회와 형식 검사 → head가 바뀌었으면 `superseded`(`failed`이면 `StaleFailure`)
    - 주체 확인 → 계획 → 라인 → 요약 → `recordFindingOutcomes` 한 번 → 결과 반환. 이전 Publish_Error를 덮어쓰면 로그에 남김
    - 게시 뒤 저장이 실패하면 `PublishFailed(PublishError("store", null), summaryPosted)`
    - _Requirements: 6.7, 11.7, 11.9, 11.11, 14.1, 14.2, 14.3, 14.5, 14.6_
  - [ ]* 22.5 Property 18(게시 대상 Finding에만 Line_Comment), Property 19(재게시해도 Line_Comment 수 유지), Property 20(Summary_Comment는 PR마다 하나), Property 23(현재 head 기준) 테스트
    - **Validates: Requirements 11.1, 11.14, 11.15, 12.1, 12.4, 12.5, 12.6, 12.7, 12.13, 12.14, 12.15, 14.2, 14.8, 19.3**
  - [ ]* 22.6 게시 예시 테스트 (422 재게시와 식별 불가, 422 외 실패 뒤 Summary 계속, 라인을 먼저 게시하는 순서, `summary_listed` 기록, `github_auth` 게시 생략, PR별 직렬화에서 호출이 겹치지 않음, 게시 주체의 Summary_Marker 코멘트가 둘 이상일 때 가장 먼저 만든 것만 갱신, `createReview`가 처리 여부를 알 수 없게 실패했을 때 마커 재조회)
    - _Requirements: 6.7, 11.2, 11.15, 12.8, 12.9, 12.11, 14.3_

  - [ ] 22.7 `publish` 패키지의 ArchUnit 규칙 확인 (1.1에서 `allowEmptyShould(true)`로 넣어 둔 규칙이 `publish` 코드가 생긴 뒤에도 통과하는지 확인하고 `allowEmptyShould`를 뗌: `publish..`는 `github.PublishGitHubClient`, `PublishGitHubClientProvider`와 `github`의 레코드·예외에만 의존, `webhook..`은 `publish`의 인터페이스와 `PublishResult`에만 의존)
    - _Requirements: 16.2, 16.3_

- [ ] 23. `FeedbackCollector`
  - [ ] 23.1 `FeedbackRules.decide` 구현 (게시 주체의 리액션과 `+1`·`-1` 외 리액션을 뺀 뒤 판정, login이 null인 리액션은 셈)
    - _Requirements: 15.1, 15.2, 15.13_
  - [ ]* 23.2 Property 24: Feedback_State 판정이 참조 구현과 같은지 테스트
    - **Validates: Requirements 15.2, 15.13**
  - [ ] 23.3 `FeedbackSync.sync` 구현 (서로 다른 Line_Comment ID를 오름차순으로 순회 → 리액션 모든 페이지 → `updateFeedback`, 404나 재시도 후 실패는 `FailedLookup`에 담고 계속, `StoreException`이면 중단, 끝나면 상태별 개수)
    - _Requirements: 15.5, 15.6, 15.7, 15.8_
  - [ ] 23.4 `AsyncFeedbackCollector` 구현 (작업자 1개, `PENDING`/`RUNNING`/`RUNNING_DIRTY`로 같은 PR 요청 합치기, 대기 PR 1,000개 상한, 예외 격리)
    - 예시 테스트: 실행 중에 온 요청이 한 번 더 실행됨, 1,000개 상한에서 버리고 경고
    - _Requirements: 15.3_
  - [ ]* 23.5 Property 25: 채택/기각 갱신의 멱등성 테스트
    - **Validates: Requirements 15.5, 15.6, 15.8, 15.14**
  - [ ] 23.6 `prlens feedback sync <PR URL>` 명령 구현
    - preflight 순서: 인자 → 설정 → `PRLENS_DB_URL` → `GITHUB_TOKEN` → DB → 저장된 PR 확인. 여기까지 GitHub API를 호출하지 않음
    - 이어서 주체 확인 → sync → stderr에 개수와 실패 목록 출력, stdout은 비움. 종료 코드는 `P2ExitCodes`
    - 예시 테스트: preflight 실패별 종료 코드 2와 GitHub 미호출, 일부 실패 4, 전부 성공 0
    - _Requirements: 15.4, 15.9, 15.10, 15.11, 15.12_

- [ ] 24. GitHub Actions 대체 경로 `--publish`
  - [ ] 24.1 `--publish`와 `--publish --save` 흐름 구현
    - preflight: 인자 → 설정 → 환경변수 → 허용 저장소 → (`--save`) DB
    - 저장소는 `InMemoryReviewStore` 또는 `JdbcReviewStore`. PR 조회 → `repositoryId` → `principal()` → `registerAutomatedRun(ACTIONS, staleAfter = 작업 제한 시간)` → `markRunning`. `Duplicate`·`MaxAttemptsReached`이면 Claude 호출 없이 종료 코드 0
    - 리뷰 → `completeRun`/`failRun` → `publish` → `Published`이면 `recordPublished`. 저장이 실패하면 메모리 저장소로 옮겨 게시를 계속
    - 결과별 stderr 출력과 `P2ExitCodes`
    - _Requirements: 4.11, 20.1, 20.2, 20.3, 20.4, 20.5, 20.7, 20.8, 20.9_
  - [ ]* 24.2 `--publish` 테스트 (허용되지 않은 저장소는 외부 호출 없이 종료 코드 2, 중복이면 0, `superseded`는 실패로 치지 않음, 게시 실패는 1 또는 4)
    - _Requirements: 20.3, 20.4, 20.5, 20.8, 20.9_

- [ ] 25. Checkpoint: T3 완료 확인
  - T3 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### 컨텍스트 (컨텍스트 담당 C, 트랙과 병렬)

- [ ] 28. Claude Code 컨텍스트를 P2에 맞추기 (월요일, 1.1과 함께)
  - [ ] 28.1 rules, CLAUDE.md, reviewer, 스킬 수정 (`.claude/` 아래 수정은 승인을 묻는 대상)
    - `.claude/rules/backend/api-design.md`: `paths`가 `**/api/**`라 `webhook`, `query`, `server`에 로드되지 않음. `paths`를 이 패키지들로 바꾸고 내용을 P2에 맞춤(`/api/v1/`, `ErrorResponse`와 `code` 값. 페이지네이션과 `@Valid`는 D-16 결정에 따름. 메모 예시 교체). ADR 0008이 "P2 전에 고친다"고 적은 항목
    - `backend/CLAUDE.md`: 서버 실행 명령을 `prlens server`로(`bootRun`은 P1 작업 18.7 뒤에 CLI 진입점을 실행함), 패키지 목록에 P2 패키지 추가, "Spring 애너테이션은 `cli` 조립 코드에만"을 P2 규칙으로 교체
    - `.claude/rules/backend/testing.md`: Docker 태그 규칙(1.3의 jqwik 확인 결과 포함), webhook 픽스처 위치, P2 속성 태그 주석 예시, 가짜 구현 허용 목록에 `ReviewStore`(`InMemoryReviewStore`) 추가
    - `.claude/agents/reviewer.md`: "`cli` 밖의 Spring 애너테이션"을 위반으로 보는 항목을 P2 의존 규칙으로 교체. 그대로 두면 `server`, `webhook`, `query`, `store.db` 코드가 `/pr-ready`에서 모두 위반으로 잡힘
    - `.claude/skills/new-endpoint/SKILL.md`: 메모 패키지와 `@Valid` 기준이라 9.2, 17.2의 설계(직접 검증, `query` 전용 오류 처리)와 반대로 이끔. PR Lens 기준으로 고침(ROADMAP이 "P2, P3 전"으로 적은 항목)
    - 비밀값 목록(루트 `CLAUDE.md`, `backend/CLAUDE.md`, `reviewer.md`)에 `GITHUB_APP_PRIVATE_KEY`, `GITHUB_WEBHOOK_SECRET`, `PRLENS_DB_URL`·`PRLENS_DB_PASSWORD` 추가
    - `.claude/settings.json`의 allow에는 `gradlew check`, `dockerTest`, `generateWebhookFixtures`, `bootJar`가 없어 실행할 때마다 승인을 묻습니다. 넣을지는 사람이 정합니다(권한 설정이므로 Claude가 임의로 고치지 않음)

### 통합 (전원, 목~금)

- [ ] 26. 픽스처, 통합 테스트, CI, 설치 문서
  - PR 경계: 26.1~26.2 / 26.3~26.4 / 26.5 (26.6은 실행 기록이라 코드 PR이 아님). 26.1~26.2는 목요일을 기다리지 않고 6.1 뒤, 9.4 전에 먼저 냄(담당은 킥오프에서 정함)
  - [ ] 26.1 서명된 webhook 픽스처와 생성기 작성 (9.4와 26.3이 씀)
    - `src/test/resources/fixtures/webhook/`에 헤더 JSON과 원문 본문 쌍: `opened`, `synchronize`, `reopened`, `closed`, `ping`, `signature_mismatch`, `repo_not_allowed`, `missing_fields`(필드별), `bad_head_sha`, `crlf_unicode`, `redelivery`(같은 delivery ID), `new_delivery_same_sha`(다른 delivery ID)
    - `WebhookFixtureGenerator`와 `backend/`에서 `./gradlew generateWebhookFixtures`(`build.gradle.kts`에 태스크를 추가하므로 사람의 승인 필요). 결과 파일을 커밋하고, CI에서 다시 생성해 커밋본과 비교
    - 픽스처 폴더를 `.gitattributes`에 `-text`로 지정(CRLF 픽스처의 서명이 깨지지 않게). 한 픽스처는 GitHub 문서의 공개 서명 예시 값과 비교
    - _Requirements: 16.8_
  - [ ] 26.2 픽스처 비밀정보 스캔에 PEM 헤더와 `ghs_` 접두어 추가 (P1 작업 20.3의 스캔이 `fixtures/` 아래를 모두 훑는지 확인. webhook 픽스처도 대상)
    - _Requirements: 16.8, 18.7_
  - [ ] 26.3 서버 조립 통합 테스트 (`@Tag("docker")`. DB와 가짜 `GitHubClient`/`LlmClient`로 서명된 픽스처를 webhook부터 게시까지 한 번 실행)
    - _Requirements: 1.1, 4.4, 11.1, 16.7_
  - [ ] 26.4 CI 구성 갱신 (Linux 러너의 `./gradlew check`에 OpenAPI 계약 검사와 픽스처 재생성 비교를 포함. Docker 테스트 분리와 OS별 실행 범위는 1.3에서 이미 설정)
    - _Requirements: 10.9, 16.2, 16.8_
  - [ ] 26.5 설치 문서 작성 (설계 "설치 문서에 적을 항목" 전부: App 권한과 등록 절차, 환경변수 목록, smee와 로컬 DB, `prlens server` 실행, 요약 코멘트가 둘 생길 수 있는 제한, 직렬화 한계, Actions 워크플로 예시와 fork 제한, 대체 경로에서는 저장되지 않음, 유실 이벤트 재전송 방법, 설정 원본이 경로마다 다름, 채택 수집 시점, force-push 복귀 한계, 저장소 이름 변경 시 재시작, `feedback sync` 실행 계정, outdated 코멘트)
    - 위치는 `docs/guide/prlens-setup.md`. `GITHUB_APP_ID`에 넣을 값(App ID 또는 client ID), 운영 서버는 한 대, Docker 없는 PC의 PostgreSQL 준비, Actions 워크플로를 쓰는 저장소의 `repositories.allowed`도 적음
    - _Requirements: 6.9, 11.13, 14.7, 20.6_
  - [ ] 26.6 실제 webhook 리허설과 응답 시간 측정 (목요일. 사람이 실행)
    - 0.1의 App과 smee로 실제 PR을 열어 요약 코멘트가 달리는지, 커밋을 추가하면 같은 코멘트가 갱신되는지 확인(ROADMAP 3주차 완료 조건)
    - 설계 Testing Strategy의 "응답 시간 측정 절차"로 `opened` 5회, `synchronize` 5회를 재고 결과를 3주차 회고에 기록. 120초를 넘으면 단계별 시간으로 원인을 찾음
    - _Requirements: 17.1, 17.2_

- [ ] 27. Final Checkpoint
  - 전체 테스트와 CI가 통과하는지 확인하고, `./gradlew test` 소요 시간이 Stop hook 제한(540초) 안인지 기록합니다. 질문이 있으면 사용자에게 묻습니다.

## Notes

- spike(design.md "3주차 spike 체크리스트")는 작업 0.2~0.4에서 수행합니다. 결과에 따라 6.4, 10.1, 10.2, 11.4, 12.1, 15.2, 17.4, 20.2, 21.4, 22.2, 24.1의 세부 구현이 달라집니다. spike 결과가 설계와 다르면 design.md를 먼저 고친 뒤 작업합니다.
- 요구사항 공백 G-1~G-13은 설계 제안대로 작업에 반영했습니다(G-1·G-2는 11.3, G-4는 14.3·16.2, G-5·G-6은 18.3, G-7은 14.3·16.1, G-9는 16.3, G-11은 21.1·21.4, G-12·G-13은 26.5). 요구사항에는 `(G-n 제안)` 표기로 옮겼고, 요구사항을 고치기로 하면 관련 작업도 함께 수정합니다.
- 결정 대기 D-13~D-18(요구사항 "결정 대기 항목")은 작업에 반영하지 않았습니다. 결정 전에는 이 항목들의 제안이 아니라 요구사항 문구대로 구현합니다(예: `interrupted`는 시도 횟수에 포함, 리액션에서 빼는 주체는 게시 주체). 결정되면 요구사항, 설계, 작업을 함께 고칩니다.
- D-2(DB와 테스트 전략, ADR-0006)와 D-8의 서버 담당은 제안값으로 대신할 수 없습니다. 1.2(의존성)와 0.1(환경 준비)이 이 결정을 기다리므로 킥오프 전에 확정합니다. D-13(게시 실패 복구)과 D-14(재전송과 `interrupted`)는 도그푸딩 중에 바로 드러나는 문제라 3주차 킥오프에서 먼저 정하는 것이 좋습니다. D-17(P3가 요구하는 조회 API 차이)은 17.1과 17.3을 시작하기 전에, D-15의 전환 기준은 수요일 스모크 전에 정합니다.
- 응답 시간 측정(요구사항 17.1·17.2)은 재시도 대기와 smee 전달 지연을 뺀 값입니다. ROADMAP 3주차 완료 조건의 "2분 안에"는 이 기준으로 판정합니다.
- 다음 항목은 코드 작업이 아니라 사람이 수행합니다: 환경 준비(0.1), 실제 App 스모크(10.7), 실제 webhook 리허설과 응답 시간 측정(26.6), 리뷰어 B의 사람 리뷰와 PR Lens 지적 비교 기록.
- **완료 조건 최소 경로와 밀리면 미룰 것**: 금요일 완료 조건(새 PR에 2분 안에 요약 코멘트, 커밋 추가 시 갱신)에 꼭 필요한 것은 0.1~0.4, 1.1~1.3, 2.1~2.4, 2.6, 2.7, 3.1, 4.1, 4.2, 6.1, 6.4, 7.1, 7.2, 8.1, 8.2, 9.1, 9.2, 10.1~10.4, 10.7, 11.1~11.3, 12.1(재시작 복구 중 `running` 정리와 `queued` 재등록), 12.2, 14.1, 14.3, 15.1~15.3, 16.1~16.3, 20.1~20.3, 21.1, 21.3, 21.4, 22.1, 22.3, 22.4, 26.6입니다. spike(0.2~0.4)는 이 경로의 6.4, 10.1, 15.2, 20.2, 21.4를 막고, 4.2의 `logback-server.xml`은 12.2의 서버 시작이 씁니다. 스펙 전체(요구사항 20개, 말단 작업 107개)를 3명이 한 주에 끝내기는 어려우므로, 일정이 밀리면 아래 순서로 미룹니다(ROADMAP "일정 위험과 완충": FR-11을 FR-6보다 먼저 줄임).
  1. `*` 표시 작업 전부(속성 테스트와 보조 테스트)
  2. FR-11 채택/기각 수집: 23 전부. `FeedbackCollector.NOOP`(2.4)으로 대신하고 채택률은 PR 설명의 수기 기록으로 모음
  3. Actions 대체 경로: 24 (webhook이 동작하면 필요 없음)
  4. 제한 시간 취소와 미게시 건 재게시: 11.4, 12.1의 `enqueuePublishOnly` 부분. 12.1의 나머지(`markRunningAsInterrupted`, `queued` 재등록)는 미루지 않음. 없으면 재시작 뒤 Active_Run이 남아 그 커밋은 재전송해도 `duplicate`가 됨
  5. OpenAPI 계약 검사: 17.4
- 완료 조건에는 들지 않지만 FR 충족에 필요해 미루지 않는 것: 라인 코멘트(21.5, 22.2, FR-10), CLI `--save`(18, FR-9), 조회 API와 OpenAPI 문서(17.1~17.3, 4주차 P3가 기다림). ROADMAP의 축소 순서(FR-14 → FR-11 → FR-6)에 없는 항목이므로, 줄이려면 ROADMAP을 먼저 고칩니다(PRD·ROADMAP 변경 승인 규칙).
- Query_API는 인증 없이 동작합니다(D-8). 12.2의 시작 경고와 기본 바인딩 `127.0.0.1`로 보완하며, 외부에 노출하려면 인증 추가가 먼저 필요합니다.

## Task Dependency Graph

0.1(환경 준비)은 킥오프 전에 끝냅니다. spike(0.2~0.4)는 0.1 뒤에 하고, 0.3의 Testcontainers 확인은 1.2 뒤입니다. 픽스처(26.1, 26.2)는 9.4보다 먼저 냅니다. 월요일 선머지는 1.1, 2.1~2.4, 2.6, 2.7입니다(wave 0~2). wave 3부터 세 트랙과 리드의 나머지 작업이 병렬로 진행됩니다. 같은 wave 안에서도 같은 사람이 맡은 작업은 번호 순서대로 합니다.

```json
{
  "waves": [
    { "id": 0, "tasks": ["0.1", "1.1"] },
    { "id": 1, "tasks": ["0.2", "0.4", "1.2", "1.3", "2.1", "2.3", "2.7", "28.1"] },
    { "id": 2, "tasks": ["0.3", "2.2", "2.4", "2.6", "4.1"] },
    { "id": 3, "tasks": ["2.5", "3.1", "4.2", "4.3", "6.1", "6.4", "7.1", "7.2", "8.2", "10.1", "11.1", "11.2", "14.1", "15.1", "17.1", "18.1", "20.3", "21.1", "21.3", "23.1"] },
    { "id": 4, "tasks": ["3.2", "4.4", "6.2", "6.3", "7.3", "8.1", "10.2", "14.2", "14.3", "15.2", "15.3", "17.3", "21.2", "21.4", "21.5", "22.1", "23.2", "23.3", "26.1"] },
    { "id": 5, "tasks": ["3.3", "8.3", "10.3", "14.4", "15.4", "16.1", "17.2", "18.2", "21.6", "22.2", "22.3", "23.4", "26.2"] },
    { "id": 6, "tasks": ["5", "9.1", "10.5", "10.6", "16.2", "20.1", "20.2", "22.4", "23.5"] },
    { "id": 7, "tasks": ["9.2", "9.3", "10.4", "10.7", "16.3", "18.3", "22.5", "22.6", "22.7"] },
    { "id": 8, "tasks": ["9.4", "11.3", "16.4", "17.4", "17.5", "18.4", "23.6", "24.1"] },
    { "id": 9, "tasks": ["11.4", "12.1", "19", "24.2"] },
    { "id": 10, "tasks": ["11.5", "12.2", "25"] },
    { "id": 11, "tasks": ["12.3"] },
    { "id": 12, "tasks": ["13"] },
    { "id": 13, "tasks": ["26.5"] },
    { "id": 14, "tasks": ["26.3", "26.4", "26.6"] },
    { "id": 15, "tasks": ["27"] }
  ]
}
```
