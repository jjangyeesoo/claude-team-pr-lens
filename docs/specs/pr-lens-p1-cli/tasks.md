# Implementation Plan: PR Lens P1 CLI

## Overview

[design.md](design.md)를 구현하는 작업 목록입니다. 순서는 "공유 경계 선머지(리드 B, 월요일) → T2·T1·T3 병렬 → 통합·CI"입니다. 월요일 선머지는 작업 1.1과 2.1~2.3(패키지 뼈대, 공유 타입, 경계 인터페이스)까지입니다(ROADMAP: 인터페이스 타입만 먼저 머지). 이 넷은 새 의존성 없이 JDK만으로 만들 수 있어 ADR 결정을 기다리지 않습니다. 각 트랙은 이 넷이 머지되면 시작하고, 리드는 1.2(의존성과 ArchUnit), 2.4~2.6, 작업 3(재시도, 마스킹)을 트랙과 병렬로 진행합니다. 트랙 안에서는 순수 함수 → 어댑터 → 조립 순서로 진행합니다.

- 언어와 도구: Java 17, Gradle Kotlin DSL, JUnit Jupiter 6(Spring Boot 4.1 관리 버전), jqwik(속성 기반 테스트, JUnit 6.0.3에서 1.10.1 동작 확인), ArchUnit, picocli(ADR 0004), Anthropic Java SDK
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

- [x] 1. 프로젝트 골격과 의존성 준비
  - PR 경계: 1.1 / 1.2 (1.1은 월요일 선머지. 패키지 이름 변경이 크면 그 부분만 먼저 따로 냄)
  - [x] 1.1 패키지 이름 변경, 메모 샘플 제거, 패키지 뼈대 (월요일 선머지. 새 의존성 없음)
    - 패키지를 `com.example.starter`에서 `com.prlens`로 바꿉니다(ROADMAP 1주차 항목. 이미 돼 있으면 건너뜀). `build.gradle.kts`의 group, `settings.gradle.kts`의 프로젝트 이름, `application.properties`를 함께 바꿉니다. 변경 줄 수가 많으면 이 부분만 별도 PR로 먼저 냅니다
    - backend의 메모 샘플(`memo` 패키지와 그 테스트)을 지웁니다(ROADMAP: 2주차 첫 기능 PR에서 제거). `common/error/ApiExceptionHandler`가 `MemoNotFoundException`을 import하므로 그 처리 메서드도 함께 지웁니다. `common`의 나머지는 P2 스펙에서 위치를 정할 때까지 둡니다(ADR 0003)
    - web의 메모 화면(`web/src/app/memos`, `web/src/lib/api/memos.ts`)은 4주차까지 그대로 둡니다(ROADMAP 4주차 T1). backend에서 메모 API가 사라지지만 이 PR에서 `web/`은 고치지 않습니다("API가 바뀌면 `types.ts`도 같은 PR" 규칙의 예외)
    - `backend/src/main/java/com/prlens/` 아래 패키지 뼈대 생성(ADR 0003): `model`, `support`, `github`, `pullrequest`, `diff`, `glob`, `filter`, `context`, `llm`, `review`, `codec`, `output`, `config`, `cli`
    - `src/test/java/com/prlens/testkit/` 패키지 생성
    - _Requirements: 17.3_
  - [x] 1.2 의존성 추가와 아키텍처 규칙 (선머지 대상 아님. 리드가 진행. ADR 0004와 ADR 0005는 확정)
    - `backend/build.gradle.kts`에 picocli, jqwik, ArchUnit, Anthropic Java SDK를 버전 고정으로 추가 (PR 설명에 추가 이유 기록). 의존성 추가는 사람의 승인이 필요합니다. YAML 파서(snakeyaml)와 Jackson 3은 Spring Boot가 이미 가져오므로 추가하지 않습니다. 임시 프로젝트에서 함께 동작을 확인한 버전은 jqwik 1.10.1, archunit-junit5 1.5.1, picocli 4.7.7, anthropic-java 2.68.0입니다
    - ArchUnit 규칙 작성: `model`은 JDK 외 의존 금지, `review`는 `cli`·`output`·`github`·`config` 의존 금지, `review`는 `AnthropicLlmClient` 참조 금지, `context`·`pullrequest`는 `HttpGitHubClient` 참조 금지, Spring 애너테이션은 `cli` 조립 코드에만(스타터의 `common`과 Application 클래스는 예외로 둠), picocli 타입은 `cli`에서만(ADR 0004), `println` 사용 금지, `com.fasterxml.jackson.databind` 사용 금지(Jackson 3 `tools.jackson`만 사용)
    - 대상 클래스가 아직 없는 규칙은 `allowEmptyShould(true)`로 둡니다(ArchUnit은 기본값에서 대상이 없는 규칙을 실패 처리)
    - `%n` 금지는 ArchUnit이 문자열 리터럴을 보지 못하므로 소스 파일을 읽는 테스트로 검사합니다
    - _Requirements: 17.3, 22.5_

