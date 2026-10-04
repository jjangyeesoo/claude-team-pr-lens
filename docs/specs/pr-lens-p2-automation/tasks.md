# Implementation Plan: PR Lens P2 자동화·저장

## Overview

[design.md](design.md)를 구현하는 작업 목록입니다. 순서는 "공유 경계 선머지(리드 A, 월요일) → T1·T2·T3 병렬 → 통합·CI"입니다. P1 코드(`model`, `github`, `pullrequest`, `context`, `review`, `llm`, `codec`, `config`, `support`)가 머지된 상태를 전제로 하며, `review`·`llm` 패키지는 수정하지 않습니다(요구사항 16.4).

- 언어와 도구: Java 17, Spring Boot 4, PostgreSQL + Flyway, JDBC(`JdbcClient`), Testcontainers, JUnit 5, jqwik, ArchUnit (D-2·D-4는 결정 대기, 설계의 권고안으로 진행)
- `*`가 붙은 하위 작업은 속성 기반 테스트·보조 테스트로, 일정이 밀리면 뒤로 미룰 수 있습니다. 요구사항이 테스트나 CI 검사 자체를 요구하는 작업(OpenAPI 계약 검사, 서명된 webhook 픽스처, ArchUnit 규칙)은 필수로 두었습니다
- 속성 테스트에는 `// Feature: pr-lens-p2-automation, Property N: <이름>` 태그 주석을 답니다
- 트랙 담당은 ROADMAP 3주차 기본안(T1 A, T2 B, T3 C)을 따릅니다
- 트랙 사이 의존:
  - T1·T3의 흐름 테스트는 T2의 `InMemoryReviewStore`(14.3)를 씁니다. T2는 14.1과 14.3을 화요일까지 먼저 머지합니다
  - T1(10.3, 401 재발급)과 T3(20.1, 게시 메서드)는 둘 다 `HttpGitHubClient`를 고칩니다. 10.3을 먼저 머지한 뒤 20.1을 시작합니다

## Tasks

### 공유 경계 (리드 A, 월요일 선머지)

- [ ] 1. 패키지 골격, 의존성, 아키텍처 규칙
  - `backend/src/main/java/com/prlens/` 아래 `webhook`, `store`, `store.db`, `store.memory`, `query`, `publish`, `server` 패키지 생성
  - `backend/build.gradle.kts`에 의존성을 버전 고정으로 추가하고 PR 설명에 이유를 적음: Spring Web, `postgresql` 드라이버, `flyway-core`, `flyway-database-postgresql`, Testcontainers PostgreSQL. JWT·PEM 라이브러리(spike 5)와 OpenAPI 검증 도구(spike 8)는 spike 결과가 나오면 추가
  - `ArchitectureTest`에 설계 "의존 규칙" 표의 여섯 규칙을 추가해 `./gradlew check`에 포함
  - CI에 `review`·`llm` 패키지 diff 경고 단계를 추가하고 PR 템플릿에 "`review`·`llm` 패키지 diff 없음" 체크 항목을 넣음
  - _Requirements: 16.1, 16.2, 16.3, 16.4_

- [ ] 2. 공유 타입과 경계 인터페이스 정의
  - [ ] 2.1 `store` 레코드와 열거형 작성
    - `RunTrigger`, `RunStatus`(`isActive()`), `PublishOutcome`, `FeedbackState`, `DedupeKey`(head SHA `^[0-9a-f]{40}$` 아니면 거부), `PrKey`, `NewRun`, `CliRun`, `ReviewCompletion`, `RunFailure`, `RegisterOutcome`, `DedupeStatus`, `ReviewRun`, `RunTimestamps`, `RunError`, `PublishError`, `ContextFileRef`, `StoredFinding`, `FindingOutcome`, `Page<T>`, `RepositorySummary`, `PullRequestSummary`, `RunSummary`, `StoreException`, `StoreErrorKind`
    - 불변식 검사: `NewRun`은 `trigger == WEBHOOK ⇔ installationId != null`이고 `CLI`는 거부, `CliRun`은 completion과 failure 중 정확히 하나. 목록은 `List.copyOf`, 시각은 마이크로초로 자름
    - _Requirements: 7.6, 7.7, 16.5_
  - [ ] 2.2 `ReviewStore` 인터페이스 작성 (설계 T2 절의 메서드 전체, 상태 전이 가드와 반환값 의미는 Javadoc에 적음)
    - _Requirements: 5.3, 16.5, 16.6_
  - [ ] 2.3 `github` 확장 인터페이스 작성
    - `RefreshableCredentials`, `PublishGitHubClient`, `GitHubApi`, `PublishGitHubClientProvider`, `GitHubPage`, `GitHubPrincipal`, `ReviewCommentRef`, `IssueCommentRef`, `ReactionRef`, `ReviewDraft`, `DraftComment`, `CreatedReview`, `GitHubUnprocessableException`
    - P1 `GitHubClient`는 수정하지 않음. 설계는 T3가 추가한다고 적었지만, T1의 `GitHubClientFactory`가 `GitHubApi`를 반환하므로 병렬 작업을 위해 월요일에 인터페이스만 먼저 머지
    - _Requirements: 16.3_
  - [ ] 2.4 `publish` 인터페이스 작성: `CommentPublisher`, `PublishResult`, `PublishSettings`, `FeedbackCollector`
    - _Requirements: 16.2_
  - [ ] 2.5 `testkit` 가짜 구현과 공용 생성기 기본형 작성
    - `FakeGitHubClient`(`GitHubApi` 구현: PR 상태, 이슈·리뷰 코멘트, 리액션 페이지, 게시 주체, 호출 기록, 401·404·422·5xx·지연 주입)
    - `FakeLlmClient`(응답 스크립트, 요청 기록), 가변 `Clock`, 기록하는 `Sleeper`
    - 생성기: Dedupe_Key(대소문자 변형 head SHA), ReviewRun·StoredFinding(U+0000 제외, 마이크로초 시각), 리액션 페이지
    - _Requirements: 16.6, 16.7_

