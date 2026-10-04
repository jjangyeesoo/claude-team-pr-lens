# Implementation Plan: PR Lens P1 CLI

## Overview

[design.md](design.md)를 구현하는 작업 목록입니다. 순서는 "공유 경계 선머지(리드 B, 월요일) → T2·T1·T3 병렬 → 통합·CI"입니다. 월요일 선머지는 작업 1.1과 2.1~2.3(패키지 뼈대, 공유 타입, 경계 인터페이스)까지입니다(ROADMAP: 인터페이스 타입만 먼저 머지). 이 넷은 새 의존성 없이 JDK만으로 만들 수 있어 ADR 결정을 기다리지 않습니다. 각 트랙은 이 넷이 머지되면 시작하고, 리드는 1.2(의존성과 ArchUnit), 2.4~2.6, 작업 3(재시도, 마스킹)을 트랙과 병렬로 진행합니다. 트랙 안에서는 순수 함수 → 어댑터 → 조립 순서로 진행합니다.

- 언어와 도구: Java 17, Gradle Kotlin DSL, JUnit Jupiter 6(Spring Boot 4.1 관리 버전), jqwik(속성 기반 테스트, JUnit 6.0.3에서 1.10.1 동작 확인), ArchUnit, picocli(ADR-0004 대기), Anthropic Java SDK
- `*`가 붙은 하위 작업은 속성 기반 테스트·보조 테스트로, 일정이 밀리면 뒤로 미룰 수 있습니다. 요구사항이 테스트 자체를 요구하는 작업(골든 픽스처, 인젝션 회귀, CI 매트릭스)은 필수로 두었습니다
- 속성 테스트에는 `// Feature: pr-lens-p1-cli, Property N: <이름>` 태그 주석을 답니다
- 트랙 사이 의존: 아래 넷은 다른 트랙이 구현을 가져다 씁니다. 시그니처는 2.3에서 스텁으로 먼저 머지하므로 T1과 T3는 구현을 기다리지 않고 시작합니다. T2는 이 넷을 가장 먼저 구현합니다(화요일 목표)
  - `GlobMatcher`(6.1) ← T3 `ConfigLoader`(16.1)
  - `DiffPrinter`(5.2) ← T1 `PromptBuilder`(11.5)
  - `LineRanges`(5.3) ← T1 `FindingValidator`(10.4)
  - `DiffFilter`(6.3) ← T1 `ReviewEngine`(12.1)
- 작업 3의 구현을 쓰는 작업은 7.1(`RetryExecutor`), 13.2(`RetryExecutor`), 18.2(`SecretMasker`)입니다. 이 셋은 3.1 또는 3.3이 머지된 뒤 시작합니다
- 명령은 `backend/`에서 실행합니다(`cd backend && ./gradlew test`). 서브프로젝트는 없습니다
- PR 단위: 상위 작업 하나가 PR 하나입니다. 구현과 그 구현의 테스트(`*` 작업 포함)를 같은 PR에 넣습니다. 큰 상위 작업에는 "PR 경계"를 적어 두었고, 그 작업은 경계대로 나눠서 냅니다. 크기 목표는 `src/main` 변경 400줄 이하이고(테스트, 픽스처, 문서는 세지 않음), 넘으면 PR 설명에 이유를 적습니다

## Tasks

### 공유 경계 (리드 B, 월요일 선머지)

- [ ] 1. 프로젝트 골격과 의존성 준비
  - PR 경계: 1.1 / 1.2 (1.1은 월요일 선머지. 패키지 이름 변경이 크면 그 부분만 먼저 따로 냄)
  - [ ] 1.1 패키지 이름 변경, 메모 샘플 제거, 패키지 뼈대 (월요일 선머지. 새 의존성 없음)
    - 패키지를 `com.example.starter`에서 `com.prlens`로 바꿉니다(ROADMAP 1주차 항목. 이미 돼 있으면 건너뜀). `build.gradle.kts`의 group, `settings.gradle.kts`의 프로젝트 이름, `application.properties`를 함께 바꿉니다. 변경 줄 수가 많으면 이 부분만 별도 PR로 먼저 냅니다
    - backend의 메모 샘플(`memo` 패키지와 그 테스트)을 지웁니다(ROADMAP: 2주차 첫 기능 PR에서 제거). `common/error/ApiExceptionHandler`가 `MemoNotFoundException`을 import하므로 그 처리 메서드도 함께 지웁니다. `common`의 나머지는 P2 스펙에서 위치를 정할 때까지 둡니다(ADR 0003)
    - web의 메모 화면(`web/src/app/memos`, `web/src/lib/api/memos.ts`)은 4주차까지 그대로 둡니다(ROADMAP 4주차 T1). backend에서 메모 API가 사라지지만 이 PR에서 `web/`은 고치지 않습니다("API가 바뀌면 `types.ts`도 같은 PR" 규칙의 예외)
    - `backend/src/main/java/com/prlens/` 아래 패키지 뼈대 생성(ADR 0003): `model`, `support`, `github`, `pullrequest`, `diff`, `glob`, `filter`, `context`, `llm`, `review`, `codec`, `output`, `config`, `cli`
    - `src/test/java/com/prlens/testkit/` 패키지 생성
    - _Requirements: 17.3_
  - [ ] 1.2 의존성 추가와 아키텍처 규칙 (선머지 대상 아님. ADR-0004·0005 결정 후 리드가 진행)
    - `backend/build.gradle.kts`에 picocli, jqwik, ArchUnit, Anthropic Java SDK를 버전 고정으로 추가 (PR 설명에 추가 이유 기록). 의존성 추가는 사람의 승인이 필요합니다. YAML 파서(snakeyaml)와 Jackson 3은 Spring Boot가 이미 가져오므로 추가하지 않습니다. 임시 프로젝트에서 함께 동작을 확인한 버전은 jqwik 1.10.1, archunit-junit5 1.5.1, picocli 4.7.7, anthropic-java 2.68.0입니다
    - ArchUnit 규칙 작성: `model`은 JDK 외 의존 금지, `review`는 `cli`·`output`·`github`·`config` 의존 금지, `review`는 `AnthropicLlmClient` 참조 금지, `context`·`pullrequest`는 `HttpGitHubClient` 참조 금지, Spring 애너테이션은 `cli` 조립 코드에만(스타터의 `common`과 Application 클래스는 예외로 둠), `println` 사용 금지, `com.fasterxml.jackson.databind` 사용 금지(Jackson 3 `tools.jackson`만 사용)
    - 대상 클래스가 아직 없는 규칙은 `allowEmptyShould(true)`로 둡니다(ArchUnit은 기본값에서 대상이 없는 규칙을 실패 처리)
    - `%n` 금지는 ArchUnit이 문자열 리터럴을 보지 못하므로 소스 파일을 읽는 테스트로 검사합니다
    - _Requirements: 17.3, 22.5_