- [x] 2. 공유 타입과 경계 인터페이스 정의
  - PR 경계: 2.1~2.3 / 2.4~2.6 (앞쪽은 월요일 선머지)
  - [x] 2.1 `model` 패키지 레코드 작성
    - `RepoRef`, `PullRequestSnapshot`, `ChangedFile`, `FileStatus`, `PatchContent`(sealed: `Parsed`, `Absent`, `Unparseable`), `Hunk`, `DiffLine`, `LineKind`
    - `ReviewContext`(경로 중복 시 생성 거부), `ContextFile`, `ContextSource`, `Revision`
    - `ReviewResult`, `ExcludedFile`, `ResultStatus`, `IncompleteReason`, `IncompleteDetails`, `ChunkIssue`(원본 응답 발췌 포함), `FileCountGap`, `ReviewStats`, `ReviewMode`, `Finding`, `Basis`, `Demotion`, `Severity`, `Category`, `BasisType`, `LineVerdict`, `SummaryOnlyReason`, `Usage`
    - `Configuration`(`defaults()` 포함), `ModelPricing`
    - compact constructor 규칙: `requireNonNull(x, "필드이름")`, `List.copyOf`, 맵은 빈 `TreeMap`에 옮겨 담아 `unmodifiableSortedMap`(키 자연 순서, null 키·값 거부), `BigDecimal` 정규화(비용 `setScale(4, HALF_UP)`(`Usage.estimatedCostUsd`, `Configuration.maxCostUsdPerReview`), 단가 `stripTrailingZeros`), 토큰 수 음수 거부
    - 불변식 검사: `status == COMPLETE ⇔ incompleteReasons 비어 있음`, `verdict == SUMMARY_ONLY ⇔ summaryOnlyReason != null`, `excludedFiles == excludedFileDetails의 path 목록`, `PullRequestSnapshot.body` null → `""`
    - _Requirements: 1.2, 17.1, 17.2, 17.4, 17.5, 17.6, 17.7, 17.8_
  - [x] 2.2 `RepoPaths.normalize` 구현 (`\` → `/`, 앞의 `./`·`/` 반복 제거, `..`은 해석하지 않음)
    - 여러 패키지가 쓰므로 `model`에 둡니다(design.md "패키지 배치" 표의 `model` 행)
    - _Requirements: 4.8, 11.1, 22.2, 22.6_
  - [x] 2.3 경계 인터페이스 작성 (시그니처는 design.md "공유 경계"의 코드가 기준)
    - `github`: `GitHubCredentials`, `GitHubClient`(`getPullRequest`, `listFiles`, `getTree`, `getFile`), `PullRequestMeta`, `FilePage`, `RepoTree`, `FileFetch`(`Found`/`NotFound`/`IsDirectory`/`TooLarge`)
    - `pullrequest`, `context`: `PrFetcher.fetch(RepoRef, int)`, `ContextCollector.collect(PullRequestSnapshot, Configuration)`의 생성자와 시그니처(본문은 `UnsupportedOperationException`)
    - `llm`: `LlmClient`, `LlmRequest`(모델, 최대 출력 토큰, effort, system, 캐시 블록, 데이터 블록, JSON 스키마), `LlmResponse`(stopReason, text, usage), `LlmUsage`, `LlmApiException`(상태 코드, 오류 종류, `retry-after` 원문)
    - `review`: `ReviewEngine` 생성자와 `review(snapshot, context, config)` 시그니처(본문은 `UnsupportedOperationException`), `ReviewListener`
    - `support`: `RetryListener`(대기 시간은 `Duration`), `Sleeper`, `Warning(code, message)`, `WarningSink`, `PrLensException`(sealed가 아닌 추상 클래스), `Attempt<T>`(`Success`/`Retryable`/`Fatal`)와 `RetryExecutor.execute(api, call)` 시그니처
    - T2 순수 함수의 스텁(본문은 `UnsupportedOperationException`): `GlobMatcher.matches`/`validate`, `DiffPrinter.print`, `LineRanges.of`/`contains`, `DiffFilter.apply`, `FilterOutcome(targets, excluded, warnings)`. `GlobMatcher.validate`가 던지는 `GlobSyntaxException(pattern, reason)`(`PrLensException` 하위 타입)도 여기서 만듭니다(T3 `ConfigLoader`가 6.1을 기다리지 않고 잡을 수 있게). T1과 T3가 구현을 기다리지 않게 하려는 것이고, 구현은 5.2, 5.3, 6.1, 6.3이 채웁니다
    - 생성자 시그니처만 먼저 고정: `RetryExecutor(RetryPolicy, Sleeper, RetryListener)`, `HttpGitHubClient(HttpClient, GitHubCredentials, RetryExecutor)`, `RetryingLlmClient(LlmClient, RetryExecutor)` (P2가 작업마다 다시 조립)
    - _Requirements: 17.2, 17.3, 17.9_
  - [x]* 2.4 Property 43: 공유 타입 불변성과 값 동등성 테스트
    - **Validates: Requirements 17.4, 17.5, 17.6, 17.8**
  - [x]* 2.5 Property 22: 경로 정규화 비교 테스트
    - 주의(선머지 리뷰): 생성기의 기준 경로 p는 이미 정규화된 값으로 만듭니다(`./`·`/`로 시작하거나 `\`를 포함하면 "정규화 후 p와 같다"가 성립하지 않음). 대소문자 변형은 p에 대소문자 있는 글자가 하나 이상 있을 때만 p와 다릅니다. `RepoPaths.normalize`가 정한 동작(중간의 `./`·`//`와 끝의 `/` 유지, 공백 유지, 빈 문자열 가능)은 그 Javadoc과 `RepoPathsTest`에 있습니다
    - **Validates: Requirements 11.1, 22.2, 22.6**
  - [x] 2.6 `testkit` 공용 생성기 기본형 작성 (jqwik이 필요하므로 1.2 뒤)
    - 저장소 경로(대소문자, `./`, `\` 변형), `ReviewResult`(한글·따옴표·역슬래시·줄바꿈·탭·이모지 문자열, null 선택 필드, 빈 목록), `Configuration`
    - 트랙별 생성기(Hunk, glob, import 그래프, 응답 수열)는 각 트랙이 추가합니다
    - 주의(선머지 리뷰): 저장소 경로 생성기는 정규화된 기준 경로와 그 변형(`./`, `/`, `\`, 대소문자)을 짝으로 내야 2.5에서 그대로 쓸 수 있습니다. `ReviewResult`와 `Configuration` 생성기는 2.1의 불변식(`status`와 `incompleteReasons`, `verdict`와 `summaryOnlyReason`, `excludedFiles`와 `excludedFileDetails`, 토큰 수 0 이상)을 지켜야 생성자에서 거부되지 않습니다
    - _Requirements: 17.8_

- [x] 3. `support`: 재시도와 비밀정보 마스킹 (선머지 대상이 아님. 2.3이 머지되면 트랙과 병렬로 진행)
  - [x] 3.1 `RetryPolicy`, `RetryExecutor` 구현
    - 첫 시도 + 재시도 n회(0~10), 대기 = 호출자가 정한 값(GitHub rate limit) 또는 유효한 `retry-after` 또는 `min(2^(k-1), 30)`초. 대기가 60초를 넘으면 남은 시도 횟수와 관계없이 즉시 `RetryAfterTooLongException`(이 검사를 "마지막 시도" 검사보다 먼저 합니다)
    - 재시도 대상: 429, 5xx, 연결 실패, 제한 시간 초과, 그리고 호출자가 재시도 대상으로 분류한 응답(GitHub rate limit 403/429, 대기 시간은 호출자가 함께 전달)
    - `execute`는 호출자가 돌려준 `Attempt`로 판단: `Success`면 값 반환, `Fatal`이면 `NonRetryableApiException`, `Retryable`이면 대기 후 재시도. 호출자가 던진 예외(`GitHubApiException` 등)는 그대로 통과
    - 예외(모두 `support`에 정의, `PrLensException` 하위 타입, final 아님): `RetriesExhaustedException(api, lastStatus, attempts)`, `RetryAfterTooLongException(api, seconds)`, `NonRetryableApiException(api, status)`
    - HTTP 날짜·음수·소수 `retry-after`는 무효로 보고 지수 백오프 사용
    - 재시도마다 `RetryListener.onRetry(api, nextAttempt, maxAttempts, status, wait)` 호출(`wait`는 정책이 계산한 `Duration`)
    - 주의(선머지 리뷰): 호출자가 정한 대기 시간은 `Attempt.Retryable.delay()`로 읽습니다(record 구성 요소 이름으로 `wait`를 쓸 수 없어 바꿈). 2.3의 `RetryPolicy(int maxRetries)`는 생성자 시그니처를 고정하려고 둔 최소 형태라 구성 요소를 더해도 됩니다
    - 결정(3.1): `maxRetries` 0~10 검증은 `RetryPolicy`에서도 합니다(범위 밖이면 `IllegalArgumentException`). 음수면 시도 횟수가 0 이하가 되어 동작이 정의되지 않고, P2는 `ConfigLoader`를 거치지 않고 조립할 수 있기 때문입니다. 사용자에게 보이는 오류 수집은 `ConfigLoader`(요구사항 18.6)가 맡습니다
    - _Requirements: 1.13, 21.1, 21.3, 21.4, 21.5, 21.6, 21.7, 21.8, 21.9, 21.10, 21.11_
  - [x]* 3.2 Property 42: 재시도 정책 테스트 (가짜 `Sleeper`)
    - **Validates: Requirements 21.1, 21.3, 21.4, 21.5, 21.8, 21.9, 21.10, 21.11**
  - [x] 3.3 `SecretMasker`, `MaskingPrintStream` 구현
    - 비밀값 모든 출현 위치(겹침 포함)를 덮고 연속 구간마다 `***` 하나로 치환, 빈 값·공백 값 제외
    - `MaskingPrintStream`: `FileDescriptor.out/err` 기반, BOM 없는 UTF-8, 줄 단위 버퍼
    - 헤더 마스킹 함수: `Authorization`, `x-api-key` 이름 대소문자 무시
    - _Requirements: 19.4, 19.5, 19.7, 22.3_
  - [x]* 3.4 Property 39: 비밀정보 치환 테스트 (`*` 없는 비밀값으로 한정, 설계 G-3)
    - **Validates: Requirements 19.4, 19.5, 19.7**

- [x] 4. Checkpoint: 공유 부분(작업 1~3) 완료 확인
  - `./gradlew test`와 ArchUnit 규칙이 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다. 트랙 시작 조건은 이 Checkpoint가 아니라 작업 1, 2.1~2.3의 머지입니다.

### T2 GitHub 연동 (A)

- [x] 5. diff 해석과 출력
  - [x] 5.1 `DiffParser` 구현
    - CRLF·단독 CR을 LF로 정규화, 끝 LF 하나 제거 후 줄 분할, 빈 문자열은 빈 목록
    - 상태 기계(`EXPECT_HEADER` → `IN_HUNK`), 헤더 정규식, 생략된 줄 수 = 1, `sectionHeading` 원문 보존, `\` 줄은 직전 줄 `noNewlineAtEnd`
    - 오류: `DiffParseException(path, lineNo, hunkIndex, kind)` — 헤더 문법, 줄 수 불일치, 잘못된 접두어, 첫 헤더 앞 내용, 겹침·비증가. 부분 결과 없음
    - 예시 테스트: 줄 수를 생략한 헤더(`@@ -3 +3 @@`)는 줄 수 1로 해석(요구사항 2.2. `DiffPrinter`가 생략형을 출력하지 않아 round-trip 속성으로는 검증되지 않음)
    - _Requirements: 2.1, 2.2, 2.5, 2.6, 2.7, 22.4_
  - [x] 5.2 `DiffPrinter` 구현 (`@@ -a,b +c,d @@` + `sectionHeading`, LF, `\ No newline at end of file`) (T1이 사용, 화요일 목표)
    - _Requirements: 2.8_
  - [x] 5.3 `LineRanges.of(hunks)` 구현 (head 줄 수 ≥ 1 구간만, 정렬·병합, 이진 탐색 `contains`) (T1이 사용, 화요일 목표)
    - _Requirements: 2.3, 2.4_
  - [x]* 5.4 Property 3: diff round-trip 테스트
    - 주의(2.4~2.6 리뷰): `testkit`에 이미 있는 Hunk 생성기는 `PullRequestSnapshotArbitraries` 안의 private이고 diff로 유효하지 않습니다(줄 번호·줄 수·줄 목록이 서로 맞지 않고 줄 내용에 줄바꿈이 들어감). 유효 구간을 보장하는 공용 Hunk 생성기는 여기서 새로 만듭니다
    - **Validates: Requirements 2.1, 2.5, 2.8, 2.9**
  - [x]* 5.5 Property 4: 추가·삭제 줄 수 보존 테스트
    - **Validates: Requirements 2.10**
  - [x]* 5.6 Property 5: Changed_Line_Range 모델 일치 테스트
    - **Validates: Requirements 2.3, 2.4**
  - [x]* 5.7 Property 6: 잘못된 patch 거부 테스트
    - **Validates: Requirements 2.7**
  - [x]* 5.8 Property 7(diff 부분): 줄바꿈 무관 해석 테스트
    - **Validates: Requirements 22.4**

- [x] 6. glob과 diff 필터
  - [x] 6.1 `GlobMatcher` 구현 (T3가 사용, 화요일 목표. `GlobSyntaxException(pattern, reason)` 타입은 2.3에서 만들어 두었으므로 여기서는 던지는 조건을 채움)
    - `PathMatcher` 미사용. `/` 구간 분할, `**` 구간 = 0개 이상 구간, `*`·`?` 구간 정규식, 메모이제이션 DP
    - `{a,b}`(중첩)는 구간 분할 전에 모두 펼친 뒤(256개 상한) 펼친 패턴마다 접두어 제거와 컴파일
    - 문법 오류: 짝 없는 중괄호, 빈 패턴, `\` 포함, 구간 일부의 `**`, 펼친 개수 256 초과, 중괄호 중첩 32단계 초과
    - 예시 테스트: `**/x`가 루트 `x`와 일치, 대소문자 구분
    - 주의(선머지 리뷰): 패턴에는 `RepoPaths.normalize`를 그대로 쓰지 않습니다. `\`를 `/`로 바꾸므로 `a\b`가 문법 오류 없이 통과합니다. 패턴은 `\` 검사를 먼저 하거나 앞의 `./`·`/` 제거만 따로 합니다(경로 쪽에는 `normalize`를 써도 됨). `/`, `./` 패턴은 접두어를 떼면 `""`가 되므로 빈 패턴 검사는 접두어 제거 뒤에 합니다
    - _Requirements: 3.2, 5.7_
  - [x]* 6.2 Property 12: glob 중괄호 모델 기반 테스트
    - **Validates: Requirements 5.8**
  - [x] 6.3 `DiffFilter` 구현 (T1이 사용, 화요일 목표)
    - 기본 패턴 목록(요구사항 3.1 순서), 합성 = 기본 − (해제 ∩ 비밀정보 아닌 기본) + 추가
    - 비밀정보 패턴 해제 요청은 무시하고 경고를 `FilterOutcome`에 담아 반환. 제외 파일은 경로와 사유(`ExcludedFile`)로 반환
    - 판정 순서: 새 경로·이전 경로 패턴 일치(처음 일치 패턴) → `Absent`는 `binary_or_too_large` → `Unparseable`은 `unparseable_patch`(설계 G-1) → 대상. 입력 순서 유지
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.8, 3.9, 3.10_
  - [x]* 6.4 Property 8: 필터 분할 불변과 사유 결정 테스트
    - 주의(2.4~2.6 리뷰): `PullRequestSnapshotArbitraries.snapshots()`는 공유 타입의 불변성 검사용이라 이 테스트의 입력으로 쓰지 않습니다(Hunk의 줄 번호·줄 수·줄 목록이 서로 맞지 않고, 경로가 중복되거나 `""`일 수 있고, `additions`/`deletions`가 Hunk와 무관). 플래너는 path·additions·deletions만 읽으므로 Hunk 없는 별도 생성기로 만듭니다(11.2에서 확인: 5.4의 Hunk 생성기는 필요 없음)
    - 주의(5 리뷰): 5.4의 공용 Hunk 생성기는 `testkit/DiffArbitraries.validHunks()`(0~5개)와 `nonEmptyValidHunks()`(1~5개)입니다. 0/0 Hunk는 만들지 않고 `noNewlineAtEnd`는 마지막 줄이 아닌 줄에도 붙습니다. `ChangedFile`의 `additions`/`deletions`는 Hunk의 `ADDED`/`REMOVED` 줄 수로 직접 계산해야 합니다
    - **Validates: Requirements 3.2, 3.3, 3.4, 3.5, 3.12**
  - [x]* 6.5 Property 9: 필터 멱등 테스트
    - **Validates: Requirements 3.13**
  - [x]* 6.6 Property 10: 제외 패턴 목록 합성 테스트
    - **Validates: Requirements 3.8, 3.9, 3.10**

- [ ] 7. GitHub 클라이언트와 PR 가져오기
  - PR 경계: 7.1 / 7.2~7.5 / 7.6~7.7
  - [x] 7.1 `HttpGitHubClient`, PAT용 `GitHubCredentials` 구현
    - 생성자 `HttpGitHubClient(HttpClient, GitHubCredentials, RetryExecutor)`. 요청 제한 시간 30초, 모든 요청을 `GitHubCredentials`로 인증(P1은 `GITHUB_TOKEN`)
    - `RetryExecutor` 적용. rate limit 분류(design.md "재시도 정책"의 표): 403/429에 `retry-after`가 있으면 그 값, 없고 `x-ratelimit-remaining == 0`이면 `x-ratelimit-reset`까지 남은 시간, 둘 다 아니고 본문이 secondary rate limit을 알리면 60초를 `Attempt.Retryable`의 대기 시간으로 전달. 그 밖의 403은 권한 부족
    - 401, 404, rate limit이 아닌 403은 `HttpGitHubClient`가 직접 `GitHubApiException`(상태 코드와 원인 후보 메시지. final로 두지 않음: P2가 `GitHubAuthException`으로 상속)으로 던짐. 그 밖의 429 외 4xx는 `Attempt.Fatal`로 돌려줌
    - contents API(base64) 조회, 404 → `NotFound`, 폴더 → `IsDirectory`. 재귀 트리 조회와 `truncated` 전달. 1MB 초과 파일(`encoding: "none"`, 빈 `content`)은 빈 문자열로 넘기지 말고 `FileFetch.TooLarge`로 돌려줌(설계 G-12)
    - HTTP 디버그 로그(기본 꺼짐)는 헤더 마스킹 함수를 거쳐 기록
    - 주의(선머지 리뷰): `getTree`는 `Optional` 없이 `RepoTree`를 돌려줍니다. 트리를 찾지 못하면(404) `getPullRequest`·`listFiles`처럼 `GitHubApiException`을 던지고, 404를 결과 값(`NotFound`)으로 돌려주는 것은 `getFile`뿐입니다. `FilePage.Entry`는 GitHub 값 그대로 채웁니다(상태 매핑과 patch 해석은 7.4)
    - 결정(7.1): 트리 항목의 `type`이 `blob`/`tree`/`commit` 외이면 그 항목을 버린다(경고 없음, 7.7에서 실제로 있는지 확인). contents API는 배열과 `submodule`을 `IsDirectory`, `symlink`와 그 밖의 `type`을 `NotFound`로 돌려준다
    - 2.3의 record는 `PullRequestMeta.title`·`baseSha`·`headSha`, `FilePage.Entry.filename`·`status`, `RepoTree.Entry.path`에 null을 거부합니다. GitHub가 이 값들을 항상 채워 준다는 가정이고 실제 응답으로 확인한 것은 아닙니다(7.7에서 확인)
    - 결정(7.1): `x-ratelimit-reset`까지 60초를 넘으면 `Retryable`을 돌려주지 않고 `HttpGitHubClient`가 `RateLimitResetException`(`RetryAfterTooLongException`의 하위 타입, UTC 시각 포함 메시지)을 직접 던진다
    - 주의(3 리뷰): `Attempt.Retryable`에 `delay`를 주면 실행기는 `retryAfter`를 보지 않고 `delay`만 씁니다. 음수 `delay`는 0초로 보고 바로 재시도하고, 60초를 1ms라도 넘으면 `RetryAfterTooLongException`입니다. 설계 표의 "`retry-after`가 reset보다 먼저"라는 우선순위는 `HttpGitHubClient`가 `delay`를 정할 때 직접 적용합니다
    - 결정(7.1): 숫자가 아닌 `retry-after`는 remaining/reset, secondary 판정을 먼저 거치고 둘 다 아니면 60초 `delay`로 재시도한다(지수 백오프를 쓰지 않음. GitHub 문서의 "최소 1분")
    - 주의(3 리뷰): 연결 실패와 제한 시간 초과를 예외로 던지면 실행기가 재시도 없이 그대로 통과시킵니다. 재시도하려면 `Attempt.Retryable`로 돌려줍니다. `NonRetryableApiException`의 api는 `execute(api, …)`의 인자이고 `Attempt.Fatal.api()`는 쓰이지 않으므로 두 값을 같게 넘깁니다
    - 주의(3 리뷰): 실행기는 `execute`의 `api`와 `Retryable.statusOrError`를 가공 없이 예외(`api()`, `lastStatus()`)와 `RetryListener`에 넘기고, 이 문자열이 요구사항 21.6·21.7의 메시지에 그대로 나옵니다. API 이름은 `GitHub`, HTTP 상태는 숫자만으로 된 문자열(`"503"`), 네트워크 오류는 숫자가 아닌 종류 이름으로 맞춥니다 (스펙을 읽고 추론한 항목)
    - 결정(7.1): `SecretMasker`는 오버로드 생성자 `HttpGitHubClient(…, Options)`의 `Options`로 받는다. 3인자 생성자는 빈 마스커와 디버그 로그 꺼짐이다. 참고: 값 전체를 가리는 이름은 `Authorization`과 `x-api-key`뿐이고 이 둘은 값이 null이어도 `***`를 돌려줍니다. 그 밖의 헤더는 값 안의 비밀값만 치환하며 값이 null이면 NPE입니다. 이름이 null이면 항상 NPE입니다
    - 결정(7.1): owner나 repo가 `.`·`..`이면 요청을 보내지 않고 `GitHubApiException(404)`를 던진다. `getFile`의 path에 `.`·`..`·빈 구간이 있으면 요청 없이 `NotFound`다. `PullRequestUrl`은 그대로 둔다
    - _Requirements: 1.8, 1.11, 1.13, 19.5, 19.6, 21.2, 21.9, 21.10, 21.11_
  - [x] 7.2 `PullRequestUrl` 구현 (설계의 정규식, 번호 `long` 파싱 후 2,147,483,647 이하 확인, `github.com`만 허용, `repo()`와 `number()` 제공)
    - _Requirements: 1.3, 1.4, 1.9_
  - [x]* 7.3 Property 1: PR URL 파싱 round-trip과 거부 테스트
    - **Validates: Requirements 1.3, 1.4, 1.9**
  - [x] 7.4 `PrFetcher` 구현 (`fetch(RepoRef, int)`, 경고는 `WarningSink`로)
    - 메타데이터 조회, 파일 목록 `per_page=100` 페이지 조회(누적 수 == `changed_files` 또는 빈 페이지/next 없음에서 중단), 같은 `filename` 버림
    - 상태 매핑: `copied` → `ADDED`, `unchanged` → `MODIFIED`(설계 G-6), 이름 변경은 이전 경로 기록
    - patch 없음 → `Absent`, `DiffParseException` → `Unparseable` + 경고
    - 주의(선머지 리뷰): `FilePage.Entry`는 GitHub 값 그대로(`status` 문자열, `patch`는 없으면 null)이고 다음 페이지 여부는 `FilePage.hasNext`입니다. `ChangedFile`은 경로 정규화를 강제하지 않으므로 `filename`을 `RepoPaths.normalize`에 거쳐 담습니다. `PullRequestMeta.body`는 null일 수 있고 `PullRequestSnapshot` 생성자가 `""`로 바꿉니다
    - 결정(7.4): `patch == null`만 `Absent`이고 `""`·`"\n"`은 `DiffParser.parse`를 거쳐 Hunk 없는 `Parsed`가 됩니다(경고 없음). `Absent`는 DiffFilter에서 사유 `binary_or_too_large`가 되는데 빈 patch는 바이너리도 큰 파일도 아니어서 사유가 틀리고, 줄 수 0인 대상 파일은 G-2(단일 모드 1회 호출)가 이미 처리하기 때문입니다
    - 주의(5 리뷰): `DiffParseException`을 `PatchContent.Unparseable`로 옮길 때는 `kind().name().toLowerCase(Locale.ROOT)`, `lineNo()`(1부터, 줄바꿈 정규화 뒤 기준), `hunkIndex()`(0부터)를 넘깁니다. `name()`이나 `toString()`을 그대로 넘겨도 컴파일되므로 주의합니다
    - _Requirements: 1.1, 1.2, 1.5, 1.6, 1.7, 1.12, 1.14_
  - [x]* 7.5 Property 2: 파일 목록 조회의 순서·중복·개수 테스트 (절단 판정은 엔진이 하므로 12.2의 Property 44)
    - **Validates: Requirements 1.7**
  - [x]* 7.6 GitHub 가짜 서버 통합 테스트
    - 401/403/404 메시지, rate limit 응답 세 종류(`retry-after`, remaining 0 + reset, secondary)의 분류와 대기 시간, 60초 초과 시 즉시 종료, 짧게 주입한 타임아웃, `Authorization` 헤더, base/head ref 선택, 트리 `truncated` 대체 경로
    - 결정(7.6): `Options`에 제한 시간을 더하지 않고 테스트 전용 `ShortTimeoutHttpClient`가 원래 요청의 30초를 기록한 뒤 짧은 값으로 바꿔 보냅니다. `Options`는 design "공유 경계"의 public record라 구성 요소를 더하면 설계와 P2 조립 코드가 바뀌고, 30초 지정과 시간 초과의 재시도 분류는 테스트 쪽 주입으로 모두 확인되기 때문입니다. 기준 주소의 뒤 `/` 자르기는 가짜 서버 주소(뒤 `/` 포함)로 확인합니다
    - _Requirements: 1.8, 1.11, 1.13, 21.2, 21.9, 21.10, 21.11_

  - [x] 7.7 실제 GitHub 스모크 (수요일까지. `GITHUB_TOKEN`이 있을 때만 도는 수동 실행 테스트)
    - 대상: 고정 픽스처 PR [#15](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/15) (구성과 확인한 사실은 [spike 2026-10-14](../../spikes/2026-10-14-fixture-pr.md)). 내용이 같은 이름 변경도 patch가 없음
    - 팀 저장소의 실제 PR 1건을 `PrFetcher`로 가져와 파일 수, patch 해석, `changed_files`와 받은 파일 수가 같은지 확인
    - base SHA로 재귀 트리 조회와 contents 조회가 되는지 확인
    - 2.3과 7.1이 가정한 것을 실제 응답으로 확인: 없는 SHA의 트리 조회가 주는 상태 코드(404로 가정), PR 메타데이터의 `title`·base/head SHA와 파일 항목의 `filename`·`status`, 트리 항목의 `path`가 항상 채워져 오는지, 트리 항목 `type`에 `blob`/`tree`/`commit` 외의 값이 있는지
    - 결과를 PR 설명이나 `docs/spikes/`에 기록. CI에서는 실행하지 않음(요구사항 22.7)
    - 주의(7.1 리뷰): 7.1은 다음을 가정으로 구현했습니다. secondary rate limit은 본문의 `secondary rate limit`(대소문자 무시)으로 판별합니다. 트리 항목 `type`이 `blob`/`tree`/`commit` 밖이면 버립니다. contents API의 symlink는 `NotFound`, submodule은 `IsDirectory`입니다. `getFile`은 `Accept: application/vnd.github.object+json`으로 요청하고, 다음 두 가지를 가정합니다. 폴더는 `{"type":"dir","entries":[...]}` 객체로 옵니다. 1MB를 넘는 파일은 `content`가 비고 `encoding: "none"`인 200 응답으로 와서 `TooLarge`가 됩니다. 이것들을 실제 응답으로 확인합니다
    - 결정(7.7): 실행 조건은 `GITHUB_TOKEN`과 `PRLENS_SMOKE=1` 둘 다(Stop hook이 실제 GitHub를 부르지 않게)
    - 결과(7.7, 2026-10-21): 가정 대부분 확인됨. 다른 것은 없는 SHA의 트리 조회가 404가 아니라 **422**라는 점이고 `getTree`의 분류를 따로 정해야 합니다(`GitHubSmokeTest.treeOfUnknownShaThrowsNotFound`가 스모크 실행 시 실패로 남음). 1MB 초과·symlink·submodule은 픽스처 base에 대상이 없어 확인 못 함. 상세는 spike 2026-10-14 문서
    - _Requirements: 1.1, 1.5, 1.7, 1.14, 4.1_

- [ ] 8. 컨텍스트 수집
  - PR 경계: 8.1~8.5 / 8.6~8.8 / 8.9~8.12
  - [x] 8.1 `RepoTreeIndex` 구현 (base SHA 재귀 트리 → 경로별 blob/tree, `truncated`이면 경로별 조회로 대체하고 rule 수집은 경고 후 건너뜀, 설계 G-9)
    - _Requirements: 4.1, 4.9_
  - [x] 8.2 `CLAUDE.md` 수집 구현
    - 루트 `CLAUDE.md`, `.claude/CLAUDE.md`(대소문자 정확히 일치), 대상 파일(이름 변경이면 두 경로) 상위 폴더를 가까운 순으로, 루트 제외, 중복 없음, 제외 파일 경로 미사용
    - 모두 base SHA 기준, 404는 경고 없이 건너뜀
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.9, 4.10_
  - [x]* 8.3 Property 13: 상위 폴더 CLAUDE.md 후보 테스트
    - **Validates: Requirements 4.4, 4.5, 4.6, 4.7**
  - [x] 8.4 `FrontmatterParser`와 rule 선택 구현
    - 첫 줄 `---`일 때만 다음 `---`까지 안전 YAML 로더로 해석, `paths` 문자열 → 한 개짜리 목록, CRLF 정규화
    - 오류 `YAML_ERROR`, `UNCLOSED`, `INVALID_PATHS`는 포함 + 경고
    - `.claude/rules/**` 중 소문자 `.md`, `paths` 없으면 포함, 있으면 대상 파일(이름 변경이면 이전 경로 또는 새 경로)과 일치할 때만 포함
    - 무효 glob 패턴은 그 패턴만 무시하고 경고, 모두 무효면 포함 + 경고(설계 G-4)
    - 주의(6 리뷰): `GlobMatcher.matches`는 무효 패턴이면 unchecked `GlobSyntaxException`을 던집니다. rule `paths`는 먼저 패턴마다 `GlobMatcher.validate`로 무효 패턴을 걸러 패턴당 경고 1건을 내고, 남은 패턴으로만 `matches`를 부르세요. 파일마다 `matches`를 try/catch하면 같은 경고가 대상 파일 수만큼 반복됩니다
    - 결정(8.4): rule `paths`는 대상 파일의 새 경로와, `status == RENAMED`일 때만 이전 경로로 맞춥니다. `copied`(`ADDED` + `previousPath`)의 복사 원본은 PR이 바꾸지 않은 파일이라 쓰지 않습니다(요구사항 5.4와 Property 14가 "이름 변경"만 말함. 8.2의 CLAUDE.md 탐색도 같음). 제외 판정에 `previousPath`를 보는 `DiffFilter`와 다릅니다
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 5.9, 5.10, 5.11, 22.4_
  - [x]* 8.5 Property 14(rule 선택)와 Property 7(프런트매터 부분) 테스트
    - **Validates: Requirements 5.4, 5.5, 5.6, 22.4**
  - [x] 8.6 `ImportResolver` 구현
    - 펜스 블록(닫히지 않으면 파일 끝까지)과 인라인 코드 건너뛰기, `(^|\s)@(\S+)` 인식
    - 선언 파일 폴더 기준 해석과 `.`/`..` 정규화, `~/`·`/`·드라이브 문자·루트 위 `..` 거부 + 경고
    - BFS `(path, depth, declaredBy)`, 깊이 1~4만 가져오고 5 이상은 경고, 이미 담긴 경로는 건너뜀, 404·폴더는 경고
    - `claude_md`와 `import` 출처에서만 import 탐색
    - 결정(8.6): 절대 경로 거부(`/`, `~/`, 드라이브 문자)를 구간 해석보다 먼저 하고 `RepoPaths.normalize`는 부르지 않습니다. "선언 폴더 + import 경로"를 구간 단위로 직접 해석합니다(`.`·빈 구간 버림, `..`은 앞 구간 제거, 지울 구간이 없으면 루트 밖). `@\foo`는 `\`를 `/`로 바꾼 뒤 판정하므로 루트 밖으로 거부하고 `a\b.md`는 `a/b.md`입니다(Windows에서 `\foo`가 드라이브 루트 기준 절대 경로이고 `normalize`가 `\`를 구분자로 보는 것과 맞춤). 해석 결과가 빈 문자열(`@.`, `@a/..`)이면 루트 폴더라 요청 없이 `import_is_directory`로 경고합니다
    - 결정(8.6): `BaseFileReader.fetch`가 `Found`·`Missing`·`Directory`·`TooLarge`를 나눠 돌려주고 `read`는 `Found`일 때만 내용을 돌려줍니다. 1MB 초과는 `fetch`의 `context_file_too_large` 경고 하나만 내고 import 경고는 더 내지 않습니다(G-12 제안값). 경고 코드는 `import_outside_repo`, `import_depth_exceeded`, `import_not_found`, `import_is_directory`이고 메시지에 선언 파일과 원문 경로를 넣습니다
    - 결정(8.6): BFS 판정 순서는 루트 밖 → 이미 담긴 경로(`collected` 포함, 경고 없음) → 깊이 5 이상 경고 → 이전에 실패한 경로(요청 없이 같은 경고) → 조회입니다. 같은 경로는 한 번만 요청하고, 한 파일 안의 같은 해석 경로는 첫 선언만 처리합니다. 인라인 코드는 한 줄 안에서만 짝짓고(줄을 넘는 스팬 없음) 펜스는 ``` 만 다룹니다(`~~~` 제외)
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 6.8, 6.9, 6.10_
  - [x]* 8.7 Property 15: import 도달 가능성 불변 테스트
    - **Validates: Requirements 6.1, 6.2, 6.3, 6.5, 6.6, 6.7, 6.8, 6.11**
  - [x]* 8.8 Property 16: import 인식 메타모픽 테스트
    - **Validates: Requirements 6.4, 6.10**
  - [x] 8.9 `SpecLinkExtractor`와 스펙 조회 구현
    - 상대 경로·같은 저장소 URL 두 정규식을 본문 순서로 병합, 앵커·쿼리 제거, 다른 저장소(대소문자 무시)·`..`·`docs/specs/` 밖은 경고 후 건너뜀
    - 중복 제거, 최대 20개(초과 개수 경고)
    - 조회: base → 404일 때만 head(`Revision.HEAD`), 둘 다 없거나 폴더면 경고
    - 주의(선머지 리뷰): `RepoPaths.normalize`는 `..`을 남기므로 `..` 구간 검사와 `docs/specs/` 접두어 검사는 여기서 구간 단위로 합니다. `docs/specs//a.md`와 `docs/specs/a.md`는 정규화 뒤에도 다른 문자열이라 중복 제거에서 다른 경로가 됩니다.
    - 결정(8.9): 빈 구간과 `.` 구간을 버려 `docs/specs/a.md`로 정리하고 같은 경로로 중복 제거합니다(8.6 `ImportResolver`와 같은 방식)
    - 주의(8.1~8.5 리뷰): base 조회는 `BaseFileReader.fetch`를 씁니다. `Missing`은 트리상 없음(요청 없음)과 404를 함께 뜻하므로 둘 다 head 재조회 대상이고, `Directory`는 폴더 경고 대상입니다. `TooLarge`는 `fetch`가 이미 `context_file_too_large` 경고를 냈으므로 head로 다시 조회하거나 경고를 또 내지 않습니다. `RepoTreeIndex`는 base 트리만 담으므로 head 조회는 이 색인과 `BaseFileReader`를 거치지 말고 `getFile(repo, path, headSha)`로 하세요(색인으로 거르면 base에 없는 파일이 요청 없이 `ABSENT`가 됩니다)
    - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7, 7.8_
  - [x]* 8.10 Property 17: 스펙 링크 추출 테스트
    - **Validates: Requirements 7.1, 7.3, 7.5, 7.6, 7.7**
  - [ ] 8.11 `ContextCollector` 조립
    - `DiffFilter`로 대상 파일 결정 → `claude_md` → `rule` → `import` → `spec` 순서로 누적기(`LinkedHashMap`)에 수집. 같은 경로가 다시 들어오면 우선순위가 더 높은 출처를 남김(수집 순서가 우선순위와 같아 실제로는 기존 출처가 남음)
    - 404 외 오류(401, 403, 재시도 소진)는 예외로 전파
    - 경고는 생성자로 받은 `WarningSink`에 `Warning(code, message)`로 전달. 순수 함수가 돌려준 경고도 여기로 넘김
    - 주의(선머지 리뷰): `ReviewContext`와 `ContextFile`은 경로 정규화를 강제하지 않고, `ReviewContext`의 중복 검사는 문자열 그대로 비교합니다(`./a`와 `a`를 다른 경로로 봄). 누적기에 넣기 전에 `RepoPaths.normalize`를 거쳐야 요구사항 4.8이 성립합니다. 불변화는 `new ReviewContext(files)`로 합니다(`of` 팩터리는 없음)
    - 주의(8.1~8.5 리뷰): `RepoTreeIndex.load(github, repo, baseSha)`를 한 번만 부르고 같은 색인을 `BaseFileReader`와 `RuleCollector`에 넘깁니다. `ClaudeMdCollector.collect`와 `RuleCollector.collect`에는 `DiffFilter.apply(...).targets()`만 넘깁니다. `RuleCollector`(선택 경고, `rules_skipped_tree_truncated`)와 `BaseFileReader`(`context_file_too_large`)는 경고를 생성자의 `WarningSink`로 이미 보내므로 조립 쪽에서 다시 넘기지 않습니다. `getTree` 예외는 `load`가 그대로 던집니다(요구사항 4.11)
    - 주의(8.6~8.8 리뷰): `ImportResolver.resolve(collected)`에는 `claude_md`뿐 아니라 이미 모은 `rule` 파일도 모두 넘깁니다. `resolve`는 `collected`의 경로만 문자열 그대로 비교해 건너뛰므로 rule을 빼면 rule 파일을 가리키는 import가 `IMPORT`로 다시 조회되고 그 안의 `@path`까지 따라가 요구사항 6.2·6.7을 어깁니다. 경로는 넘기기 전에 정규화합니다. 돌려받는 것은 새 `IMPORT`(`Revision.BASE`) 파일뿐이고 `import_*`·`context_file_too_large` 경고는 생성자의 `WarningSink`로 이미 나갔으므로 다시 넘기지 않습니다. `BaseFileReader`는 `claude_md`·rule 수집과 같은 인스턴스(같은 트리 색인)를 씁니다
    - 주의(7.6~7.7 리뷰): 7.6의 `BaseContextServerTest.claudeMdIsReadAtBaseShaNotHeadSha`는 base SHA를 테스트가 직접 `RepoTreeIndex.load`·`BaseFileReader`에 넘기므로, 조립 코드가 `snapshot.baseSha()`를 고르는지는 검증하지 않습니다. 8.11 테스트에서 base와 head SHA를 다르게 둔 스냅샷으로 트리·contents 요청이 모두 base SHA로 가는지 확인하세요 (design "GitHub 가짜 서버"의 base/head ref 선택)
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
    - 주의(선머지 리뷰): `Basis.ref`는 null일 수 있고 `RepoPaths.normalize(null)`은 `NullPointerException`이므로 null·빈 문자열·공백 검사(요구사항 11.4)를 `normalize` 호출 전에 합니다. `normalize`는 공백을 지우지 않으므로 앞뒤에 공백이 붙은 `ref`나 `file`은 일치하지 않습니다(요구사항 11.1의 "정확히 일치"에 맞는 동작)
    - 주의(11.1~11.4 리뷰): 응답의 `file`이 이스케이프된 경로(`<~prlens:`꼴)일 수 있습니다. 경로에 `<prlens:` 꼴이 있을 때만 해당하며, 비교 전에 `file`만 `DelimiterCodec.unescape`하는 것이 G-13 제안값입니다. 스펙 추론이라 구현하면서 확인합니다
    - 주의(15 리뷰): `ResultCodec.read`는 `inline_eligible`인데 `line`이 null인 Finding을 거부하지만 `write`는 막지 않습니다. 판정이 이 조합을 만들면 `--format json` 출력이 다시 읽히지 않고 20.2에서야 드러납니다
    - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7_
  - [ ]* 10.5 Property 23, 24, 25, 26 테스트 (basis 검증 불변, 멱등, 필드 보존, 라인 판정)
    - **Validates: Requirements 11.2, 11.3, 11.4, 11.5, 11.8, 11.9, 11.10, 12.1, 12.2, 12.3, 12.4, 12.5, 12.6, 12.7, 12.10, 12.11**

- [ ] 11. 청크 계획, 프롬프트, 비용, 병합
  - PR 경계: 11.1~11.4 / 11.5~11.9
  - [x] 11.1 모드 결정과 `ChunkPlanner` 구현
    - 모드: 대상 0개 `NO_TARGET`, count ≤ L 단일(count 0 포함, 설계 G-2), ≤ 3L 분할, 초과 요약 전용
    - 분할: head 경로 코드 포인트 정렬 후 결정적 순차 greedy, L 초과 파일은 단독 Chunk
    - _Requirements: 3.6, 13.1, 13.2, 13.3, 13.4, 13.9, 13.10_
  - [x]* 11.2 Property 28: 청크 계획 테스트
    - 주의(2.4~2.6 리뷰): `PullRequestSnapshotArbitraries.snapshots()`는 공유 타입의 불변성 검사용이라 이 테스트의 입력으로 쓰지 않습니다(Hunk의 줄 번호·줄 수·줄 목록이 서로 맞지 않고, 경로가 중복되거나 `""`일 수 있고, `additions`/`deletions`가 Hunk와 무관). 플래너는 path·additions·deletions만 읽으므로 Hunk 없는 별도 생성기로 만듭니다(11.2에서 확인: 5.4의 Hunk 생성기는 필요 없음)
    - **Validates: Requirements 13.2, 13.3, 13.4, 13.13, 13.14**
  - [x] 11.3 `DelimiterCodec` 구현 (`<~*/?prlens:` 바이트 스터핑 escape/unescape, 대소문자 무시)
    - _Requirements: 14.5, 14.8_
  - [x]* 11.4 Property 33: Delimiter 이스케이프 테스트
    - **Validates: Requirements 14.2, 14.5, 14.8**
  - [ ] 11.5 `PromptBuilder` 구현
    - system: 리뷰 지시문(`PROMPT_VERSION`), 출력 규칙, 인젝션 규칙, `<~` 이스케이프 설명. PR·diff 텍스트 없음
    - 출력 규칙에 "`basis.ref`는 `team_context` 파일 머리줄의 `path`를 글자 그대로, 근거 파일이 없으면 `general`과 null, `file`은 `file_path` 값 그대로"를 넣음(설계 "PromptBuilder와 요청 배치"). 빠지면 근거가 모두 `general`로 강등돼 M1 데모 조건을 못 채움
    - 첫 user 블록: `<prlens:team_context>` 안 Common_Context(출처 순서 → 경로 오름차순, 파일별 `path`/`source` 머리줄), 블록 끝 `cache_control`
    - 둘째 user 블록: 제목, 본문, head 스펙, Chunk 파일별 경로와 `DiffPrinter` 출력을 각각 Delimiter_Tag로 감싸고 이스케이프
    - 요약 전용: patch 대신 경로·상태·줄 수·Hunk 헤더, 같은 스키마 사용
    - 요청마다 모델과 effort 명시, 최대 출력 토큰 지정
    - 주의(11.1~11.4 리뷰): `DelimiterCodec`은 문자열 변환만 하고 영역을 감싸지 않습니다. 파일 경로, 제목, 본문, head 스펙, diff, 컨텍스트 파일 내용을 모두 `escape`한 뒤 태그로 감쌉니다. `team_context`의 파일 머리줄 `path`/`source` 값도 같습니다(경로에 태그 꼴이 있을 수 있음. 머리줄은 스펙 추론)
    - _Requirements: 3.7, 9.1, 14.1, 14.2, 14.3, 14.4, 14.6, 20.6, 20.7, 20.8, 20.9_
  - [ ] 11.6 `CostCalculator` 구현 (공개 순수 함수 `estimate(LlmUsage, ModelPricing)`, `BigDecimal` Σ(토큰 × 단가 / 1,000,000), `setScale(4, HALF_UP)`, 단가 없으면 `null`. P2가 같은 함수를 씀)
    - _Requirements: 20.2, 20.5_
  - [ ]* 11.7 Property 40: 추정 비용 계산 테스트
    - **Validates: Requirements 20.2**
  - [ ] 11.8 `ResultMerger` 구현
    - findings Chunk 순서 연결, Chunk가 둘 이상이면 summary에 `[n/N] 파일 목록` 머리줄, 토큰·비용 합(하나라도 `null`이면 `null`), Incomplete_Reason 첫 등장 순 중복 제거, `ChunkIssue` 기록(`schema_violation`이면 원본 앞 2,000자 포함, 단일·요약 전용 모드는 `chunkIndex` 1), `excludedFiles`와 `excludedFileDetails` 중복 제거
    - 주의(20.1 리뷰): 골든 픽스처 `split-three-chunks`의 summary는 Chunk마다 `[n/N] <경로>` 머리줄 다음 줄에 요약을 두고 Chunk 사이를 빈 줄 하나(`\n\n`)로 잇고 끝 줄바꿈은 없습니다. 파일이 여럿인 Chunk의 경로 구분자는 픽스처가 정하지 않았습니다. 이 형식은 스펙 추론이므로 다른 형식을 고르면 그 픽스처의 `expected-result.json`과 `expected.md`를 함께 고칩니다
    - 주의(20.1 리뷰): 분할 모드 `estimatedCostUsd`는 Chunk별로 `setScale(4, HALF_UP)`한 비용의 합입니다(픽스처는 0.0834 + 0.0482 + 0.0478 = 0.1794). 합친 토큰으로 다시 계산하면 0.1795가 되어 어긋납니다
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
    - 주의(선머지 리뷰): `RetriesExhaustedException`이 3.1에서 생기므로 3.1이 머지된 뒤 시작합니다. `ReviewResult`는 `usage`, `incompleteDetails`, `stats`를 포함해 모든 필드가 필수라, 대상 0개일 때도 `Usage(0, 0, 0, 0, model, …)`와 `IncompleteDetails(List.of(), null)`을 만들어야 합니다
    - 미정(여기서 정함): 대상 0개일 때 `estimatedCostUsd`를 `0.0000`으로 할지 `null`로 할지. `null`이면 CLI가 "단가 없음" 경고(요구사항 20.5)를 잘못 냅니다
    - 주의(11.1~11.4 리뷰): SINGLE에서는 `ChunkPlanner.plan`을 부르지 않고 대상 전체(원래 순서)를 Chunk 하나로 씁니다. `plan`은 모드와 무관하게 경로순으로 정렬해 묶으므로 SPLIT에서만 부릅니다. 모드는 `ChunkPlanner.decideMode(targets, sizeLimit)`, 줄 수는 `changedLineCount(targets)`이고 둘 다 정적 메서드입니다. SINGLE에서 diff 순서가 경로순이 아니어도 되는지는 여기서 정합니다(데이터 영역만 달라지고 캐시 앞부분은 영향 없음)
    - _Requirements: 1.14, 3.7, 9.1, 13.1, 13.5, 13.6, 13.7, 13.8, 13.9, 13.11, 13.12, 20.1, 20.4_
  - [ ]* 12.2 가짜 `LlmClient`로 엔진 속성 테스트
    - Property 27(리뷰 모드와 API 호출 수), Property 11(제외 파일 내용 미전송), Property 34(프롬프트 배치와 캐시 앞부분 동일성), Property 41(누적 비용 경고 1회), Property 44(파일 수 부족 판정)
    - 주의(2.4~2.6 리뷰): `PullRequestSnapshotArbitraries.snapshots()`는 공유 타입의 불변성 검사용이라 이 테스트의 입력으로 쓰지 않습니다(Hunk의 줄 번호·줄 수·줄 목록이 서로 맞지 않고, 경로가 중복되거나 `""`일 수 있고, `additions`/`deletions`가 Hunk와 무관). 플래너는 path·additions·deletions만 읽으므로 Hunk 없는 별도 생성기로 만듭니다(11.2에서 확인: 5.4의 Hunk 생성기는 필요 없음). 또 `snapshots()`의 `reportedChangedFiles`(0~3,500)는 `files` 크기(0~4)와 무관해 거의 항상 더 큽니다. Property 44의 "크지 않을 때는 기록되지 않는다" 쪽을 보려면 `files.size()` 근처 값(같음, 1 작음, 1 큼)을 직접 섞습니다
    - **Validates: Requirements 1.14, 3.6, 3.7, 9.1, 13.1, 13.9, 13.10, 13.11, 14.3, 20.4, 20.6, 20.7**

- [ ] 13. Claude API 어댑터
  - [ ] 13.1 `AnthropicLlmClient` 구현 (1주차 spike 결과 반영)
    - SDK 자체 재시도 끄기, 요청 제한 시간 180초, `ANTHROPIC_API_KEY`는 이 클라이언트에만 전달
    - 요청 매핑: 모델, `max_tokens`, effort, system, user 블록 2개(첫 블록 ephemeral `cache_control`), `output_config.format`
    - 응답 매핑: `stop_reason`, 첫 text 블록, usage 네 토큰(thinking 포함 출력 토큰). HTTP 오류와 네트워크 오류는 `LlmApiException`(상태 코드, 오류 종류, `retry-after`)으로 던짐
    - 주의(선머지 리뷰): 클래스 이름은 `AnthropicLlmClient`로 둡니다. `ArchitectureTest`의 "`review`는 이 클래스를 참조하지 않는다" 규칙이 이름 문자열로 대상을 찾으므로, 이름이 다르면 규칙이 조용히 무력해집니다. 2.3의 `LlmRequest`·`LlmResponse`·`LlmUsage`에는 null 검사가 없습니다. `stopReason`이나 `text`가 없는 응답을 어떻게 다룰지는 여기서 정합니다. `LlmApiException` 생성자는 `(message, status, errorKind, retryAfter, cause)`이고 필요하면 오버로드를 더합니다
    - 결정(7.1 리뷰): HTTP 디버그 로그의 헤더는 인스턴스 메서드 `SecretMasker.maskHeader(name, value)`로 가립니다. 7.1이 `SecretMasker`를 중첩 public record `HttpGitHubClient.Options(URI apiBase, Clock clock, SecretMasker masker, Consumer<String> debugLog)`와 4인자 생성자 오버로드로 받기로 정했습니다(원래 생성자는 `Options.defaults()`, 곧 빈 마스커와 꺼진 디버그 로그로 넘김). `AnthropicLlmClient`도 같은 방식으로 맞춥니다. 민감하지 않은 헤더는 값이 null이면 NPE이고, 이름이 null이면 항상 NPE입니다
    - _Requirements: 9.1, 19.6, 20.1, 20.8, 21.2_
  - [ ] 13.2 `RetryingLlmClient` 데코레이터 구현 (생성자 `RetryingLlmClient(LlmClient, RetryExecutor)`, API 이름 "Claude", `LlmApiException`을 `Attempt.Retryable`/`Fatal`로 분류)
    - 주의(3 리뷰): 연결 실패와 제한 시간 초과를 예외로 던지면 실행기가 재시도 없이 그대로 통과시킵니다. 재시도하려면 `Attempt.Retryable`로 돌려줍니다. `NonRetryableApiException`의 api는 `execute(api, …)`의 인자이고 `Attempt.Fatal.api()`는 쓰이지 않으므로 두 값을 같게 넘깁니다
    - 주의(3 리뷰): 실행기는 `execute`의 `api`와 `Retryable.statusOrError`를 가공 없이 예외(`api()`, `lastStatus()`)와 `RetryListener`에 넘기고, 이 문자열이 요구사항 21.6·21.7의 메시지에 그대로 나옵니다. HTTP 상태는 숫자만으로 된 문자열(`"503"`), 네트워크 오류는 숫자가 아닌 종류 이름으로 맞춥니다 (스펙을 읽고 추론한 항목)
    - 주의(3 리뷰): 실행기는 `retryAfter`가 ASCII 숫자만으로 된 문자열일 때만 유효로 봅니다. 앞뒤 공백, `+5`, `1.5`, HTTP 날짜는 오류 없이 지수 백오프로 넘어가므로, 헤더 값을 가공 없이 넘기되 공백이 붙어 오는지 확인합니다 (스펙을 읽고 추론한 항목)
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

- [x] 15. `ResultCodec` 구현
  - [x] 15.1 직렬화·역직렬화 구현
    - Jackson 트리 모델로 직접 매핑(애너테이션 없음), `schemaVersion: 1`, camelCase 필드, enum 소문자 snake_case, 선택 필드는 키 유지 + `null`, 목록은 `[]`
    - `estimatedCostUsd`는 JSON 숫자 ↔ `BigDecimal`(부동소수점 경유 금지)
    - 오류: 필드 경로(`findings[2].severity`)와 enum 허용 값, 문법 오류 줄·열, 음수 토큰 경로. 모르는 필드 무시
    - Spring Boot 4의 Jackson 3 패키지(`tools.jackson`)는 spike 결과를 따름
    - 주의(선머지 리뷰): `model` 타입은 불변식 위반과 음수 토큰에 `IllegalArgumentException`, 필수 필드 null에 `NullPointerException`을 던집니다. 코덱이 생성 전에 직접 검증해야(`status`와 `incompleteReasons`, `verdict`와 `summaryOnlyReason`, `excludedFiles`와 `excludedFileDetails`) 필드 경로를 담은 `CodecException`이 나가고 `PrLensException`이 아닌 예외가 새지 않습니다. `Usage`는 `estimatedCostUsd`를 `setScale(4, HALF_UP)`으로 맞추므로 `0.1`을 읽으면 `0.1000`으로 저장됩니다
    - 주의(2.4~2.6 리뷰): `ReviewResultArbitraries`는 토큰 수를 `Long.MAX_VALUE`까지, `estimatedCostUsd`를 유효 숫자 18자리(scale 4)까지 냅니다. 토큰 수를 `int`로 읽거나 비용이 `double`을 한 번이라도 거치면, 작은 값을 쓰는 예시 테스트는 통과하고 15.2의 Property 19에서 실패합니다
    - 결정(15.1): `inline_eligible`인데 `line`이 null이거나 `line_missing`인데 `line`이 있는 JSON은 `findings[i].line` 경로의 `CodecException`으로 거부합니다. 읽는 쪽에서 막아 두면 코덱을 거친 결과가 서로 모순된 판정을 갖지 않기 때문입니다(`MarkdownFormatter`는 엔진 결과를 직접 받으므로 이 검증에 기대지 않습니다. 17.1의 주의 참고). 쓰는 쪽은 막지 않으므로 Property 19에는 영향이 없습니다
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.8, 10.9, 11.7, 12.9, 16.4_
  - [x]* 15.2 Property 19(JSON round-trip), Property 20(모르는 필드 무시), Property 21(역직렬화 오류 경로) 테스트
    - **Validates: Requirements 10.1, 10.2, 10.3, 10.4, 10.5, 10.6, 10.7, 10.9, 10.10, 10.11, 11.7, 12.9, 16.4**

- [x] 16. 설정 파일
  - [x] 16.1 `ConfigLoader`, `ConfigException` 구현
    - `--config` 경로만 읽고 없으면 대체 없이 오류, 옵션이 없으면 `./.prlens.yml` → `~/.prlens.yml` 첫 파일, 없으면 기본값
    - UTF-8로 읽고 허용 키와 대조, 범위 검증(요구사항 18.6, effort 허용 값 포함), 모든 문제를 모아 한 번에 보고
    - `cost.pricing`은 모델 이름 단위로 기본 단가표에 병합, 지정한 모델은 네 단가 필수(설계 G-10)
    - 비밀 키 이름(`token`, `githubToken`, `apiKey`, `anthropicApiKey`, 대소문자·`_`/`-` 무시)은 값을 읽지 않고 환경변수 안내
    - `exclude.add`/`exclude.remove` 패턴을 `GlobMatcher`로 검증
    - 주의(선머지 리뷰): `Configuration`은 값 범위를 검증하지 않고, `maxCostUsdPerReview`를 `setScale(4, HALF_UP)`으로 맞춥니다. 범위 검사(0.01~100.00)는 `Configuration`을 만들기 전 원본 값에 합니다. 만든 뒤에 하면 `0.00996`이 `0.0100`으로 반올림돼 통과합니다. `GlobMatcher.validate`는 `GlobSyntaxException(pattern, reason)`을 던지고, 이 타입은 2.3에서 만들어 두었습니다(던지는 조건은 6.1). `pricing` 맵은 null 키·값을 거부합니다
    - 결정(16.1): 소수 다섯째 자리 이하가 있는 비용 상한(`0.123456`)은 `0.1235`로 반올림합니다(HALF_UP). `Configuration`이 이미 이렇게 맞추고 요구사항 18.6은 범위 안의 값을 유효하다고 하므로 거부하지 않습니다. 범위 검사(0.01~100.00)는 반올림 전 원본 값에 하므로 `0.00996`은 거부합니다
    - _Requirements: 3.11, 18.1, 18.2, 18.3, 18.4, 18.5, 18.6, 18.7, 18.8, 18.9, 19.1, 22.2, 22.3_
  - [x] 16.2 `ConfigPrinter` 구현 (모든 항목을 고정 순서로, 문자열은 항상 큰따옴표)
    - 주의(선머지 리뷰): `maxCostUsdPerReview`는 scale 4라 그대로 쓰면 `0.5000`으로 나옵니다. 단가(`ModelPricing`)는 `stripTrailingZeros`를 거쳐 `20.00`이 `2E+1`이 되므로 `toPlainString()`으로 씁니다. `pricing`은 모델 이름의 자연 순서로 순회됩니다
    - 주의(2.4~2.6 리뷰): `ConfigurationArbitraries`는 `model`뿐 아니라 `pricing`의 키에도 `true`, `123`, `null`, `model: v1`, `"quoted"`, `a\b`를 냅니다. `pricing`의 키도 큰따옴표로 감싸고 `"`와 `\`를 이스케이프해야 16.3의 round-trip이 성립합니다
    - _Requirements: 18.10_
  - [x]* 16.3 Property 37(설정 round-trip과 부분 지정), Property 38(설정 검증 오류 수집) 테스트
    - 주의(2.4~2.6 리뷰): `ConfigurationArbitraries.configurations()`는 요구사항 18.6 범위 안의 값만 내므로 Property 38의 범위 밖 값은 따로 만듭니다. `pricing`에는 기본 단가 모델이 항상 있고(G-10 병합 전제) 그 단가는 기본값과 다를 수 있으므로, "기본 모델 단가를 적지 않으면 기본값 유지"는 부분 지정 쪽이나 예시 테스트로 확인합니다. 비용 상한은 소수 넷째 자리까지만 나와 다섯째 자리 이하 처리는 검증되지 않습니다. 패턴 중복은 한 목록 안에서만 없고, 같은 패턴이 추가 목록과 해제 목록에 함께 나올 수 있습니다
    - **Validates: Requirements 18.5, 18.6, 18.7, 18.10, 18.11**

- [x] 17. 출력 형식과 종료 코드
  - [x] 17.1 `MarkdownFormatter` 구현
    - 구역 순서: [불완전 경고(사유, 불완전 Chunk 파일, 부분 결과 표시)] → 요약(요약 전용이면 "PR을 나누세요", Changed_Line_Count, 3×Size_Limit) → 심각도별 지적 수(4개 모두, 강등 수 한 줄) → 파일별 지적((경로, 라인) 오름차순, 없는 `suggestion`·`basis.ref` 생략) → 라인 밖 지적(사유 포함) → 제외 파일(경로와 제외 사유) → 사용된 컨텍스트 파일 (N)(출처 → 경로 순, 0개 안내) → 토큰 사용량과 추정 비용(넷째 자리, 단가 없으면 "알 수 없음")
    - 빈 구역도 제목과 0건 표시, 줄바꿈은 `\n`
    - 주의(15 리뷰): `inline_eligible`의 `line`이 null이 아니라는 보장은 코덱의 읽기 검증이 아니라 10.4의 판정 순서에서 옵니다. 포매터는 엔진 결과를 직접 받으므로 코덱 검증에 기대지 않고, 라인 밖 지적(`not_target_file`, `line_missing`)은 `line`이 null일 수 있습니다 (스펙을 읽고 추론한 항목)
    - _Requirements: 8.1, 8.3, 8.4, 11.6, 12.8, 13.9, 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.14, 16.3, 20.5, 22.5_
  - [x]* 17.2 Property 35: Markdown 구조와 개수 일관성 테스트
    - 주의(2.4~2.6 리뷰): `ReviewResultArbitraries.findings()`는 `file`을 Finding마다 따로 뽑아 같은 파일의 Finding이 거의 겹치지 않으므로, "같은 파일 안 라인 오름차순"(요구사항 15.5)을 보려면 작은 경로 풀에서 `file`을 뽑는 생성기를 따로 둡니다. `file`은 드물게 `""`이고 `summary`·`message`·`suggestion`도 `""`일 수 있습니다. 문자열에 `\r\n`, 탭, 백틱은 나오지만 `#`, `-`, `|` 같은 Markdown 표식은 나오지 않아, 내용이 구역 구조를 깨는 경우는 이 생성기로 검증되지 않습니다
    - **Validates: Requirements 8.1, 8.3, 11.6, 12.8, 15.2, 15.3, 15.4, 15.5, 15.13, 16.3, 22.5**
  - [x] 17.3 `ExitCodeResolver` 구현 (blocker ≥ 1 → 1, incomplete → 3, 그 외 0)
    - _Requirements: 15.9, 15.10, 16.7, 16.8_
  - [x]* 17.4 Property 36: 종료 코드 결정 테스트
    - **Validates: Requirements 15.9, 15.10, 16.7, 16.8**

- [ ] 18. CLI 명령과 파이프라인
  - PR 경계: 18.1~18.2 / 18.3~18.6 / 18.7
  - [x] 18.1 `PrLensMain`, `ReviewCommand` 구현 (picocli, `review <url> [--format markdown|json] [--config <path>]`, 자동 오류 처리 끄고 `UsageException`으로 전달)
    - 결정(18.1): picocli가 직접 쓰는 출력(사용법)은 `setOut`/`setErr`에 UTF-8 `PrintWriter`를 명시해 넘깁니다. 기본 writer의 문자 집합은 확인하지 않고 명시로 피했습니다. 인자 오류는 `execute()`가 처리기의 예외를 삼키므로 `parseArgs`를 직접 불러 `UsageException`으로 바꿉니다
    - _Requirements: 15.1, 15.7, 15.12, 22.1_
  - [x] 18.2 `CliPipeline.preflight` 구현
    - 순서: 환경변수 값을 읽어 `SecretMasker`와 UTF-8 `MaskingPrintStream` 설치(빈 값은 제외) → 인자(URL, `--format`) → 설정 → 환경변수 검사(`GITHUB_TOKEN`, `ANTHROPIC_API_KEY`, 둘 다 없으면 한 메시지). 마스커를 가장 먼저 설치해 인자·설정 오류 메시지도 마스킹합니다
    - 어느 단계든 실패하면 외부 API 호출 없이 종료 코드 2
    - CLI용 `logback.xml` 추가(대상 `System.err`, 기본 수준 WARN). Spring 컨텍스트 없이 실행하면 Logback 기본 설정이 로그를 stdout에 써서 결과 출력에 섞임(요구사항 15.7, 15.8)
    - 주의(3 리뷰): `MaskingPrintStream`은 `\n`으로 끝나지 않은 나머지를 들고 있고, `flush()`는 그 끝이 비밀값의 앞부분과 같으면(한 글자도 해당. `ghp_…`이면 `g`, `sk-ant-…`이면 `s`) 그 부분을 내보내지 않습니다. JVM은 바꿔 끼운 스트림을 종료할 때 flush하지 않으므로, 모든 출력을 `\n`으로 끝내고 프로세스를 끝내기 전에(정상·오류 경로 모두) 두 스트림을 flush합니다
    - 주의(3 리뷰): `MaskingPrintStream.stdout()`/`stderr()`는 `FileDescriptor.out`/`err`를 직접 열므로 `close()`하면 프로세스의 실제 표준 스트림이 닫힙니다. `close()`는 `PrLensMain`의 `System.exit` 직전에만 하고, 테스트가 같은 JVM에서 부르는 경로(`CliPipeline`, 조립 함수)에서는 닫지 않고 `\n`으로 끝낸 뒤 flush합니다. 실제로 닫히는지는 직접 실행해 확인하지 않았으므로 18.6의 하위 프로세스 테스트에서 확인합니다 (스펙을 읽고 추론한 항목)
    - 결정(18.2): 마스커에는 환경변수 원본과 `strip()`한 값을 둘 다 등록하고, 요청에는 다듬은 값을 씁니다(`CliPipeline.secretMasker`)
    - 결정(18.2): `logback.xml`의 encoder에 `<charset>UTF-8</charset>`을 명시했습니다. Logback encoder의 기본 문자 집합은 확인하지 않고 명시로 피했습니다. 설정이 stderr, WARN, UTF-8인지는 `LogbackConfigTest`가 읽어 확인합니다
    - 주의(16 리뷰): `ConfigLoader`는 사용자 홈을 스스로 찾지 않습니다. `new ConfigLoader(Path.of("").toAbsolutePath(), Path.of(System.getProperty("user.home")))`처럼 현재 작업 폴더와 홈을 넘깁니다. `ConfigException.getMessage()`는 문제 전체를 여러 줄로 담고 잘못된 제외 패턴 원문을 포함하므로, 마스커를 설치한 뒤 그대로 stderr에 씁니다
    - _Requirements: 1.9, 1.10, 3.11, 15.12, 18.7, 19.1, 19.2, 19.3, 19.4, 22.3_
  - [ ] 18.3 파이프라인 조립 구현
    - Spring 컨텍스트 없이 `HttpGitHubClient`, `PrFetcher`, `ContextCollector`, `RetryingLlmClient(AnthropicLlmClient)`, `ReviewEngine`을 직접 조립
    - 조립 함수는 `GitHubClient`와 `LlmClient`를 만드는 팩토리를 인자로 받음(CLI 통합 테스트가 호출 수를 세는 가짜를 넘길 수 있게)
    - 결과에서 유도하는 경고를 `StderrReporter`로 출력: 파일 수 부족(`fileCountGap`), 단가 없음(`estimatedCostUsd == null`), 1회 비용 상한 초과
    - 출력 문자열을 모두 만든 뒤 stdout에 한 번만 쓰기, `--format json`은 `ResultCodec` 결과 하나만
    - 주의(3 리뷰): `RetryPolicy` 생성자는 `maxRetries`가 0~10을 벗어나면 `IllegalArgumentException`을 던집니다. 설정 검증(`ConfigLoader`, 요구사항 18.6)이 조립보다 먼저 돌아야 사용자에게 설정 오류로 보입니다
    - 주의(3 리뷰): 결과 문자열이 `\n`으로 끝나지 않으면 끝 글자가 비밀값 첫 글자와 같을 때(`g`, `s` 등) `flush()`만으로는 그 글자가 나오지 않습니다. 결과 끝에 `\n`을 보장하고 쓴 뒤 flush합니다(스트림을 닫는 것은 18.2의 주의대로 `PrLensMain`에서만). 18.6 통합 테스트에 "`\n` 없이 `s`로 끝나는 출력이 끝까지 나온다" 사례를 넣습니다 (스펙을 읽고 추론한 항목)
    - 주의(15 리뷰): `ResultCodec.write`의 결과는 `\n`으로 끝나지 않습니다. `--format json`은 결과 뒤에 `\n`을 붙여 stdout에 한 번에 쓰고 flush합니다(끝의 `\n`은 `read`가 받아들입니다)
    - 주의(17 리뷰): `MarkdownFormatter.format`의 결과는 이미 `\n` 하나로 끝납니다. Markdown 출력에는 `\n`을 더 붙이지 않고, `--format json`일 때만 `ResultCodec.write` 결과에 `\n`을 붙입니다. 무조건 덧붙이면 stdout이 `\n\n`으로 끝나 20.2의 골든 비교가 어긋납니다
    - 주의(7.1 리뷰): 3인자 `HttpGitHubClient` 생성자는 빈 `SecretMasker`를 쓰고 디버그 로그가 꺼져 있습니다. 디버그 모드에서 HTTP 로그를 내려면 18.2에서 만든 같은 `SecretMasker`와 stderr로 가는 `debugLog`를 `Options`에 넣어 4인자 생성자로 조립합니다 (스펙을 읽고 추론한 항목)
    - 미정(여기서 정함): `HttpGitHubClient`는 3xx 응답을 `Attempt.Fatal`(`NonRetryableApiException`)로 분류합니다. JDK `HttpClient` 기본값은 redirect를 따르지 않으므로, 이름이 바뀐 저장소의 301을 따를지(`followRedirects`)와 그때 `Authorization`이 다른 호스트로 가지 않는지(요구사항 19.6)를 정합니다. 실제 응답 확인은 7.7 (스펙을 읽고 추론한 항목)
    - 주의(18.1~18.2 리뷰): 자격 증명은 `CliPipeline.Preflight`의 `githubToken()`, `anthropicApiKey()`(다듬은 값)를 쓰고 `System.getenv`를 다시 읽지 않습니다. 조립 함수는 `PrLensMain.main`의 `CliPipeline.runner(..., preflight -> ...)` 임시 구현 자리에 들어가고, 검증 실패는 `runner`가 예외로 던지므로 조립 쪽에서 다시 검사하지 않습니다
    - _Requirements: 15.7, 15.8, 17.2_
  - [ ] 18.4 `StderrReporter` 구현 (`ReviewListener`, `RetryListener`, `WarningSink` 구현 겸)
    - 모드·Changed_Line_Count·Size_Limit·Chunk 수, 분할 모드 "n/N", 재시도 한 줄(API, "n/최대", 상태, 대기 초), 수집·필터 경고, 파일 수 부족 경고, 불완전 경고와 사유, 비용 상한 초과, 누적 비용 초과, 단가 없음 경고
    - 주의(3 리뷰): `onRetry`의 `wait`는 0초일 수 있고(`retry-after: 0`, 음수 `delay`), 호출자가 정한 `delay`는 초 단위로 떨어지지 않을 수 있습니다. "대기 초"를 `toSeconds()`로 자르면 0으로 찍히므로 표기 방식을 정합니다. 줄바꿈 없이 쓰고 flush하는 진행 표시는 끝 글자가 비밀값의 앞부분과 같으면 다음 쓰기까지 늦게 나오므로, 모든 메시지를 `\n`으로 끝나는 한 줄로 씁니다
    - _Requirements: 1.14, 3.10, 13.12, 15.8, 16.6, 20.3, 20.4, 20.5, 21.7_
  - [ ] 18.5 예외 → 종료 코드 매핑 구현 (2.3에서 만든 `PrLensException`의 하위 타입별 매핑, 설계의 오류 표, 예상하지 못한 예외는 "내부 오류" + 마스킹한 스택(디버그 모드), 종료 코드 2 경로에서 stdout 비움)
    - 주의(3 리뷰): 종료 코드 2 경로와 `System.exit` 앞에서도 메시지를 `\n`으로 끝내고 `System.out`과 `System.err`를 flush합니다(`close()`는 18.2의 주의대로 `PrLensMain`의 `System.exit` 직전에만). `MaskingPrintStream`은 줄이 끝나지 않은 나머지를 들고 있어 그대로 끝내면 마지막 오류 메시지가 사라질 수 있습니다(18.2의 주의와 같은 내용)
    - 주의(17 리뷰): `ExitCodeResolver.resolve(ReviewResult)`는 정적 메서드이고 0, 1, 3만 돌려줍니다. 결과를 만들지 못한 경우의 종료 코드 2는 이 매핑이 정합니다
    - 주의(7.1 리뷰): `x-ratelimit-reset`까지 60초를 넘으면 `github.RateLimitResetException`이 옵니다. `RetryAfterTooLongException`의 하위 타입이고 해제 시각(UTC ISO-8601)은 `getMessage()`와 `resetAt()`에만 있습니다. 상위 타입으로 잡아 `api()`·`seconds()`로 메시지를 새로 만들면 요구사항 21.10의 시각이 빠지므로 `getMessage()`를 그대로 씁니다
    - 주의(18.1~18.2 리뷰): `PrLensMain.main`은 `finally`에서 `out`·`err`를 `close()`한 뒤 `System.exit`을 부르므로, `PrLensMain.run`을 빠져나간 예외는 메시지 없이 JVM 기본 종료 코드 1로 끝납니다(닫힌 stderr에 쓰여 사라짐). `run`의 `catch`에서 모든 예외(`RuntimeException`, picocli `ExecutionException` 포함)를 잡아 매핑하고, 지금 `run`이 직접 2로 바꾸는 `UsageException`·`ConfigException`·`MissingSecretException`도 그 매핑으로 옮깁니다
    - _Requirements: 1.11, 4.11, 13.8, 15.11, 21.5, 21.6, 21.8_
  - [ ]* 18.6 CLI 통합 테스트
    - 잘못된 URL·`--format`·설정·환경변수 사례별 종료 코드 2, 빈 stdout, 가짜 클라이언트 호출 수 0, 비밀 항목 오류에서 값 미출력, `--config` 파일 없음
    - 주의(3 리뷰): `MaskingPrintStream.stdout()`/`stderr()`는 실제 `FileDescriptor`를 열어 단위 테스트가 없고, 둘이 서로 바뀌어도 실패하는 테스트가 없습니다. 하위 프로세스로 실행해 결과는 stdout, 오류는 stderr에 BOM 없는 UTF-8로 나오는지, `\n` 없이 `s`로 끝나는 출력이 끝까지 나오는지, `PrLensMain`이 `System.exit` 직전에 스트림을 닫아도 출력이 잘리지 않는지 한 번 확인합니다
    - 주의(16 리뷰): 설정 사례에서 문자열 값은 큰따옴표로 감쌉니다. 따옴표 없는 `model: 5.5`나 `effort: yes`는 `llm.model`/`llm.effort` 오류가 됩니다. 단가표에 값 없이 적은 모델(`"m":`)은 네 단가 누락 오류입니다
    - 주의(18.1~18.2 리뷰): Linux에서 `PrLensMain.main`을 하위 프로세스로 실행하면 스트림을 닫은 뒤 `System.exit`을 불러도 stderr 오류 메시지가 끝까지 나오는 것을 리뷰 중 직접 확인했습니다(stdout 결과 출력과 macOS·Windows는 확인하지 않음). 통합 테스트에 인자 없는 `prlens` 사례(종료 코드 2, stderr에 사용법)를 넣습니다
    - _Requirements: 1.9, 1.10, 15.11, 15.12, 18.8, 18.9, 19.2, 19.3_
  - [ ] 18.7 실행 패키징 (실행 jar와 래퍼 스크립트 `prlens`(sh), `prlens.cmd`, README에 `chcp 65001` 안내. jar 파일 이름은 `bootJar`의 `archiveFileName`으로 `prlens.jar`로 고정. P2의 Actions 워크플로가 이 이름을 씀)
    - `build.gradle.kts`에 `springBoot { mainClass = ... }`로 CLI 진입점(`PrLensMain`)을 지정. 지정하지 않으면 `java -jar`가 Spring 애플리케이션 클래스를 골라 웹 서버를 띄움(임시 프로젝트로 확인). `build.gradle.kts` 수정은 사람의 승인이 필요
    - _Requirements: 22.1_

- [ ] 19. Checkpoint: T3 완료 확인
  - T3 테스트가 모두 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

### 컨텍스트 (컨텍스트 담당 A, 트랙과 병렬)

- [ ] 23. Claude Code 컨텍스트를 PR Lens에 맞추기 (ROADMAP 2주차 "컨텍스트 담당 A의 추가 작업")
  - PR 경계: 23.1 / 23.2
  - [x] 23.1 ADR 0003 후속 정리 (월요일, 1.1과 함께. `backend/CLAUDE.md`, `testing.md`, `reviewer.md`, 루트 `CLAUDE.md`는 2026-10-04에 반영했으므로 남은 것은 `new-endpoint` 스킬)
    - `backend/CLAUDE.md`의 Architecture 절과 단일 테스트 예시, `.claude/agents/reviewer.md`의 레이어 규칙, `.claude/skills/new-endpoint`를 평면 패키지 기준으로 고침
    - `.claude/rules/backend/testing.md`에 jqwik(속성 하나 = `@Property` 하나, 태그 주석), ArchUnit, `testkit` 위치, 픽스처 규칙(실제 `.env`나 `secrets/` 경로의 파일을 만들지 않음, 반례는 예시 테스트로 고정)을 추가하고 메모 예시를 교체
    - 루트 `CLAUDE.md`의 브랜치 규칙(`feat/<track>-<단계>-<번호>-<slug>`), 워크플로 문구(tasks.md 작업 단위 구현), web `types.ts` 규칙의 P3 전 예외를 고침
  - [ ] 23.2 모듈별 rules 작성 (수요일까지 `main`에 머지)
    - `.claude/rules/backend/github.md`(`paths`: `github`, `pullrequest`, `diff`, `glob`, `filter`, `context` 패키지)와 `review.md`(`paths`: `llm`, `review` 패키지). 첫 며칠 동안 리뷰에서 반복된 지적을 규칙으로 옮김
    - PR Lens는 컨텍스트를 base SHA에서만 읽으므로(요구사항 4.1, 5.1), 이 rules가 `main`에 있어야 금요일 데모 PR의 지적에 규칙 근거가 붙음. 지금 P1 코드 경로에 매칭되는 rule은 `testing.md` 하나뿐임

### 통합 (T3 주도, 전원, 목~금)

- [ ] 20. 골든 픽스처와 전체 파이프라인 테스트
  - PR 경계: 20.1 / 20.2~20.3 (20.4는 실행 기록이라 코드 PR이 아님)
  - [x] 20.1 테스트 픽스처 작성 (`src/test/resources/fixtures/<name>/`) (수요일부터 시작 가능. 공유 타입과 `ResultCodec`만 있으면 됨)
    - 실제 PR diff 샘플 3~5개: 단일 모드, 분할 모드, 제외 파일 포함
    - 인젝션 픽스처 4개: diff 추가 줄, PR 제목, PR 본문, diff 안 닫는 Delimiter_Tag. 기대 결과에 인젝션 위치 `security` Finding과 심어 둔 `blocker` Finding
    - 픽스처마다 `snapshot.json`, `context.json`, `llm-responses/*.json`, `expected-result.json`, `expected.md`. 비밀정보·개인정보는 가짜 값
    - `snapshot.json`과 `context.json`을 읽는 픽스처 로더를 `testkit`에 작성(테스트 전용. `model`에는 Jackson 애너테이션을 넣지 않음)
    - 픽스처 폴더를 `.gitattributes`에 `-text`로 지정(줄 끝 변환 방지). Stop hook은 `.md`만 바뀐 경우 검증을 건너뛰므로 `expected.md`만 고쳤을 때는 `./gradlew test`를 직접 실행
    - 주의(15 리뷰): `ResultCodec.read`는 `schemaVersion`이 1이 아니거나, `inline_eligible`인데 `line`이 null이거나, `line_missing`인데 `line`이 있거나, `excludedFiles`와 `excludedFileDetails[].path`가 순서까지 같지 않으면 `CodecException`으로 거부합니다. 손으로 쓴 `expected-result.json`도 이 조건을 지켜야 합니다
    - 주의(17 리뷰): `expected.md`는 `MarkdownFormatter` 출력과 바이트까지 같아야 합니다. 손으로 쓰지 말고 포매터 출력으로 만든 뒤 검토하며, 형식은 `design.md`의 "구현 형식(작업 17.1)"을 따릅니다
    - _Requirements: 14.7, 17.10, 17.11, 17.12_
  - [ ] 20.2 골든 파이프라인 테스트 작성 (픽스처의 스냅샷과 컨텍스트를 주입해 엔진과 출력을 실행. 가짜 `LlmClient`, stdout 바이트와 종료 코드 비교. `--format json` 출력 전체가 `ResultCodec`으로 다시 읽히는지도 확인)
    - `PrFetcher`와 `ContextCollector`는 이 테스트를 거치지 않음(각자의 테스트 7.x, 8.x로 검증)
    - 주의(15 리뷰): `ResultCodec.write`의 출력은 2칸 들여쓰기와 `\n` 줄바꿈이고 끝에 줄바꿈이 없습니다. JSON 바이트를 비교하는 골든 파일은 이 모양을 따릅니다
    - 주의(17 리뷰): stdout 바이트 비교의 Markdown 쪽은 `MarkdownFormatter` 출력 그대로이고 끝 줄바꿈은 `\n` 하나입니다
    - 주의(20.1 리뷰): 인젝션 픽스처 4개는 `blocker` Finding이 있어 기대 종료 코드가 1이고, `single-basic`, `split-three-chunks`, `excluded-files`는 `complete`이면서 `blocker`가 없어 0입니다. 제목·본문 인젝션의 `security` Finding은 `line`이 null인 `summary_only`(`line_missing`)입니다. `llm-responses/NN.json`은 파일 이름 순서가 Chunk 호출 순서이고 `text`가 객체면 `FixtureLoader`가 압축 JSON 문자열로 바꿉니다. `FixtureLoader`는 리소스를 `Path`로 열어서 jar 안에서는 동작하지 않습니다
    - _Requirements: 14.7, 15.7, 17.9, 17.10, 22.8_
  - [x] 20.3 픽스처 비밀정보 스캔 테스트 (토큰 형식 정규식)
    - 주의(20.1 리뷰): 픽스처에는 40자 hex 모양의 가짜 SHA(`aaaa…`, `bbbb…`), `config/secrets/dev.yml` 경로 문자열(내용 `placeholder: true`), 인젝션 표식 `[prlens-injection]`이 정상 데이터로 들어 있습니다. 토큰 정규식이 이것들을 오탐하지 않아야 합니다(40자 hex 전체를 비밀정보로 보지 않음)
    - _Requirements: 17.12_
  - [ ] 20.4 M1 데모 리허설 (목요일. 실제 API, 사람이 실행)
    - 대상: 고정 픽스처 PR #15. 심어 둔 지적과 판정 기준은 [spike 2026-10-14](../../spikes/2026-10-14-fixture-pr.md)의 "심어 둔 지적"
    - 팀 저장소의 실제 PR 1건을 패키징한 `prlens review`로 리뷰. 규칙 근거(`basis.type`이 `rule`)가 붙은 지적이 1건 이상 나오는지 확인(ROADMAP 2주차 완료 조건). 안 나오면 강등 기록(`demotion`)을 보고 프롬프트의 출력 규칙이나 rules를 고침
    - 400줄 PR의 실행 시간 측정(요구사항 9.7. 재시도 대기 제외, 첫 호출과 그 뒤 호출을 따로 기록), 인젝션 픽스처를 실제 모델로 한 번 실행
    - 결과를 PR이나 `docs/spikes/`에 기록
    - _Requirements: 9.7, 14.7_

- [x] 21. CI 매트릭스 구성
  - GitHub Actions에 `ubuntu-latest`, `windows-latest`, `macos-latest` × Java 17로 `./gradlew test` 실행, 실제 API 호출 없음, 세 OS 모두 통과해야 통과
  - 1.1이 머지되면 바로 만듭니다. OS별 문제(줄바꿈, 인코딩, 경로)가 금요일 전에 드러나야 합니다
  - _Requirements: 22.7, 22.8_

- [ ] 22. Final Checkpoint
  - 전체 테스트와 세 OS CI가 통과하는지 확인하고, 질문이 있으면 사용자에게 묻습니다.

## Notes

- 트랙 담당은 ROADMAP 2주차 기본안(T1 B, T2 A, T3 C)을 따르며 월요일 킥오프에서 바뀔 수 있습니다.
- 작업 본문의 "주의(선머지 리뷰)"와 "미정(여기서 정함)" 항목은 선머지 PR(2.1~2.3)의 리뷰에서 넘긴 것입니다. 2.1~2.3의 코드와 테스트로 확인한 사실도 있지만, 뒤 작업에 대한 부분은 구현 없이 스펙을 읽고 추론한 것이므로 구현하면서 틀린 것이 드러나면 그 항목을 고칩니다. "미정"은 그 작업에서 정하고 필요하면 요구사항이나 설계에 반영합니다.
- 1주차 spike 결과에 따라 세부 구현이 달라지는 작업은 다음과 같습니다. spike 확인 목록은 design.md "1주차 spike 확인 목록"에 있습니다.
  - 13.1, 13.2, 3.1: SDK 빌더 이름, `retry-after` 헤더 접근 방법, 실제 `retry-after` 값에 공백이나 소수가 오는지(실행기는 ASCII 숫자만으로 된 값만 유효로 봄)
  - 7.1: secondary rate limit 본문 문구
  - 18.7: 래퍼 스크립트
  - 2.1: `Configuration.defaults()`의 effort(`medium`)와 최대 출력 토큰(16,000, 범위 1~20,000). [ADR 0005](../../adr/0005-llm-settings.md)
  - 13.1: 스키마 직렬화 순서 고정, 첫 text 블록 선택, SDK 빌더 이름. [spike 2026-10-08](../../spikes/2026-10-08-llm-settings.md)의 "SDK에서 확인한 것"
- 실제 API를 쓰는 확인은 세 번 있습니다: 7.7과 13.4(수요일, 어댑터 단위), 20.4(목요일, 전체 리허설). 400줄 120초 측정(요구사항 9.7), 실제 모델의 인젝션 픽스처 동작, 규칙 근거가 붙은 지적 1건 이상(M1 완료 조건)은 20.4에서 확인합니다.
- design.md "요구사항 공백" G-1~G-7과 G-9, G-10, G-12의 잠정 처리는 해당 작업에 반영했습니다. G-8과 G-11은 결정 전이라 작업이 없습니다. G-13은 제안값으로 10.4에 주의로 넘겼습니다. 요구사항을 고치기로 하면 관련 작업도 함께 수정합니다.
- 결정 대기 항목이 막는 작업은 다음과 같습니다. 결정 전에는 제안값으로 진행하고, 결정이 바뀌면 이 작업들을 다시 봅니다.

  | 항목 | 막는 작업 |
  |---|---|
  | D-1 CLI 라이브러리 (확정: ADR 0004, picocli) | 없음 |
  | D-2 모델·effort·최대 출력 토큰·fallback ([ADR 0005](../../adr/0005-llm-settings.md)) | 1.2, 2.1, 11.5, 13.1, 13.3, 16.1, 16.2 |
  | D-3 비용 상한 | 2.1, 16.1, 18.4 |
  | D-4 불완전 결과와 요약 전용 모드의 종료 코드 | 17.3, 17.4, 18.5, 20.1, 20.2 |
  | D-5 설정 파일 형식·위치 | 16.1, 16.2, 16.3, 18.2 |
  | D-6 생성 코드 기본 패턴 | 6.3, 6.4, 6.6, 20.1 |
  | D-7 스펙 head SHA fallback | 2.1, 8.9, 8.10, 11.5 |
  | D-8 리뷰 대상 저장소 한정 | 결정되면 요구사항과 작업을 추가 |
  | G-13 응답 `file`의 이스케이프 해제 (설계 "요구사항 공백") | 10.4, 12.1 |
  | G-14 LLM이 만든 문자열의 Markdown 구조 문자 처리 (설계 "요구사항 공백") | 17.1, 20.1 |
- 이 스펙은 2주차 세 트랙을 한 건으로 묶은 것입니다(ROADMAP 1주차의 "2주차 스펙 검토와 승인"에 해당). 스펙 하나가 PR 하나가 될 수 없으므로 PR은 상위 작업 단위로 냅니다(Overview의 "PR 단위", 루트 `CLAUDE.md`). PR 경계는 설계 문서의 클래스 수로 어림해 정한 것이라, 실제로 해 보고 맞지 않으면 이 문서의 경계를 고칩니다.
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