- [ ] 3. 설정 확장
  - [ ] 3.1 `AutomationSettings`, `LoadedConfiguration`와 `ConfigLoader` 확장
    - `repositories.allowed`, `jobs.*`, `publish.*`, `server.address` 구역과 범위 검증(정수 스칼라만, 불리언 리터럴만, IP 리터럴만), 모든 문제를 모아 한 번에 보고
    - 허용 목록은 대소문자 무시로 중복을 제거하고 처음 나온 표기를 유지, 1,000개 상한
    - 비밀 항목 키(`webhooksecret`, `privatekey`, `password`, `dburl` 등, `db`·`database` 구역)는 값을 읽지 않고 대신 쓸 환경변수를 안내
    - P1 `Configuration`은 수정하지 않음
    - _Requirements: 3.5, 3.6, 3.7, 13.4, 18.3, 19.1, 19.2, 19.4_
  - [ ] 3.2 `ConfigPrinter`에 P2 항목 출력 추가 (P1 항목 뒤, 문자열은 큰따옴표)
    - _Requirements: 19.6_
  - [ ]* 3.3 Property 27: 설정 round-trip 테스트
    - **Validates: Requirements 19.6**

- [ ] 4. 서버 비밀정보 치환
  - [ ] 4.1 `support.SecretRegistry` 구현 (불변 `SecretMasker`를 `volatile`로 보관하고 추가할 때마다 교체, 빈 값·공백 값 제외)
    - App 개인 키 등록 형태: 환경변수 원문(`\n` 이스케이프), `\n` 변환본, `\r\n` 변환본, 16자 이상인 base64 줄 각각
    - `PRLENS_DB_URL` 비밀번호 추출: `user:pass@`와 `password=` 값, 각각 원문과 URL 디코딩본
    - Installation_Token은 최근 10,000개까지 보관
    - _Requirements: 18.1, 18.2_
  - [ ] 4.2 `MaskingLayoutEncoder`와 `logback-spring.xml` 작성 (메시지와 스택 전체를 문자열로 만든 뒤 치환, 모든 appender에 적용), Spring·Tomcat 요청 로깅 끄기, `System.out`/`err` 감싸기
    - _Requirements: 18.2, 18.4_
  - [ ] 4.3 `server` 전역 오류 처리 구현 (최하위 우선순위 `@RestControllerAdvice` → `ProblemDetail(500)`, 클래스 이름·메시지·스택 없음, `server.error.include-*` 끄기, `detail`은 레지스트리 치환)
    - _Requirements: 18.6_
  - [ ]* 4.4 Property 26: 치환 후 비밀값이 남지 않는지 테스트
    - **Validates: Requirements 11.10, 18.2, 18.7**

- [ ] 5. Checkpoint: 선머지 확인
  - `./gradlew check`(ArchUnit 포함)가 통과하는지 확인하고, 질문이 있으면 트랙 시작 전에 사용자에게 묻습니다.

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
    - _Requirements: 1.3_

- [ ] 7. payload 해석과 허용 저장소
  - [ ] 7.1 `PullRequestPayloadParser` 구현
    - Required_Fields 경로(`repository.id`, `repository.full_name`, `pull_request.number`, `pull_request.head.sha`, `pull_request.base.sha`, `installation.id`) 검사 후 누락 필드 이름을 모두 반환
    - head·base SHA는 `^[0-9a-fA-F]{40}$` 검사 후 소문자로, `title`이 없으면 `""`, `draft`는 읽지 않음, `full_name`은 P1 문자 규칙으로 검사
    - _Requirements: 1.7, 1.8, 1.13, 5.7_
  - [ ] 7.2 `AllowedRepositories` 구현 (`Locale.ROOT` 소문자 집합, 빈 집합이면 항상 거부, `private` 값 무시)
    - _Requirements: 3.1, 3.2, 3.3, 3.7, 3.8, 3.9_
  - [ ]* 7.3 Property 4: 허용 저장소 판정이 소문자 비교 모델과 같은지 테스트
    - **Validates: Requirements 3.1, 3.3, 3.7, 3.8, 3.9**