- [ ] 2. 공유 타입과 경계 인터페이스 정의
  - PR 경계: 2.1~2.3 / 2.4~2.6 (앞쪽은 월요일 선머지)
  - [ ] 2.1 `model` 패키지 레코드 작성
    - `RepoRef`, `PullRequestSnapshot`, `ChangedFile`, `FileStatus`, `PatchContent`(sealed: `Parsed`, `Absent`, `Unparseable`), `Hunk`, `DiffLine`, `LineKind`
    - `ReviewContext`(경로 중복 시 생성 거부), `ContextFile`, `ContextSource`, `Revision`
    - `ReviewResult`, `ExcludedFile`, `ResultStatus`, `IncompleteReason`, `IncompleteDetails`, `ChunkIssue`(원본 응답 발췌 포함), `FileCountGap`, `ReviewStats`, `ReviewMode`, `Finding`, `Basis`, `Demotion`, `Severity`, `Category`, `BasisType`, `LineVerdict`, `SummaryOnlyReason`, `Usage`
    - `Configuration`(`defaults()` 포함), `ModelPricing`
    - compact constructor 규칙: `requireNonNull(x, "필드이름")`, `List.copyOf`, 맵은 `unmodifiableSortedMap(new TreeMap<>(m))`, `BigDecimal` 정규화(비용 `setScale(4)`, 단가 `stripTrailingZeros`), 토큰 수 음수 거부
    - 불변식 검사: `status == COMPLETE ⇔ incompleteReasons 비어 있음`, `verdict == SUMMARY_ONLY ⇔ summaryOnlyReason != null`, `excludedFiles == excludedFileDetails의 path 목록`, `PullRequestSnapshot.body` null → `""`
    - _Requirements: 1.2, 17.1, 17.2, 17.4, 17.5, 17.6, 17.7, 17.8_
  - [ ] 2.2 `RepoPaths.normalize` 구현 (`\` → `/`, 앞의 `./`·`/` 반복 제거, `..`은 해석하지 않음)
    - 여러 패키지가 쓰므로 `model`에 둡니다(설계 문서에 위치가 없어 정한 값)
    - _Requirements: 4.8, 11.1, 22.2, 22.6_
  - [ ] 2.3 경계 인터페이스 작성 (시그니처는 design.md "공유 경계"의 코드가 기준)
    - `github`: `GitHubCredentials`, `GitHubClient`(`getPullRequest`, `listFiles`, `getTree`, `getFile`), `PullRequestMeta`, `FilePage`, `RepoTree`, `FileFetch`(`Found`/`NotFound`/`IsDirectory`/`TooLarge`)
    - `pullrequest`, `context`: `PrFetcher.fetch(RepoRef, int)`, `ContextCollector.collect(PullRequestSnapshot, Configuration)`의 생성자와 시그니처(본문은 `UnsupportedOperationException`)
    - `llm`: `LlmClient`, `LlmRequest`(모델, 최대 출력 토큰, effort, system, 캐시 블록, 데이터 블록, JSON 스키마), `LlmResponse`(stopReason, text, usage), `LlmUsage`, `LlmApiException`(상태 코드, 오류 종류, `retry-after` 원문)
    - `review`: `ReviewEngine` 생성자와 `review(snapshot, context, config)` 시그니처(본문은 `UnsupportedOperationException`), `ReviewListener`
    - `support`: `RetryListener`(대기 시간은 `Duration`), `Sleeper`, `Warning(code, message)`, `WarningSink`, `PrLensException`(sealed가 아닌 추상 클래스), `Attempt<T>`(`Success`/`Retryable`/`Fatal`)와 `RetryExecutor.execute(api, call)` 시그니처
    - T2 순수 함수의 스텁(본문은 `UnsupportedOperationException`): `GlobMatcher.matches`/`validate`, `DiffPrinter.print`, `LineRanges.of`/`contains`, `DiffFilter.apply`, `FilterOutcome(targets, excluded, warnings)`. T1과 T3가 구현을 기다리지 않게 하려는 것이고, 구현은 5.2, 5.3, 6.1, 6.3이 채웁니다
    - 생성자 시그니처만 먼저 고정: `RetryExecutor(RetryPolicy, Sleeper, RetryListener)`, `HttpGitHubClient(HttpClient, GitHubCredentials, RetryExecutor)`, `RetryingLlmClient(LlmClient, RetryExecutor)` (P2가 작업마다 다시 조립)
    - _Requirements: 17.2, 17.3, 17.9_
  - [ ]* 2.4 Property 43: 공유 타입 불변성과 값 동등성 테스트
    - **Validates: Requirements 17.4, 17.5, 17.6, 17.8**
  - [ ]* 2.5 Property 22: 경로 정규화 비교 테스트
    - **Validates: Requirements 11.1, 22.2, 22.6**
  - [ ] 2.6 `testkit` 공용 생성기 기본형 작성 (jqwik이 필요하므로 1.2 뒤)
    - 저장소 경로(대소문자, `./`, `\` 변형), `ReviewResult`(한글·따옴표·역슬래시·줄바꿈·탭·이모지 문자열, null 선택 필드, 빈 목록), `Configuration`
    - 트랙별 생성기(Hunk, glob, import 그래프, 응답 수열)는 각 트랙이 추가합니다
    - _Requirements: 17.8_

- [ ] 3. `support`: 재시도와 비밀정보 마스킹 (선머지 대상이 아님. 2.3이 머지되면 트랙과 병렬로 진행)
  - [ ] 3.1 `RetryPolicy`, `RetryExecutor` 구현
    - 첫 시도 + 재시도 n회(0~10), 대기 = 호출자가 정한 값(GitHub rate limit) 또는 유효한 `retry-after` 또는 `min(2^(k-1), 30)`초. 대기가 60초를 넘으면 남은 시도 횟수와 관계없이 즉시 `RetryAfterTooLongException`(이 검사를 "마지막 시도" 검사보다 먼저 합니다)
    - 재시도 대상: 429, 5xx, 연결 실패, 제한 시간 초과, 그리고 호출자가 재시도 대상으로 분류한 응답(GitHub rate limit 403/429, 대기 시간은 호출자가 함께 전달)
    - `execute`는 호출자가 돌려준 `Attempt`로 판단: `Success`면 값 반환, `Fatal`이면 `NonRetryableApiException`, `Retryable`이면 대기 후 재시도. 호출자가 던진 예외(`GitHubApiException` 등)는 그대로 통과
    - 예외(모두 `support`에 정의, `PrLensException` 하위 타입, final 아님): `RetriesExhaustedException(api, lastStatus, attempts)`, `RetryAfterTooLongException(api, seconds)`, `NonRetryableApiException(api, status)`
    - HTTP 날짜·음수·소수 `retry-after`는 무효로 보고 지수 백오프 사용
    - 재시도마다 `RetryListener.onRetry(api, nextAttempt, maxAttempts, status, wait)` 호출(`wait`는 정책이 계산한 `Duration`)
    - _Requirements: 1.13, 21.1, 21.3, 21.4, 21.5, 21.6, 21.7, 21.8, 21.9, 21.10, 21.11_
  - [ ]* 3.2 Property 42: 재시도 정책 테스트 (가짜 `Sleeper`)
    - **Validates: Requirements 21.1, 21.3, 21.4, 21.5, 21.8, 21.9, 21.10, 21.11**
  - [ ] 3.3 `SecretMasker`, `MaskingPrintStream` 구현
    - 비밀값 모든 출현 위치(겹침 포함)를 덮고 연속 구간마다 `***` 하나로 치환, 빈 값·공백 값 제외
    - `MaskingPrintStream`: `FileDescriptor.out/err` 기반, BOM 없는 UTF-8, 줄 단위 버퍼
    - 헤더 마스킹 함수: `Authorization`, `x-api-key` 이름 대소문자 무시
    - _Requirements: 19.4, 19.5, 19.7, 22.3_
  - [ ]* 3.4 Property 39: 비밀정보 치환 테스트 (`*` 없는 비밀값으로 한정, 설계 G-3)
    - **Validates: Requirements 19.4, 19.5, 19.7**

- [ ] 4. Checkpoint: 공유 부분(작업 1~3) 완료 확인
  - `./gradlew test`와 ArchUnit 규칙이 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다. 트랙 시작 조건은 이 Checkpoint가 아니라 작업 1, 2.1~2.3의 머지입니다.

### T2 GitHub 연동 (A)

- [ ] 5. diff 해석과 출력
  - [ ] 5.1 `DiffParser` 구현
    - CRLF·단독 CR을 LF로 정규화, 끝 LF 하나 제거 후 줄 분할, 빈 문자열은 빈 목록
    - 상태 기계(`EXPECT_HEADER` → `IN_HUNK`), 헤더 정규식, 생략된 줄 수 = 1, `sectionHeading` 원문 보존, `\` 줄은 직전 줄 `noNewlineAtEnd`
    - 오류: `DiffParseException(path, lineNo, hunkIndex, kind)` — 헤더 문법, 줄 수 불일치, 잘못된 접두어, 첫 헤더 앞 내용, 겹침·비증가. 부분 결과 없음
    - 예시 테스트: 줄 수를 생략한 헤더(`@@ -3 +3 @@`)는 줄 수 1로 해석(요구사항 2.2. `DiffPrinter`가 생략형을 출력하지 않아 round-trip 속성으로는 검증되지 않음)
    - _Requirements: 2.1, 2.2, 2.5, 2.6, 2.7, 22.4_
  - [ ] 5.2 `DiffPrinter` 구현 (`@@ -a,b +c,d @@` + `sectionHeading`, LF, `\ No newline at end of file`) (T1이 사용, 화요일 목표)
    - _Requirements: 2.8_
  - [ ] 5.3 `LineRanges.of(hunks)` 구현 (head 줄 수 ≥ 1 구간만, 정렬·병합, 이진 탐색 `contains`) (T1이 사용, 화요일 목표)
    - _Requirements: 2.3, 2.4_
  - [ ]* 5.4 Property 3: diff round-trip 테스트
    - **Validates: Requirements 2.1, 2.5, 2.8, 2.9**
  - [ ]* 5.5 Property 4: 추가·삭제 줄 수 보존 테스트
    - **Validates: Requirements 2.10**
  - [ ]* 5.6 Property 5: Changed_Line_Range 모델 일치 테스트
    - **Validates: Requirements 2.3, 2.4**
  - [ ]* 5.7 Property 6: 잘못된 patch 거부 테스트
    - **Validates: Requirements 2.7**
  - [ ]* 5.8 Property 7(diff 부분): 줄바꿈 무관 해석 테스트
    - **Validates: Requirements 22.4**

- [ ] 6. glob과 diff 필터
  - [ ] 6.1 `GlobMatcher`, `GlobSyntaxException` 구현 (T3가 사용, 화요일 목표)
    - `PathMatcher` 미사용. `/` 구간 분할, `**` 구간 = 0개 이상 구간, `*`·`?`·`{a,b}`(중첩) 구간 정규식, 메모이제이션 DP
    - `/`를 포함한 중괄호만 먼저 펼친 뒤 컴파일
    - 문법 오류: 짝 없는 중괄호, 빈 패턴, `\` 포함, 구간 일부의 `**`, 펼친 개수 256 초과
    - 예시 테스트: `**/x`가 루트 `x`와 일치, 대소문자 구분
    - _Requirements: 3.2, 5.7_
  - [ ]* 6.2 Property 12: glob 중괄호 모델 기반 테스트
    - **Validates: Requirements 5.8**
  - [ ] 6.3 `DiffFilter` 구현 (T1이 사용, 화요일 목표)
    - 기본 패턴 목록(요구사항 3.1 순서), 합성 = 기본 − (해제 ∩ 비밀정보 아닌 기본) + 추가
    - 비밀정보 패턴 해제 요청은 무시하고 경고를 `FilterOutcome`에 담아 반환. 제외 파일은 경로와 사유(`ExcludedFile`)로 반환
    - 판정 순서: 새 경로·이전 경로 패턴 일치(처음 일치 패턴) → `Absent`는 `binary_or_too_large` → `Unparseable`은 `unparseable_patch`(설계 G-1) → 대상. 입력 순서 유지
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.8, 3.9, 3.10_
  - [ ]* 6.4 Property 8: 필터 분할 불변과 사유 결정 테스트
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.5, 3.12**
  - [ ]* 6.5 Property 9: 필터 멱등 테스트
    - **Validates: Requirements 3.13**
  - [ ]* 6.6 Property 10: 제외 패턴 목록 합성 테스트
    - **Validates: Requirements 3.8, 3.9, 3.10**

- [ ] 7. GitHub 클라이언트와 PR 가져오기
  - PR 경계: 7.1 / 7.2~7.5 / 7.6~7.7
  - [ ] 7.1 `HttpGitHubClient`, PAT용 `GitHubCredentials` 구현
    - 생성자 `HttpGitHubClient(HttpClient, GitHubCredentials, RetryExecutor)`. 요청 제한 시간 30초, 모든 요청을 `GitHubCredentials`로 인증(P1은 `GITHUB_TOKEN`)
    - `RetryExecutor` 적용. rate limit 분류(design.md "재시도 정책"의 표): 403/429에 `retry-after`가 있으면 그 값, 없고 `x-ratelimit-remaining == 0`이면 `x-ratelimit-reset`까지 남은 시간, 둘 다 아니고 본문이 secondary rate limit을 알리면 60초를 `Attempt.Retryable`의 대기 시간으로 전달. 그 밖의 403은 권한 부족
    - 401, 404, rate limit이 아닌 403은 `HttpGitHubClient`가 직접 `GitHubApiException`(상태 코드와 원인 후보 메시지)으로 던짐. 그 밖의 429 외 4xx는 `Attempt.Fatal`로 돌려줌
    - contents API(base64) 조회, 404 → `NotFound`, 폴더 → `IsDirectory`. 재귀 트리 조회와 `truncated` 전달. 1MB 초과 파일(`encoding: "none"`, 빈 `content`)은 빈 문자열로 넘기지 말고 `FileFetch.TooLarge`로 돌려줌(설계 G-12)
    - HTTP 디버그 로그(기본 꺼짐)는 헤더 마스킹 함수를 거쳐 기록
    - _Requirements: 1.8, 1.11, 1.13, 19.5, 19.6, 21.2, 21.9, 21.10, 21.11_
  - [ ] 7.2 `PullRequestUrl` 구현 (설계의 정규식, 번호 `long` 파싱 후 2,147,483,647 이하 확인, `github.com`만 허용, `repo()`와 `number()` 제공)
    - _Requirements: 1.3, 1.4, 1.9_
  - [ ]* 7.3 Property 1: PR URL 파싱 round-trip과 거부 테스트
    - **Validates: Requirements 1.3, 1.4, 1.9**
  - [ ] 7.4 `PrFetcher` 구현 (`fetch(RepoRef, int)`, 경고는 `WarningSink`로)
    - 메타데이터 조회, 파일 목록 `per_page=100` 페이지 조회(누적 수 == `changed_files` 또는 빈 페이지/next 없음에서 중단), 같은 `filename` 버림
    - 상태 매핑: `copied` → `ADDED`, `unchanged` → `MODIFIED`(설계 G-6), 이름 변경은 이전 경로 기록
    - patch 없음 → `Absent`, `DiffParseException` → `Unparseable` + 경고
    - _Requirements: 1.1, 1.2, 1.5, 1.6, 1.7, 1.12, 1.14_
  - [ ]* 7.5 Property 2: 파일 목록 조회의 순서·중복·개수 테스트 (절단 판정은 엔진이 하므로 12.2의 Property 44)
    - **Validates: Requirements 1.7**
  - [ ]* 7.6 GitHub 가짜 서버 통합 테스트
    - 401/403/404 메시지, rate limit 응답 세 종류(`retry-after`, remaining 0 + reset, secondary)의 분류와 대기 시간, 60초 초과 시 즉시 종료, 짧게 주입한 타임아웃, `Authorization` 헤더, base/head ref 선택, 트리 `truncated` 대체 경로
    - _Requirements: 1.8, 1.11, 1.13, 21.2, 21.9, 21.10, 21.11_

  - [ ] 7.7 실제 GitHub 스모크 (수요일까지. `GITHUB_TOKEN`이 있을 때만 도는 수동 실행 테스트)
    - 팀 저장소의 실제 PR 1건을 `PrFetcher`로 가져와 파일 수, patch 해석, `changed_files`와 받은 파일 수가 같은지 확인
    - base SHA로 재귀 트리 조회와 contents 조회가 되는지 확인
    - 결과를 PR 설명이나 `docs/spikes/`에 기록. CI에서는 실행하지 않음(요구사항 22.7)
    - _Requirements: 1.1, 1.5, 1.7, 1.14, 4.1_

- [ ] 8. 컨텍스트 수집
  - PR 경계: 8.1~8.5 / 8.6~8.8 / 8.9~8.12
  - [ ] 8.1 `RepoTreeIndex` 구현 (base SHA 재귀 트리 → 경로별 blob/tree, `truncated`이면 경로별 조회로 대체하고 rule 수집은 경고 후 건너뜀, 설계 G-9)
    - _Requirements: 4.1, 4.9_
  - [ ] 8.2 `CLAUDE.md` 수집 구현
    - 루트 `CLAUDE.md`, `.claude/CLAUDE.md`(대소문자 정확히 일치), 대상 파일(이름 변경이면 두 경로) 상위 폴더를 가까운 순으로, 루트 제외, 중복 없음, 제외 파일 경로 미사용
    - 모두 base SHA 기준, 404는 경고 없이 건너뜀
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.9, 4.10_
  - [ ]* 8.3 Property 13: 상위 폴더 CLAUDE.md 후보 테스트
    - **Validates: Requirements 4.4, 4.5, 4.6, 4.7**
  - [ ] 8.4 `FrontmatterParser`와 rule 선택 구현
    - 첫 줄 `---`일 때만 다음 `---`까지 안전 YAML 로더로 해석, `paths` 문자열 → 한 개짜리 목록, CRLF 정규화
    - 오류 `YAML_ERROR`, `UNCLOSED`, `INVALID_PATHS`는 포함 + 경고
    - `.claude/rules/**` 중 소문자 `.md`, `paths` 없으면 포함, 있으면 대상 파일(이름 변경이면 이전 경로 또는 새 경로)과 일치할 때만 포함
    - 무효 glob 패턴은 그 패턴만 무시하고 경고, 모두 무효면 포함 + 경고(설계 G-4)
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.9, 5.10, 5.11, 22.4_
  - [ ]* 8.5 Property 14(rule 선택)와 Property 7(프런트매터 부분) 테스트
    - **Validates: Requirements 5.4, 5.5, 5.6, 22.4**
  - [ ] 8.6 `ImportResolver` 구현
    - 펜스 블록(닫히지 않으면 파일 끝까지)과 인라인 코드 건너뛰기, `(^|\s)@(\S+)` 인식
    - 선언 파일 폴더 기준 해석과 `.`/`..` 정규화, `~/`·`/`·드라이브 문자·루트 위 `..` 거부 + 경고
    - BFS `(path, depth, declaredBy)`, 깊이 1~4만 가져오고 5 이상은 경고, 이미 담긴 경로는 건너뜀, 404·폴더는 경고
    - `claude_md`와 `import` 출처에서만 import 탐색
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 6.10_
  - [ ]* 8.7 Property 15: import 도달 가능성 불변 테스트
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.5, 6.6, 6.7, 6.8, 6.11**
  - [ ]* 8.8 Property 16: import 인식 메타모픽 테스트
    - **Validates: Requirements 6.4, 6.10**
  - [ ] 8.9 `SpecLinkExtractor`와 스펙 조회 구현
    - 상대 경로·같은 저장소 URL 두 정규식을 본문 순서로 병합, 앵커·쿼리 제거, 다른 저장소(대소문자 무시)·`..`·`docs/specs/` 밖은 경고 후 건너뜀
    - 중복 제거, 최대 20개(초과 개수 경고)
    - 조회: base → 404일 때만 head(`Revision.HEAD`), 둘 다 없거나 폴더면 경고
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_
  - [ ]* 8.10 Property 17: 스펙 링크 추출 테스트
    - **Validates: Requirements 7.1, 7.3, 7.5, 7.6, 7.7**
  - [ ] 8.11 `ContextCollector` 조립
    - `DiffFilter`로 대상 파일 결정 → `claude_md` → `rule` → `import` → `spec` 순서로 누적기(`LinkedHashMap`)에 수집. 같은 경로가 다시 들어오면 우선순위가 더 높은 출처를 남김(수집 순서가 우선순위와 같아 실제로는 기존 출처가 남음)
    - 404 외 오류(401, 403, 재시도 소진)는 예외로 전파
    - 경고는 생성자로 받은 `WarningSink`에 `Warning(code, message)`로 전달. 순수 함수가 돌려준 경고도 여기로 넘김
    - _Requirements: 4.8, 4.11, 6.7, 8.2, 8.5_
  - [ ]* 8.12 Property 18: 컨텍스트 경로 유일성과 출처 우선순위 테스트
    - **Validates: Requirements 4.8, 8.2, 8.5**

- [ ] 9. Checkpoint: T2 완료 확인
  - T2 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### T1 리뷰 엔진 (B)

- [ ] 10. 응답 해석과 Finding 검증
  - [ ] 10.1 `ReviewJsonSchema` 작성 (설계의 JSON 스키마, 모든 속성 `required`, null 허용 3개, `minimum`·`minLength` 없음) 과 스냅샷 테스트
    - _Requirements: 9.1, 9.2, 9.3_
  - [ ] 10.2 `ResponseParser` 구현
    - 정상: Jackson 트리 해석 → 로컬 검증(enum 대소문자 무시 후 소문자 정규화(설계 G-7), `line ≥ 1`, `message` 비어 있지 않음), 실패 시 `schema_violation` + 원본 앞 2,000자
    - `refusal`: 본문 미해석, `findings=[]`
    - `max_tokens`, `model_context_window_exceeded`: 스트리밍 파서로 완전히 닫힌 유효 Finding만 유지, `summary` 없으면 `""`, 사유는 둘 다 `max_tokens`
    - 그 밖의 `stop_reason`(`stop_sequence`, `tool_use`, `pause_turn`)은 `schema_violation`
    - 결과는 비공개 `RawFinding` 목록과 usage
    - _Requirements: 9.4, 9.5, 9.6, 16.1, 16.2, 20.1_
  - [ ]* 10.3 Property 30(정상 응답 변환), Property 31(스키마 위반), Property 32(max_tokens 절단 복구) 테스트
    - **Validates: Requirements 9.4, 9.5, 9.6, 16.2, 20.1**
  - [ ] 10.4 `FindingValidator` 구현
    - basis: `rule`/`spec`이고 `ref`가 공백이거나 컨텍스트 경로에 없으면 `general`로 강등 + `Demotion`
    - 라인: 전체 대상 파일의 `head 경로 → LineRanges` 맵으로 `not_target_file` → `line_missing` → `out_of_range` → `INLINE_ELIGIBLE`
    - `basis`, `demotion`, 판정 필드 외에는 변경하지 않음
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_
  - [ ]* 10.5 Property 23, 24, 25, 26 테스트 (basis 검증 불변, 멱등, 필드 보존, 라인 판정)
    - **Validates: Requirements 11.2, 11.3, 11.4, 11.5, 11.8, 11.9, 11.10, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.10, 12.11**

- [ ] 11. 청크 계획, 프롬프트, 비용, 병합
  - PR 경계: 11.1~11.4 / 11.5~11.9
  - [ ] 11.1 모드 결정과 `ChunkPlanner` 구현
    - 모드: 대상 0개 `NO_TARGET`, count ≤ L 단일(count 0 포함, 설계 G-2), ≤ 3L 분할, 초과 요약 전용
    - 분할: head 경로 코드 포인트 정렬 후 결정적 순차 greedy, L 초과 파일은 단독 Chunk
    - _Requirements: 3.6, 13.1, 13.2, 13.3, 13.4, 13.9, 13.10_
  - [ ]* 11.2 Property 28: 청크 계획 테스트
    - **Validates: Requirements 13.2, 13.3, 13.4, 13.13, 13.14**
  - [ ] 11.3 `DelimiterCodec` 구현 (`<~*/?prlens:` 바이트 스터핑 escape/unescape, 대소문자 무시)
    - _Requirements: 14.5, 14.8_
  - [ ]* 11.4 Property 33: Delimiter 이스케이프 테스트
    - **Validates: Requirements 14.2, 14.5, 14.8**
  - [ ] 11.5 `PromptBuilder` 구현
    - system: 리뷰 지시문(`PROMPT_VERSION`), 출력 규칙, 인젝션 규칙, `<~` 이스케이프 설명. PR·diff 텍스트 없음
    - 출력 규칙에 "`basis.ref`는 `team_context` 파일 머리줄의 `path`를 글자 그대로, 근거 파일이 없으면 `general`과 null, `file`은 `file_path` 값 그대로"를 넣음(설계 "PromptBuilder와 요청 배치"). 빠지면 근거가 모두 `general`로 강등돼 M1 데모 조건을 못 채움
    - 첫 user 블록: `<prlens:team_context>` 안 Common_Context(출처 순서 → 경로 오름차순, 파일별 `path`/`source` 머리줄), 블록 끝 `cache_control`
    - 둘째 user 블록: 제목, 본문, head 스펙, Chunk 파일별 경로와 `DiffPrinter` 출력을 각각 Delimiter_Tag로 감싸고 이스케이프
    - 요약 전용: patch 대신 경로·상태·줄 수·Hunk 헤더, 같은 스키마 사용
    - 요청마다 모델과 effort 명시, 최대 출력 토큰 지정
    - _Requirements: 3.7, 9.1, 14.1, 14.2, 14.3, 14.4, 14.6, 20.6, 20.7, 20.8, 20.9_
  - [ ] 11.6 `CostCalculator` 구현 (공개 순수 함수 `estimate(LlmUsage, ModelPricing)`, `BigDecimal` Σ(토큰 × 단가 / 1,000,000), `setScale(4, HALF_UP)`, 단가 없으면 `null`. P2가 같은 함수를 씀)
    - _Requirements: 20.2, 20.5_
  - [ ]* 11.7 Property 40: 추정 비용 계산 테스트
    - **Validates: Requirements 20.2**
  - [ ] 11.8 `ResultMerger` 구현
    - findings Chunk 순서 연결, Chunk가 둘 이상이면 summary에 `[n/N] 파일 목록` 머리줄, 토큰·비용 합(하나라도 `null`이면 `null`), Incomplete_Reason 첫 등장 순 중복 제거, `ChunkIssue` 기록(`schema_violation`이면 원본 앞 2,000자 포함, 단일·요약 전용 모드는 `chunkIndex` 1), `excludedFiles`와 `excludedFileDetails` 중복 제거
    - _Requirements: 13.5, 13.6, 13.7, 16.5, 20.10_
  - [ ]* 11.9 Property 29: 청크 결과 병합 테스트
    - **Validates: Requirements 13.5, 13.6, 13.7, 16.5, 20.10**

- [ ] 12. `ReviewEngine` 조립
  - [ ] 12.1 `ReviewEngine.review` 구현
    - 설계의 흐름대로 필터 → 모드 → 청크 → Chunk별 `llm.send` → 해석 → 검증 → 비용 → 병합
    - `reportedChangedFiles > files.size()`이면 `files_truncated`와 `FileCountGap`. 이 판정과 `listener.modeDecided`는 대상 0개 분기보다 먼저 합니다
    - 대상 0개는 API 호출 없이 빈 결과(summary 문구, usage 0). 파일 수 부족이 아니면 `complete`, 부족이면 `incomplete`
    - 분할 모드 Chunk의 `RetriesExhausted`는 `chunk_failed`로 흡수, 모두 실패하면 `AllChunksFailedException`. 429 외 4xx와 `RetryAfterTooLong`은 즉시 전파
    - `listener.modeDecided`, `chunkStarted`, 누적 비용 첫 초과 시 `costThresholdExceeded` 1회
    - `DiffFilter`가 돌려준 경고는 버림(수집기가 이미 알림). 엔진은 `WarningSink`를 받지 않음
    - _Requirements: 1.14, 3.7, 9.1, 13.1, 13.5, 13.6, 13.7, 13.8, 13.9, 13.11, 13.12, 20.1, 20.4_
  - [ ]* 12.2 가짜 `LlmClient`로 엔진 속성 테스트
    - Property 27(리뷰 모드와 API 호출 수), Property 11(제외 파일 내용 미전송), Property 34(프롬프트 배치와 캐시 앞부분 동일성), Property 41(누적 비용 경고 1회), Property 44(파일 수 부족 판정)
    - **Validates: Requirements 1.14, 3.6, 3.7, 9.1, 13.1, 13.9, 13.10, 13.11, 14.3, 20.4, 20.6, 20.7**

- [ ] 13. Claude API 어댑터
  - [ ] 13.1 `AnthropicLlmClient` 구현 (1주차 spike 결과 반영)
    - SDK 자체 재시도 끄기, 요청 제한 시간 180초, `ANTHROPIC_API_KEY`는 이 클라이언트에만 전달
    - 요청 매핑: 모델, `max_tokens`, effort, system, user 블록 2개(첫 블록 ephemeral `cache_control`), `output_config.format`
    - 응답 매핑: `stop_reason`, 첫 text 블록, usage 네 토큰(thinking 포함 출력 토큰). HTTP 오류와 네트워크 오류는 `LlmApiException`(상태 코드, 오류 종류, `retry-after`)으로 던짐
    - _Requirements: 9.1, 19.6, 20.1, 20.8, 21.2_
  - [ ] 13.2 `RetryingLlmClient` 데코레이터 구현 (생성자 `RetryingLlmClient(LlmClient, RetryExecutor)`, API 이름 "Claude", `LlmApiException`을 `Attempt.Retryable`/`Fatal`로 분류)
    - _Requirements: 21.1, 21.6, 21.7_
  - [ ]* 13.3 요청·응답 매핑 단위 테스트 (모델·effort 항상 명시, 캐시 지점 위치, usage 필드 매핑)
    - _Requirements: 20.8, 20.9_

  - [ ] 13.4 실제 Claude API 스모크 (수요일까지. `ANTHROPIC_API_KEY`가 있을 때만 도는 수동 실행 테스트)
    - 손으로 만든 작은 `LlmRequest`(짧은 system, 팀 컨텍스트 파일 하나, 작은 diff)를 `RetryingLlmClient(AnthropicLlmClient)`로 실제 호출해 `ResponseParser`까지 통과시킴
    - 확인: 구조화된 출력이 스키마대로 오는지, thinking 블록 뒤의 text 블록 선택, usage 네 토큰과 캐시 필드, `basis.ref`가 컨텍스트 경로와 같은 문자열로 오는지, 응답 시간과 비용
    - 결과를 PR 설명이나 `docs/spikes/`에 기록. CI에서는 실행하지 않음(요구사항 22.7)
    - _Requirements: 9.1, 9.4, 20.1_

- [ ] 14. Checkpoint: T1 완료 확인
  - T1 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### T3 CLI 출력 (C)

- [ ] 15. `ResultCodec` 구현
  - [ ] 15.1 직렬화·역직렬화 구현
    - Jackson 트리 모델로 직접 매핑(애너테이션 없음), `schemaVersion: 1`, camelCase 필드, enum 소문자 snake_case, 선택 필드는 키 유지 + `null`, 목록은 `[]`
    - `estimatedCostUsd`는 JSON 숫자 ↔ `BigDecimal`(부동소수점 경유 금지)
    - 오류: 필드 경로(`findings[2].severity`)와 enum 허용 값, 문법 오류 줄·열, 음수 토큰 경로. 모르는 필드 무시
    - Spring Boot 4의 Jackson 3 패키지(`tools.jackson`)는 spike 결과를 따름
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9, 11.7, 12.9, 16.4_
  - [ ]* 15.2 Property 19(JSON round-trip), Property 20(모르는 필드 무시), Property 21(역직렬화 오류 경로) 테스트
    - **Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.9, 10.10, 10.11, 11.7, 12.9, 16.4**

- [ ] 16. 설정 파일
  - [ ] 16.1 `ConfigLoader`, `ConfigException` 구현
    - `--config` 경로만 읽고 없으면 대체 없이 오류, 옵션이 없으면 `./.prlens.yml` → `~/.prlens.yml` 첫 파일, 없으면 기본값
    - UTF-8로 읽고 허용 키와 대조, 범위 검증(요구사항 18.6, effort 허용 값 포함), 모든 문제를 모아 한 번에 보고
    - `cost.pricing`은 모델 이름 단위로 기본 단가표에 병합, 지정한 모델은 네 단가 필수(설계 G-10)
    - 비밀 키 이름(`token`, `githubToken`, `apiKey`, `anthropicApiKey`, 대소문자·`_`/`-` 무시)은 값을 읽지 않고 환경변수 안내
    - `exclude.add`/`exclude.remove` 패턴을 `GlobMatcher`로 검증
    - _Requirements: 3.11, 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8, 18.9, 19.1, 22.2, 22.3_
  - [ ] 16.2 `ConfigPrinter` 구현 (모든 항목을 고정 순서로, 문자열은 항상 큰따옴표)
    - _Requirements: 18.10_
  - [ ]* 16.3 Property 37(설정 round-trip과 부분 지정), Property 38(설정 검증 오류 수집) 테스트
    - **Validates: Requirements 18.5, 18.6, 18.7, 18.10, 18.11**

- [ ] 17. 출력 형식과 종료 코드
  - [ ] 17.1 `MarkdownFormatter` 구현
    - 구역 순서: [불완전 경고(사유, 불완전 Chunk 파일, 부분 결과 표시)] → 요약(요약 전용이면 "PR을 나누세요", Changed_Line_Count, 3×Size_Limit) → 심각도별 지적 수(4개 모두, 강등 수 한 줄) → 파일별 지적((경로, 라인) 오름차순, 없는 `suggestion`·`basis.ref` 생략) → 라인 밖 지적(사유 포함) → 제외 파일(경로와 제외 사유) → 사용된 컨텍스트 파일 (N)(출처 → 경로 순, 0개 안내) → 토큰 사용량과 추정 비용(넷째 자리, 단가 없으면 "알 수 없음")
    - 빈 구역도 제목과 0건 표시, 줄바꿈은 `\n`
    - _Requirements: 8.1, 8.3, 8.4, 11.6, 12.8, 13.9, 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.14, 16.3, 20.5, 22.5_
  - [ ]* 17.2 Property 35: Markdown 구조와 개수 일관성 테스트
    - **Validates: Requirements 8.1, 8.3, 11.6, 12.8, 15.2, 15.3, 15.4, 15.5, 15.13, 16.3, 22.5**
  - [ ] 17.3 `ExitCodeResolver` 구현 (blocker ≥ 1 → 1, incomplete → 3, 그 외 0)
    - _Requirements: 15.9, 15.10, 16.7, 16.8_
  - [ ]* 17.4 Property 36: 종료 코드 결정 테스트
    - **Validates: Requirements 15.9, 15.10, 16.7, 16.8**

- [ ] 18. CLI 명령과 파이프라인
  - PR 경계: 18.1~18.2 / 18.3~18.6 / 18.7
  - [ ] 18.1 `PrLensMain`, `ReviewCommand` 구현 (picocli, `review <url> [--format markdown|json] [--config <path>]`, 자동 오류 처리 끄고 `UsageException`으로 전달)
    - _Requirements: 15.1, 15.7, 15.12, 22.1_
  - [ ] 18.2 `CliPipeline.preflight` 구현
    - 순서: 환경변수 값을 읽어 `SecretMasker`와 UTF-8 `MaskingPrintStream` 설치(빈 값은 제외) → 인자(URL, `--format`) → 설정 → 환경변수 검사(`GITHUB_TOKEN`, `ANTHROPIC_API_KEY`, 둘 다 없으면 한 메시지). 마스커를 가장 먼저 설치해 인자·설정 오류 메시지도 마스킹합니다
    - 어느 단계든 실패하면 외부 API 호출 없이 종료 코드 2
    - CLI용 `logback.xml` 추가(대상 `System.err`, 기본 수준 WARN). Spring 컨텍스트 없이 실행하면 Logback 기본 설정이 로그를 stdout에 써서 결과 출력에 섞임(요구사항 15.7, 15.8)
    - _Requirements: 1.9, 1.10, 3.11, 15.12, 18.7, 19.1, 19.2, 19.3, 19.4, 22.3_
  - [ ] 18.3 파이프라인 조립 구현
    - Spring 컨텍스트 없이 `HttpGitHubClient`, `PrFetcher`, `ContextCollector`, `RetryingLlmClient(AnthropicLlmClient)`, `ReviewEngine`을 직접 조립
    - 조립 함수는 `GitHubClient`와 `LlmClient`를 만드는 팩토리를 인자로 받음(CLI 통합 테스트가 호출 수를 세는 가짜를 넘길 수 있게)
    - 결과에서 유도하는 경고를 `StderrReporter`로 출력: 파일 수 부족(`fileCountGap`), 단가 없음(`estimatedCostUsd == null`), 1회 비용 상한 초과
    - 출력 문자열을 모두 만든 뒤 stdout에 한 번만 쓰기, `--format json`은 `ResultCodec` 결과 하나만
    - _Requirements: 15.7, 15.8, 17.2_
  - [ ] 18.4 `StderrReporter` 구현 (`ReviewListener`, `RetryListener`, `WarningSink` 구현 겸)
    - 모드·Changed_Line_Count·Size_Limit·Chunk 수, 분할 모드 "n/N", 재시도 한 줄(API, "n/최대", 상태, 대기 초), 수집·필터 경고, 파일 수 부족 경고, 불완전 경고와 사유, 비용 상한 초과, 누적 비용 초과, 단가 없음 경고
    - _Requirements: 1.14, 3.10, 13.12, 15.8, 16.6, 20.3, 20.4, 20.5, 21.7_
  - [ ] 18.5 예외 → 종료 코드 매핑 구현 (2.3에서 만든 `PrLensException`의 하위 타입별 매핑, 설계의 오류 표, 예상하지 못한 예외는 "내부 오류" + 마스킹한 스택(디버그 모드), 종료 코드 2 경로에서 stdout 비움)
    - _Requirements: 1.11, 4.11, 13.8, 15.11, 21.5, 21.6, 21.8_
  - [ ]* 18.6 CLI 통합 테스트
    - 잘못된 URL·`--format`·설정·환경변수 사례별 종료 코드 2, 빈 stdout, 가짜 클라이언트 호출 수 0, 비밀 항목 오류에서 값 미출력, `--config` 파일 없음
    - _Requirements: 1.9, 1.10, 15.11, 15.12, 18.8, 18.9, 19.2, 19.3_
  - [ ] 18.7 실행 패키징 (실행 jar와 래퍼 스크립트 `prlens`(sh), `prlens.cmd`, README에 `chcp 65001` 안내)
    - `build.gradle.kts`에 `springBoot { mainClass = ... }`로 CLI 진입점(`PrLensMain`)을 지정. 지정하지 않으면 `java -jar`가 Spring 애플리케이션 클래스를 골라 웹 서버를 띄움(임시 프로젝트로 확인). `build.gradle.kts` 수정은 사람의 승인이 필요
    - _Requirements: 22.1_

- [ ] 19. Checkpoint: T3 완료 확인
  - T3 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### 컨텍스트 (컨텍스트 담당 A, 트랙과 병렬)

- [ ] 23. Claude Code 컨텍스트를 PR Lens에 맞추기 (ROADMAP 2주차 "컨텍스트 담당 A의 추가 작업")
  - PR 경계: 23.1 / 23.2
  - [ ] 23.1 ADR 0003 후속 정리 (월요일, 1.1과 함께. `backend/CLAUDE.md`, `testing.md`, `reviewer.md`, 루트 `CLAUDE.md`는 2026-10-04에 반영했으므로 남은 것은 `new-endpoint` 스킬)
    - `backend/CLAUDE.md`의 Architecture 절과 단일 테스트 예시, `.claude/agents/reviewer.md`의 레이어 규칙, `.claude/skills/new-endpoint`를 평면 패키지 기준으로 고침
    - `.claude/rules/backend/testing.md`에 jqwik(속성 하나 = `@Property` 하나, 태그 주석), ArchUnit, `testkit` 위치, 픽스처 규칙(실제 `.env`나 `secrets/` 경로의 파일을 만들지 않음, 반례는 예시 테스트로 고정)을 추가하고 메모 예시를 교체
    - 루트 `CLAUDE.md`의 브랜치 규칙(`feat/<track>-<slug>`), 워크플로 문구(tasks.md 작업 단위 구현), web `types.ts` 규칙의 P3 전 예외를 고침
  - [ ] 23.2 모듈별 rules 작성 (수요일까지 `main`에 머지)
    - `.claude/rules/backend/github.md`(`paths`: `github`, `pullrequest`, `diff`, `glob`, `filter`, `context` 패키지)와 `review.md`(`paths`: `llm`, `review` 패키지). 첫 며칠 동안 리뷰에서 반복된 지적을 규칙으로 옮김
    - PR Lens는 컨텍스트를 base SHA에서만 읽으므로(요구사항 4.1, 5.1), 이 rules가 `main`에 있어야 금요일 데모 PR의 지적에 규칙 근거가 붙음. 지금 P1 코드 경로에 매칭되는 rule은 `testing.md` 하나뿐임

### 통합 (T3 주도, 전원, 목~금)

- [ ] 20. 골든 픽스처와 전체 파이프라인 테스트
  - PR 경계: 20.1 / 20.2~20.3 (20.4는 실행 기록이라 코드 PR이 아님)
  - [ ] 20.1 테스트 픽스처 작성 (`src/test/resources/fixtures/<name>/`) (수요일부터 시작 가능. 공유 타입과 `ResultCodec`만 있으면 됨)
    - 실제 PR diff 샘플 3~5개: 단일 모드, 분할 모드, 제외 파일 포함
    - 인젝션 픽스처 4개: diff 추가 줄, PR 제목, PR 본문, diff 안 닫는 Delimiter_Tag. 기대 결과에 인젝션 위치 `security` Finding과 심어 둔 `blocker` Finding
    - 픽스처마다 `snapshot.json`, `context.json`, `llm-responses/*.json`, `expected-result.json`, `expected.md`. 비밀정보·개인정보는 가짜 값
    - `snapshot.json`과 `context.json`을 읽는 픽스처 로더를 `testkit`에 작성(테스트 전용. `model`에는 Jackson 애너테이션을 넣지 않음)
    - 픽스처 폴더를 `.gitattributes`에 `-text`로 지정(줄 끝 변환 방지). Stop hook은 `.md`만 바뀐 경우 검증을 건너뛰므로 `expected.md`만 고쳤을 때는 `./gradlew test`를 직접 실행
    - _Requirements: 14.7, 17.10, 17.11, 17.12_
  - [ ] 20.2 골든 파이프라인 테스트 작성 (픽스처의 스냅샷과 컨텍스트를 주입해 엔진과 출력을 실행. 가짜 `LlmClient`, stdout 바이트와 종료 코드 비교. `--format json` 출력 전체가 `ResultCodec`으로 다시 읽히는지도 확인)
    - `PrFetcher`와 `ContextCollector`는 이 테스트를 거치지 않음(각자의 테스트 7.x, 8.x로 검증)
    - _Requirements: 14.7, 15.7, 17.9, 17.10, 22.8_
  - [ ] 20.3 픽스처 비밀정보 스캔 테스트 (토큰 형식 정규식)
    - _Requirements: 17.12_
  - [ ] 20.4 M1 데모 리허설 (목요일. 실제 API, 사람이 실행)
    - 팀 저장소의 실제 PR 1건을 패키징한 `prlens review`로 리뷰. 규칙 근거(`basis.type`이 `rule`)가 붙은 지적이 1건 이상 나오는지 확인(ROADMAP 2주차 완료 조건). 안 나오면 강등 기록(`demotion`)을 보고 프롬프트의 출력 규칙이나 rules를 고침
    - 400줄 PR의 실행 시간 측정(요구사항 9.7. 재시도 대기 제외, 첫 호출과 그 뒤 호출을 따로 기록), 인젝션 픽스처를 실제 모델로 한 번 실행
    - 결과를 PR이나 `docs/spikes/`에 기록
    - _Requirements: 9.7, 14.7_

- [ ] 21. CI 매트릭스 구성
  - GitHub Actions에 `ubuntu-latest`, `windows-latest`, `macos-latest` × Java 17로 `./gradlew test` 실행, 실제 API 호출 없음, 세 OS 모두 통과해야 통과
  - 1.1이 머지되면 바로 만듭니다. OS별 문제(줄바꿈, 인코딩, 경로)가 금요일 전에 드러나야 합니다
  - _Requirements: 22.7, 22.8_

- [ ] 22. Final Checkpoint
  - 전체 테스트와 세 OS CI가 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

## Notes

- 트랙 담당은 ROADMAP 2주차 기본안(T1 B, T2 A, T3 C)을 따르며 월요일 킥오프에서 바뀔 수 있습니다.
- 1주차 spike 결과에 따라 세부 구현이 달라지는 작업은 다음과 같습니다. spike 확인 목록은 design.md "1주차 spike 확인 목록"에 있습니다.
  - 13.1, 13.2, 3.1: SDK 빌더 이름, `retry-after` 헤더 접근 방법
  - 7.1: secondary rate limit 본문 문구
  - 18.7: 래퍼 스크립트
  - 2.1: `Configuration.defaults()`의 effort와 최대 출력 토큰(ADR-0005)
- 실제 API를 쓰는 확인은 세 번 있습니다: 7.7과 13.4(수요일, 어댑터 단위), 20.4(목요일, 전체 리허설). 400줄 120초 측정(요구사항 9.7), 실제 모델의 인젝션 픽스처 동작, 규칙 근거가 붙은 지적 1건 이상(M1 완료 조건)은 20.4에서 확인합니다.
- design.md "요구사항 공백" G-1~G-7과 G-9, G-10, G-12의 잠정 처리는 해당 작업에 반영했습니다. G-8과 G-11은 결정 전이라 작업이 없습니다. 요구사항을 고치기로 하면 관련 작업도 함께 수정합니다.
- 결정 대기 항목이 막는 작업은 다음과 같습니다. 결정 전에는 제안값으로 진행하고, 결정이 바뀌면 이 작업들을 다시 봅니다.

  | 항목 | 막는 작업 |
  |---|---|
  | D-1 CLI 라이브러리 (ADR-0004) | 1.2, 18.1, 18.2, 18.3, 18.5, 18.6, 18.7 |
  | D-2 모델·effort·최대 출력 토큰·fallback (ADR-0005) | 1.2, 2.1, 11.5, 13.1, 13.3, 16.1, 16.2 |
  | D-3 비용 상한 | 2.1, 16.1, 18.4 |
  | D-4 불완전 결과와 요약 전용 모드의 종료 코드 | 17.3, 17.4, 18.5, 20.1, 20.2 |
  | D-5 설정 파일 형식·위치 | 16.1, 16.2, 16.3, 18.2 |
  | D-6 생성 코드 기본 패턴 | 6.3, 6.4, 6.6, 20.1 |
  | D-7 스펙 head SHA fallback | 2.1, 8.9, 8.10, 11.5 |
  | D-8 리뷰 대상 저장소 한정 | 결정되면 요구사항과 작업을 추가 |
- 이 스펙은 2주차 세 트랙을 한 건으로 묶은 것입니다(ROADMAP 1주차의 "2주차 스펙 3건 초안"에 해당). 플레이북의 "스펙 하나 = PR 하나"를 그대로 적용할 수 없으므로 PR은 상위 작업 단위로 냅니다(Overview의 "PR 단위", 루트 `CLAUDE.md`). PR 경계는 설계 문서의 클래스 수로 어림해 정한 것이라, 실제로 해 보고 맞지 않으면 이 문서의 경계를 고칩니다.
- **데모 최소 경로와 밀리면 미룰 것**: 금요일 데모(실제 PR 1건, 규칙 근거 지적 1건 이상)에 꼭 필요한 것은 1.1, 1.2, 2.1~2.3, 3.1, 3.3, 5.1~5.3, 6.1, 6.3, 7.1, 7.2, 7.4, 8.1, 8.2, 8.4, 8.11, 10.1, 10.2, 10.4, 11.1(단일 모드), 11.3, 11.5, 11.6, 11.8, 12.1, 13.1, 13.2, 17.1, 17.3, 18.1~18.3, 18.5, 23.2, 20.4입니다. 일정이 밀리면 아래 순서로 미룹니다(ROADMAP "일정 위험과 완충": FR-6 분할을 먼저 줄임).
  1. `*` 표시 작업 전부(속성 테스트와 보조 테스트)
  2. FR-6 분할: 11.1의 분할 계획과 12.1의 `chunk_failed`·누적 비용 경고. 미루는 동안 Size_Limit 초과 PR은 요약 전용 모드로 처리
  3. 8.6 `@path` import, 8.9 스펙 링크의 head SHA fallback
  4. 16.2 `ConfigPrinter`, 18.7의 sh 래퍼(데모는 `java -jar`로 실행), 21의 macOS·Windows 매트릭스
  5. 20.1의 인젝션 픽스처 4종과 20.2(요구사항 14.7, 17.10이 요구하므로 미루면 M1 뒤에 반드시 채움)

## Task Dependency Graph

wave 0~1의 1.1, 2.1, 2.2, 2.3이 월요일 선머지입니다(2.3은 2.1의 타입을 쓰므로 2.1 다음에 작성). wave 2부터 세 트랙과 리드의 작업 3이 병렬로 진행됩니다. 같은 wave 안에서도 같은 사람이 맡은 작업은 번호 순서대로 합니다.

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["1.2", "2.1", "2.2", "2.3", "23.1"] },
    { "id": 2, "tasks": ["2.4", "2.5", "2.6", "3.1", "3.3", "5.1", "5.3", "6.1", "7.2", "8.1", "10.1", "10.2", "11.1", "11.3", "11.6", "13.1", "15.1", "17.3", "18.1", "21"] },
    { "id": 3, "tasks": ["3.2", "3.4", "5.2", "6.2", "6.3", "7.1", "7.3", "7.4", "8.2", "8.4", "8.6", "8.9", "10.3", "10.4", "11.2", "11.4", "11.7", "13.2", "13.3", "15.2", "16.1", "16.2", "17.1", "17.4", "23.2"] },
    { "id": 4, "tasks": ["4", "5.4", "5.5", "5.6", "5.7", "5.8", "6.4", "6.5", "6.6", "7.5", "7.6", "7.7", "8.3", "8.5", "8.7", "8.8", "8.10", "10.5", "11.5", "11.8", "13.4", "16.3", "17.2", "18.2", "18.4", "20.1"] },
    { "id": 5, "tasks": ["8.11", "11.9", "12.1", "20.3"] },
    { "id": 6, "tasks": ["8.12", "12.2", "18.3", "20.2"] },
    { "id": 7, "tasks": ["9", "14", "18.5", "18.7"] },
    { "id": 8, "tasks": ["18.6", "20.4"] },
    { "id": 9, "tasks": ["19"] },
    { "id": 10, "tasks": ["22"] }
  ]
}
```