- [ ] 8. `JobRunner` 등록과 실행기
  - [ ] 8.1 `JobRunner.register` 구현
    - CAS로 대기 수 카운터를 올림 → 가득 차면 `inspectDedupe`로 `Duplicate`, `MaxAttemptsReached`, `QueueFull` 중 하나 반환 → 올렸으면 `registerAutomatedRun` → `Created`이면 실행기에 제출, 그 외 결과나 예외면 카운터를 내림
    - 저장 예외는 `StoreUnavailableException`으로 감쌈. `NewReviewJob`, `RegisterResult` 작성
    - _Requirements: 1.2, 1.11, 1.12, 4.1, 4.12, 4.13, 5.1, 5.2, 5.4, 5.5, 5.8, 5.10_
  - [ ] 8.2 실행기 구현 (`ThreadPoolExecutor` + 무제한 FIFO 큐, 실행 시작 시 카운터를 내림, `try/catch (Throwable)`로 격리하고 `failed`/`internal` 기록, 복구 전용 `enqueueRecovered`·`enqueuePublishOnly`는 상한 검사 없음)
    - _Requirements: 4.6, 4.9, 4.12_
  - [ ]* 8.3 Property 5(Dedupe_Key마다 Active_Run 하나), Property 6(도착 순서 무관), Property 7(시도 번호와 최대 시도 횟수) 테스트 (`InMemoryReviewStore` 사용)
    - **Validates: Requirements 4.15, 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 5.8, 5.9, 5.10, 5.11, 5.12, 5.13, 16.6**

- [ ] 9. webhook 요청 처리
  - [ ] 9.1 `WebhookProcessor.handle` 구현
    - 설계 "요청 처리 순서와 응답" 표의 순서를 따름: 크기 → 서명 → 이벤트 → JSON → action → 필드 → 허용 저장소 → `closed`/Target_Action 분기 → `FeedbackCollector.requestRefresh` → `JobRunner.register`
    - 응답 형식은 `WebhookAck(status, reason, runId)`. 서명 실패 로그에는 delivery ID(없으면 `없음`), 실패 종류, 수신 시각만 남기고 서명 헤더와 본문은 남기지 않음
    - _Requirements: 1.2, 1.4, 1.5, 1.6, 1.7, 1.8, 1.9, 1.11, 1.12, 2.5, 3.2, 4.13, 5.2, 5.5, 5.9, 15.3, 18.4_
  - [ ] 9.2 `WebhookController` 구현 (`POST /webhooks/github`, `HttpServletRequest`에서 원문 바이트를 직접 읽음, 진입 시 `Clock`으로 수신 시각 기록, DB 쿼리 제한 시간 3초)
    - _Requirements: 1.1, 1.10, 2.1_
  - [ ]* 9.3 Property 1: 첫 결정 단계에서 응답하는지 테스트
    - **Validates: Requirements 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8, 2.4, 2.6, 3.2**
  - [ ]* 9.4 webhook 예시 테스트
    - `ping` 200, 각 무시 사유, 400 필드 이름 목록, 413(`Content-Length` 있음·없음), 503(대기열 가득·저장소 오류) 뒤 Review_Run 없음, delivery ID null, draft PR, 서명 실패 로그 필드(로그 캡처)
    - _Requirements: 1.3, 1.4, 1.5, 1.8, 1.12, 1.13, 2.5, 4.13_

- [ ] 10. GitHub App 인증
  - [ ] 10.1 `AppJwtSigner` 구현 (RS256, `iat = now − 60s`, `exp = now + 9m`, `iss = App ID`, 개인 키의 `\n` 이스케이프 변환, 서명한 JWT는 `SecretRegistry`에 등록)
    - spike 5(PEM 종류, JWT 라이브러리) 결과 반영
    - _Requirements: 6.1, 18.2_
  - [ ] 10.2 `InstallationTokenProvider` 구현
    - installation ID별 캐시, 남은 유효 시간이 5분 이상이면 재사용하고 아니면 ID별 잠금 안에서 다시 확인한 뒤 발급
    - `invalidate`는 캐시의 토큰이 거부된 토큰과 같을 때만 지움. 발급 호출에 P1 `RetryExecutor` 적용, 발급한 토큰은 `SecretRegistry`에 등록
    - spike 6(응답 필드, 기본 유효 기간) 결과 반영
    - _Requirements: 6.2, 6.3, 6.6, 18.2_
  - [ ] 10.3 `InstallationCredentials`와 `HttpGitHubClient`의 401 한 번 재발급 구현 (`RefreshableCredentials`일 때만 동작, 두 번째 401은 `GitHubAuthException`, P1 PAT 동작은 그대로)
    - _Requirements: 6.4, 6.5_
  - [ ] 10.4 `GitHubClientFactory` 구현 (`forInstallation(installationId, scope)`로 `GitHubApi` 반환: 공유 `HttpClient`, 작업 전용 `RetryExecutor`, `PublishGitHubClientProvider` 구현)
    - _Requirements: 6.2, 16.3_
  - [ ]* 10.5 Property 8: Installation_Token 재사용 판정 테스트
    - **Validates: Requirements 6.2, 6.3**
  - [ ]* 10.6 GitHub App HTTP 수준 테스트 (JWT `iat`/`exp`/`iss`, 401 한 번 재발급 뒤 성공과 재실패, 동시 401에서 발급 1회)
    - _Requirements: 6.1, 6.4, 6.5_

- [ ] 11. `ReviewJob` 실행
  - [ ] 11.1 `JobScope` 구현 (제한 시각, `CancellationToken`, `JobRetryListener`(대기 합 ms), `DeadlineSleeper`, `UsageAccumulator`, `JobReviewListener`(Review_Run ID를 담은 서버 로그))
    - _Requirements: 4.7, 4.10, 17.3_
  - [ ] 11.2 작업 전용 LLM 데코레이터 구현: `CancellableLlmClient`, `UsageRecordingLlmClient`. 체인은 `RetryingLlmClient(UsageRecordingLlmClient(CancellableLlmClient(공유 클라이언트)))`
    - _Requirements: 4.7, 8.2, 8.3_
  - [ ] 11.3 `ReviewJob` 구현 (설계 실행 순서 1~7단계)
    - `markRunning`이 false면 종료 → PR·컨텍스트 조회 → head가 Dedupe_Key와 다르면 Claude 호출 전에 `failed`/`head_moved`(G-1), base가 다르면 경고 로그(G-2) → 작업별 `ReviewEngine` → `completeRun`
    - 비용 상한 초과 경고, 저장이 실패하면 `running` 그대로 두고 로그 → `CommentPublisher.publish` → `recordPublished` → 120초 초과 경고
    - 실패하면 오류 종류로 매핑해 `failRun`을 부르고 실패 게시를 요청. 게시만 하는 복구 작업은 6단계만 실행
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.11, 4.14, 7.15, 8.2, 8.3, 8.4, 17.4_
  - [ ] 11.4 제한 시간과 취소 구현 (`ScheduledExecutorService`로 `scope.cancel(TIMEOUT)` 예약: 토큰 표시 → 중단 훅 → 인터럽트, 취소된 뒤 받은 결과는 버리고 `failed`/`timeout`으로 기록)
    - spike 4(진행 중 HTTP 호출 중단) 결과 반영. 호출을 끊을 수 없으면 G-10으로 올림
    - _Requirements: 4.7_
  - [ ]* 11.5 `ReviewJob` 테스트
    - Property 10(usage는 받은 응답의 합)
    - 예시 테스트: 작업자 1개 FIFO, 예외 격리, 제한 시간(짧은 값 + 가짜 `Clock`), `head_moved`, 120초 초과 경고
    - **Validates: Requirements 8.1, 8.2, 8.3**
    - _Requirements: 4.6, 4.7, 4.9, 17.4_

- [ ] 12. 재시작 복구와 서버 시작
  - [ ] 12.1 `StartupRecovery` 구현 (`SmartInitializingSingleton`: `markRunningAsInterrupted` → `listQueuedRuns` → `enqueueRecovered` → `listUnpublishedCompletedRuns` → `enqueuePublishOnly`)
    - spike 2(웹 서버 커넥터보다 먼저 끝나는지) 결과 반영
    - _Requirements: 4.8, 4.14, 4.15_
  - [ ] 12.2 `ServerStartupValidator`와 `PrLensServerApplication` 빈 조립
    - 시작 순서: 설정 검증 → 환경변수 검증(누락·빈 값·공백 값·PEM 오류를 모아 이름만 출력, 0이 아닌 코드로 종료) → 비밀값 등록 → `DbMigrator` → 경고(빈 허용 목록, 인증 없는 Query_API, 루프백이 아닌 주소) → 복구 → 포트 열기
    - Flyway 자동 설정 끄기, `WebServerFactoryCustomizer`로 `server.address` 설정, 설정 파일은 시작할 때 한 번만 읽음
    - _Requirements: 3.4, 6.8, 7.10, 7.11, 10.11, 10.12, 19.5_
  - [ ]* 12.3 복구 테스트 (세 경로, 게시만 하는 복구가 LLM을 부르지 않음, 시작 검증 이름 목록, `\n` 이스케이프 키)
    - _Requirements: 4.8, 4.14, 6.8_

- [ ] 13. Checkpoint: T1 완료 확인
  - T1 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### T2 store·query (B)

- [ ] 14. 메모리 구현과 지문 (화요일까지 선머지, T1·T3가 사용)
  - [ ] 14.1 `store.FindingFingerprint.of(Finding)` 구현
    - 필드: 정규화 `file`, `category` JSON 값, `basis.ref ?? ""`, `message.strip()`. 각 필드의 U+0000을 U+FFFD로 바꾸고 `\u0000`으로 이어 붙인 뒤 SHA-256 소문자 16진수
    - D-7 결정 대기, 제안 구성으로 진행
    - _Requirements: 12.4, 12.5_
  - [ ]* 14.2 Property 17: Finding_Fingerprint 테스트
    - **Validates: Requirements 12.4, 12.5**
  - [ ] 14.3 `store.memory.InMemoryReviewStore` 구현
    - 모든 공개 메서드를 `ReentrantLock` 하나 안에서 실행, DB와 같은 상태 전이 가드·CHECK 검사·정렬 비교자(마지막 동점 키 `id`)
    - `toLowerCase(Locale.ROOT)`, 마이크로초 절삭, U+0000 치환과 경고(G-4), 테스트 훅 `failNextCall`
    - 운영 코드(`--publish`)에서도 쓰므로 `src/main`에 둠
    - _Requirements: 5.3, 7.13, 7.17, 15.5, 15.6, 16.6_
  - [ ]* 14.4 `ReviewStoreContract` 추상 테스트와 Property 9(저장 round-trip)의 메모리 구현 적용
    - **Validates: Requirements 7.8, 7.16, 7.17, 17.3**

- [ ] 15. DB 스키마와 마이그레이션
  - [ ] 15.1 `db/migration/V1__init.sql` 작성 (설계 DDL: 테이블 4개, CHECK 제약, 부분 유일 인덱스 `ux_review_run_active`·`ux_review_run_attempt`, 조회·복구 인덱스)
    - _Requirements: 5.3, 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.9_
  - [ ] 15.2 `store.db.DbMigrator`, `DbConnector` 구현 (Flyway API 직접 호출, 서버·CLI 공용, 연결 제한 10초, 실패 시 실패 버전과 치환한 메시지)
    - spike 7(실패 버전 API, 동시 migrate) 결과 반영
    - _Requirements: 7.10, 7.11, 7.12_
  - [ ] 15.3 DB 예외 변환 구현 (`SQLException`·`DataAccessException`·Flyway 예외 → `StoreException(kind, maskedMessage)`, 원인 예외를 붙이지 않음, 원래 예외는 debug 로그로만 남김)
    - _Requirements: 7.14, 18.2_
  - [ ]* 15.4 마이그레이션 통합 테스트 (빈 DB 적용, 재실행해도 변경 없음, 깨진 `V2`의 실패 버전 출력과 되돌림, 서버·CLI 동시 migrate)
    - _Requirements: 7.10, 7.11_

- [ ] 16. `JdbcReviewStore`
  - [ ] 16.1 `registerAutomatedRun`, `inspectDedupe` 구현
    - 한 트랜잭션(READ COMMITTED, `statement_timeout` 3초): `repository` upsert → `pull_request` 삽입 후 `FOR UPDATE` → Active_Run 조회 → `failed` 수와 최대 시도 번호 조회(`cli` 제외) → `queued` 삽입과 `pull_request` 갱신
    - `23505`가 나면 되돌린 뒤 Active_Run 조회만 다시 실행
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.8, 5.10, 5.11, 7.3, 7.5_
  - [ ] 16.2 쓰기 메서드 구현 (`markRunning`, `completeRun`(결과·usage·시각과 Finding 전부를 1 트랜잭션에), `failRun`, `markRunningAsInterrupted`, `saveCliRun`, `recordPublished`, `markSuperseded`, `recordSummaryComment`, `recordPublishError`, `recordFindingOutcomes`, `updateFeedback`)
    - 조건부 갱신 가드, 트랜잭션 경계 표, U+0000 치환(G-4), 비용은 `NUMERIC`으로 scale 보존
    - _Requirements: 4.2, 4.4, 4.5, 4.8, 7.8, 7.13, 7.17, 8.1, 8.2, 8.3, 9.1, 14.2, 15.5, 15.6_
  - [ ] 16.3 조회 메서드 구현 (`findRun`, `findFindings`, `findPullRequest`, `listRepositories`, `listPullRequests`(`JOIN LATERAL`), `listRuns`, `listFindings`, `listFeedbackTargets`, 복구용 목록)
    - `count(*) OVER ()`로 전체 수, 대소문자 무시 저장소 조회는 `updated_at`이 가장 최근인 행(G-9)
    - _Requirements: 4.8, 4.14, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_
  - [ ]* 16.4 DB 통합 테스트
    - Property 9(DB 구현, `ReviewStoreContract` 상속), Property 14(메모리 구현과 DB 구현의 동등성)
    - 예시 테스트: 동시성(스레드 16개로 같은 Dedupe_Key 등록 50회 반복, `23505` 경로), 트랜잭션 되돌림, CHECK 제약, 비밀번호가 든 잘못된 URL로 `StoreException` 치환
    - **Validates: Requirements 5.3, 7.8, 7.16, 7.17, 16.6, 16.9, 17.3**

- [ ] 17. Query_API
  - [ ] 17.1 조회 DTO 작성 (`RepositoryDto`, `PullRequestDto`, `RunDto`, `FindingDto`, 목록 봉투. camelCase 필드, 열거형은 소문자 snake_case, 시각은 ISO-8601 UTC)
    - _Requirements: 10.10_
  - [ ] 17.2 `QueryController`와 `query` 전용 오류 처리 구현
    - 엔드포인트 4개, `limit = 100`
    - 경로 변수를 `String`으로 받아 검사하고 잘못된 이름을 모두 모아 400(`invalidPathVariables`)
    - 404는 대상 종류, 500은 고정 문구 "저장소 조회 오류", 조회 제한 시간 5초. Stored_Finding 0개면 빈 목록
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.13, 10.14_
  - [ ] 17.3 `docs/api/openapi.yaml` 작성 (OpenAPI 3.1, 엔드포인트 4개, 경로 변수 패턴, DTO 스키마 `additionalProperties: false`, 400/404/500 `ProblemDetail`)
    - _Requirements: 10.9_
  - [ ] 17.4 `QueryApiContractIT` 작성 (문서 파싱, 모든 엔드포인트의 200/400/404/500 응답을 스키마로 검증, 매핑 경로 집합과 `paths` 비교)
    - spike 8(검증 도구) 결과 반영
    - _Requirements: 10.9_
  - [ ]* 17.5 Property 11(비용 합), Property 12(목록 정렬과 전체 수), Property 13(Finding 조회 round-trip) 테스트
    - **Validates: Requirements 8.5, 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.10, 10.13, 10.15**

- [ ] 18. CLI `--save`
  - [ ] 18.1 `cli.P2ExitCodes.resolve(p1Code, sideEffectFailed)` 구현 (`p1Code`가 2나 1이면 그대로, 부수 작업이 실패하면 4, 아니면 `p1Code`)
    - D-5 결정 대기, 제안값으로 진행
    - _Requirements: 9.7, 9.8_
  - [ ] 18.2 `--save` preflight 구현 (`PRLENS_DB_URL` 검사 → DB 비밀번호 등록 → 연결·마이그레이션 → 연결 닫기, 실패하면 외부 API 호출 없이 종료 코드 2. `--save`가 없으면 DB 코드를 만들지 않음)
    - _Requirements: 7.12, 9.2, 9.3, 9.4, 9.5, 9.6_
  - [ ] 18.3 저장 흐름 구현
    - CLI 조립에 `UsageRecordingLlmClient`를 끼움, `repositoryId` 조회(G-5), `saveCliRun`(새 연결)
    - stderr에 Review_Run ID 또는 저장 오류 출력. 결과 생성 실패면 `failure`로 저장하고, PR 스냅샷 전 실패는 저장하지 않음(G-6)
    - _Requirements: 9.1, 9.7, 9.8, 9.9_
  - [ ]* 18.4 CLI `--save` 테스트 (설계 종료 코드 표 전 행, stdout이 `--save` 유무와 관계없이 같음, `--save`가 없으면 DB 연결 없음, `P2ExitCodes` 전수 표)
    - _Requirements: 9.2, 9.6, 9.7, 9.8, 9.9_

- [ ] 19. Checkpoint: T2 완료 확인
  - T2 테스트(Testcontainers 포함)가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### T3 publish (C)

- [ ] 20. GitHub 게시 호출
  - [ ] 20.1 `HttpGitHubClient`에 `PublishGitHubClient` 메서드 구현 (10.3 머지 후 시작)
    - `headSha`, `repositoryId`, 리뷰·이슈 코멘트 목록(`per_page=100`, `Link rel="next"`), `createReview`(event `COMMENT`, side `RIGHT`), 이슈 코멘트 생성·수정, 리액션 목록
    - 422는 `GitHubUnprocessableException`(치환한 본문, 최대 8KB). 요청 로그에는 메서드·경로·상태만 남김
    - _Requirements: 11.1, 11.2, 12.1, 12.2, 15.2, 18.4_
  - [ ] 20.2 게시 주체 식별 구현 (`AppPrincipalResolver`: `GET /app`의 slug → `{slug}[bot]`, 프로세스 캐시 / `TokenPrincipalResolver`: `GET /user`, 403이고 `GITHUB_ACTIONS=true`이면 `github-actions[bot]`)
    - spike 10 결과 반영. 비교 기준이 바뀌면 `GitHubPrincipal.isAuthor`만 수정
    - _Requirements: 11.1, 12.4, 15.2_
  - [ ] 20.3 `GitHubPages.readAll(fn)` 헬퍼 구현 (`hasNext`가 거짓일 때까지 모든 페이지 읽기)
    - _Requirements: 11.2, 12.4, 15.2_

- [ ] 21. 게시 계획과 본문 렌더링
  - [ ] 21.1 `PublishPlanner.plan`과 `OutcomeResolver.resolve` 구현 (`Severity.rank()`, 역할 판정 표, Inline_Eligible Publishable_Finding 안에서 같은 지문 묶음 만들기, 422 외 실패는 `not_published`(G-11))
    - _Requirements: 11.3, 12.5, 12.10, 12.11, 12.12, 12.13, 13.1, 13.2, 13.3, 13.5_
  - [ ]* 21.2 Property 15(Publishable_Finding 부분 목록), Property 16(하한을 올리면 게시 대상이 늘지 않음) 테스트
    - **Validates: Requirements 13.1, 13.2, 13.5, 13.6**
  - [ ] 21.3 `UntrustedText.sanitize` 구현 (멘션 앞 `@` 뒤에 U+200B, `<!--`를 `<\u200B!--`로, 닫히지 않은 코드 펜스 보완, 목록 항목의 `message`는 한 줄로)
    - _Requirements: 11.10, 12.3_
  - [ ] 21.4 `SummaryRenderer` 구현
    - 본문 구성: 마커 → 불완전·비용 경고 → 제목 → 요약(요약 전용 안내) → 지적 수와 하한 미만 수 → 라인 밖 지적 → 사용량. `failed`이면 실패 본문
    - 65,536자 제한: 라인 밖 지적 개수를 이진 탐색으로 줄이고, 부족하면 서로게이트 쌍을 나누지 않게 `summary`를 자름
    - 필드 치환 → sanitize → 조립 → 본문 전체 재치환. 줄바꿈 `\n`, 숫자는 `Locale.ROOT`
    - _Requirements: 8.4, 11.3, 11.4, 11.5, 11.7, 11.8, 11.10_
  - [ ] 21.5 Line_Comment 본문 렌더러 구현 (첫 줄 Finding_Marker, 심각도·카테고리·메시지, 값이 있을 때 제안·근거, 리액션 안내, 같은 길이 제한)
    - _Requirements: 12.3, 11.10_
  - [ ]* 21.6 Property 21(코멘트 본문 필수 항목), Property 22(Summary_Comment 길이 상한) 테스트
    - **Validates: Requirements 8.4, 11.3, 11.4, 11.5, 11.8, 12.3**

- [ ] 22. `CommentPublisher`
  - [ ] 22.1 `PrPublishLocks` 구현 (PR별 공정 `ReentrantLock`과 사용자 수 카운트, 쓰는 스레드가 0이 되면 항목 삭제)
    - _Requirements: 14.3_
  - [ ] 22.2 Line_Comment 단계 구현
    - 게시 주체의 기존 마커로 `지문 → 가장 작은 ID` 맵을 만들어 재사용, 나머지는 `createReview` 한 번, 새 코멘트 ID는 리뷰의 코멘트 목록에서 마커로 대응
    - 422는 `RejectionParser`로 원인을 식별해 한 번만 다시 보냄(식별하지 못하면 모두 `github_rejected`), 422 외 실패는 `recordPublishError` 후 Summary 단계를 계속
    - 이전 Line_Comment는 수정·삭제하지 않음
    - spike 9(422 본문 형식, 리뷰당 코멘트 수 상한) 결과 반영
    - _Requirements: 12.1, 12.2, 12.4, 12.6, 12.7, 12.8, 12.9, 12.10, 12.11, 12.12, 12.14_
  - [ ] 22.3 Summary upsert 구현 (게시 주체가 쓰고 마커가 있는 코멘트 중 가장 먼저 만들어진 것을 수정, 없으면 생성, 수정이 404면 한 번 새로 생성, 재시도 후 실패하면 `recordPublishError`)
    - _Requirements: 11.1, 11.2, 11.6, 11.9, 11.13, 11.14_
  - [ ] 22.4 `DefaultCommentPublisher.publish` 구현 (설계 순서 1~8단계)
    - 잠금 안에서 다시 조회 → `github_auth` 실패면 코멘트 없이 오류만 기록
    - 현재 head 조회와 형식 검사 → head가 바뀌었으면 `superseded`(`failed`이면 `StaleFailure`)
    - 주체 확인 → 계획 → 라인 → 요약 → `recordFindingOutcomes` 한 번 → 결과 반환. 이전 Publish_Error를 덮어쓰면 로그에 남김
    - _Requirements: 6.7, 11.7, 11.9, 14.1, 14.2, 14.3, 14.4, 14.5, 14.6_
  - [ ]* 22.5 Property 18(게시 대상 Finding에만 Line_Comment), Property 19(재게시해도 Line_Comment 수 유지), Property 20(Summary_Comment는 PR마다 하나), Property 23(현재 head 기준) 테스트
    - **Validates: Requirements 11.1, 11.2, 11.14, 12.1, 12.4, 12.5, 12.6, 12.7, 12.13, 12.14, 12.15, 14.2, 14.8, 19.3**
  - [ ]* 22.6 게시 예시 테스트 (422 재게시와 식별 불가, 422 외 실패 뒤 Summary 계속, 라인을 먼저 게시하는 순서, `summary_listed` 기록, `github_auth` 게시 생략, PR별 직렬화에서 호출이 겹치지 않음)
    - _Requirements: 6.7, 12.8, 12.9, 12.11, 14.3_

- [ ] 23. `FeedbackCollector`
  - [ ] 23.1 `FeedbackRules.decide` 구현 (게시 주체의 리액션과 `+1`·`-1` 외 리액션을 뺀 뒤 판정, login이 null인 리액션은 셈)
    - _Requirements: 15.2, 15.13_
  - [ ]* 23.2 Property 24: Feedback_State 판정이 참조 구현과 같은지 테스트
    - **Validates: Requirements 15.2, 15.13**
  - [ ] 23.3 `FeedbackSync.sync` 구현 (서로 다른 Line_Comment ID를 오름차순으로 순회 → 리액션 모든 페이지 → `updateFeedback`, 404나 재시도 후 실패는 `FailedLookup`에 담고 계속, `StoreException`이면 중단, 끝나면 상태별 개수)
    - _Requirements: 15.5, 15.6, 15.7, 15.8_
  - [ ] 23.4 `AsyncFeedbackCollector` 구현 (작업자 1개, `PENDING`/`RUNNING`/`RUNNING_DIRTY`로 같은 PR 요청 합치기, 대기 PR 1,000개 상한, 예외 격리)
    - _Requirements: 15.3_
  - [ ]* 23.5 Property 25: 채택/기각 갱신의 멱등성 테스트
    - **Validates: Requirements 15.5, 15.6, 15.8, 15.14**
  - [ ] 23.6 `prlens feedback sync <PR URL>` 명령 구현
    - preflight 순서: 인자 → 설정 → `PRLENS_DB_URL` → `GITHUB_TOKEN` → DB → 저장된 PR 확인. 여기까지 GitHub API를 호출하지 않음
    - 이어서 주체 확인 → sync → stderr에 개수와 실패 목록 출력, stdout은 비움. 종료 코드는 `P2ExitCodes`
    - _Requirements: 15.4, 15.9, 15.10, 15.11, 15.12_

- [ ] 24. GitHub Actions 대체 경로 `--publish`
  - [ ] 24.1 `--publish`와 `--publish --save` 흐름 구현
    - preflight: 인자 → 설정 → 환경변수 → 허용 저장소 → (`--save`) DB
    - 저장소는 `InMemoryReviewStore` 또는 `JdbcReviewStore`. PR 조회 → `repositoryId` → `principal()` → `registerAutomatedRun(ACTIONS)`. `Duplicate`·`MaxAttemptsReached`이면 Claude 호출 없이 종료 코드 0
    - 리뷰 → `completeRun`/`failRun` → `publish`. 저장이 실패하면 메모리 저장소로 옮겨 게시를 계속
    - 결과별 stderr 출력과 `P2ExitCodes`
    - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5, 20.7, 20.8, 20.9_
  - [ ]* 24.2 `--publish` 테스트 (허용되지 않은 저장소는 외부 호출 없이 종료 코드 2, 중복이면 0, `superseded`는 실패로 치지 않음, 게시 실패는 1 또는 4)
    - _Requirements: 20.3, 20.4, 20.5, 20.8, 20.9_

- [ ] 25. Checkpoint: T3 완료 확인
  - T3 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### 통합 (전원, 목~금)

- [ ] 26. 픽스처, 통합 테스트, CI, 설치 문서
  - [ ] 26.1 서명된 webhook 픽스처와 생성기 작성
    - `src/test/resources/webhook/`에 헤더 JSON과 원문 본문 쌍: `opened`, `synchronize`, `reopened`, `closed`, `ping`, `signature_mismatch`, `repo_not_allowed`, `missing_fields`(필드별), `bad_head_sha`, `crlf_unicode`, `redelivery`
    - `WebhookFixtureGenerator`와 `./gradlew :backend:generateWebhookFixtures`. 결과 파일을 커밋하고, CI에서 다시 생성해 커밋본과 비교
    - _Requirements: 16.8_
  - [ ] 26.2 픽스처 비밀정보 스캔에 PEM 헤더와 `ghs_` 접두어 추가
    - _Requirements: 16.8, 18.7_
  - [ ] 26.3 서버 조립 통합 테스트 (DB와 가짜 `GitHubClient`/`LlmClient`로 서명된 픽스처를 webhook부터 게시까지 한 번 실행)
    - _Requirements: 1.1, 4.4, 11.1, 16.7_
  - [ ] 26.4 CI 구성 갱신 (`./gradlew check`에 ArchUnit·OpenAPI 계약·픽스처 비교를 포함. GitHub 호스팅 Windows·macOS 러너에는 Docker가 없으므로 Testcontainers 테스트는 Linux 러너에서만 실행하고 나머지 테스트는 세 OS 매트릭스 유지)
    - _Requirements: 10.9, 16.2, 16.8_
  - [ ] 26.5 설치 문서 작성 (App 권한, 요약 코멘트가 둘 생길 수 있는 제한, 직렬화 한계, Actions 워크플로 예시와 fork 제한, `actions` 고아 Review_Run 정리 SQL, force-push 복귀 한계, 저장소 이름 변경 시 재시작, `feedback sync` 실행 계정, outdated 코멘트)
    - _Requirements: 6.9, 11.13, 14.7, 20.6_

- [ ] 27. Final Checkpoint
  - 전체 테스트와 CI가 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

## Notes

- spike 1~13(design.md "3주차 spike 체크리스트")에 따라 6.4, 10.1, 10.2, 11.4, 12.1, 15.2, 17.4, 20.2, 22.2의 세부 구현이 달라집니다. spike 결과가 설계와 다르면 design.md를 먼저 고친 뒤 작업합니다.
- 요구사항 공백 G-1~G-13은 설계 제안대로 작업에 반영했습니다(G-1·G-2는 11.3, G-4는 14.3·16.2, G-5·G-6은 18.3, G-9는 16.3, G-11은 21.1, G-7·G-12·G-13은 26.5). 요구사항을 고치기로 하면 관련 작업도 함께 수정합니다.
- 다음 항목은 코드 작업이 아니라 사람이 수행합니다: 응답 시간 측정(요구사항 17.1·17.2, 400줄 이하 PR로 `opened` 5회와 `synchronize` 5회, 결과는 3주차 회고에 기록), 리뷰어 B의 사람 리뷰와 PR Lens 지적 비교 기록.
- Query_API는 인증 없이 동작합니다(D-8). 12.2의 시작 경고와 기본 바인딩 `127.0.0.1`로 보완하며, 외부에 노출하려면 인증 추가가 먼저 필요합니다.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1"] },
    { "id": 1, "tasks": ["2.1", "2.3", "3.1", "4.1"] },
    { "id": 2, "tasks": ["2.2", "2.4", "3.2", "4.2", "4.3"] },
    { "id": 3, "tasks": ["2.5", "3.3", "4.4"] },
    { "id": 4, "tasks": ["5"] },
    { "id": 5, "tasks": ["6.1", "6.4", "7.1", "7.2", "8.1", "8.2", "10.1", "11.1", "11.2", "14.1", "15.1", "17.1", "18.1", "20.2", "20.3", "21.1", "21.3", "23.1"] },
    { "id": 6, "tasks": ["6.2", "6.3", "7.3", "10.2", "11.4", "14.2", "14.3", "15.2", "15.3", "17.3", "21.2", "21.4", "21.5", "22.1", "23.2", "23.3"] },
    { "id": 7, "tasks": ["8.3", "10.3", "14.4", "15.4", "16.1", "17.2", "18.2", "21.6", "22.2", "22.3", "23.4"] },
    { "id": 8, "tasks": ["9.1", "10.4", "10.5", "10.6", "16.2", "20.1", "22.4", "23.5"] },
    { "id": 9, "tasks": ["9.2", "9.3", "11.3", "16.3", "18.3", "22.5", "22.6", "23.6"] },
    { "id": 10, "tasks": ["9.4", "11.5", "12.1", "16.4", "17.4", "17.5", "18.4", "24.1"] },
    { "id": 11, "tasks": ["12.2", "19", "24.2"] },
    { "id": 12, "tasks": ["12.3", "25"] },
    { "id": 13, "tasks": ["13"] },
    { "id": 14, "tasks": ["26.1", "26.2", "26.4", "26.5"] },
    { "id": 15, "tasks": ["26.3"] },
    { "id": 16, "tasks": ["27"] }
  ]
}
```
