# Spike: `/task`와 `/task-flow` 비교 (2026-10-18 계획, 결과 미기록)

`/task-flow`로 `/task`를 대체할지([task-flow-plan.md](../guide/task-flow-plan.md) "다음 PR"의 "대체") 정하기 위해, 같은 작업을 두 방식으로 한 번씩 진행하고 정확성, 컨텍스트 효율, 사람 부담을 비교합니다. 이 문서는 절차와 기록 양식이고, 결과는 실행한 뒤 아래 표에 채웁니다.

## 설계

- **대상: P1 7.1 (`HttpGitHubClient`, `GitHubCredentials`)** 한 작업을 두 방식으로 각각 합니다.
  - 서로 다른 작업을 나눠 맡기면(예: 7.1은 `/task`, 7.6~7.7은 `/task-flow`) 차이가 방식 때문인지 작업 때문인지 가를 수 없습니다. 7.6은 7.1을 테스트하는 `*` 작업이라 7.1 결과에 따라 달라지고, 7.7은 사람이 실행하는 스모크입니다.
  - 7.1은 PR 경계 하나이고, 미정 5개와 주의 4개가 있어 판단 품질 차이가 드러납니다. 생성자 `HttpGitHubClient(HttpClient, GitHubCredentials, RetryExecutor)`가 설계로 고정되어 있어 두 결과의 테스트를 서로 바꿔 돌릴 수 있습니다.
- **A: `/task`**, **B: `/task-flow`**. 같은 `origin/main` 커밋에서 worktree 두 개로 진행하고, 더 나은 쪽 하나만 PR로 냅니다.
- **맞출 조건**: 같은 모델과 effort, 각각 새 세션에서 시작, 같은 권한 모드. 사람의 답(브랜치 확인, 계획 승인, 미정 질문의 답)은 아래 "답변 기록"에 적어 두고 두 쪽에 같은 내용으로 줍니다.
- **한계**: 표본이 1건입니다. 여유가 있으면 다음 경계(8.1~8.5와 8.6~8.8)에서 방식을 바꿔 한 번 더 해 순서·작업 효과를 줄입니다(교차 설계).

## 절차

1. 준비 (저장소 루트에서)
   ```
   git fetch origin
   git worktree add ../prlen-a origin/main --detach
   git worktree add ../prlen-b origin/main --detach
   ```
   - 시작 커밋: `beb2116` (두 쪽 같음)
   - worktree마다 `build/claude-verify/`가 따로 생겨 hook 상태가 섞이지 않습니다. transcript는 worktree 경로마다 다른 프로젝트 폴더(`~/.claude/projects/<경로>/`)에 쌓입니다.
2. A: `../prlen-a`에서 `claude`를 새로 시작하고 `/task pr-lens-p1-cli 7.1`. 브랜치 이름은 제안에 `-a`를 붙입니다(예: `feat/t2-p1-7.1-github-client-a`).
3. B: `../prlen-b`에서 `claude`를 새로 시작하고 `/task-flow pr-lens-p1-cli 7.1`. 브랜치 이름에 `-b`를 붙입니다. G1이 A의 브랜치를 "진행 중"으로 알리면 시험 중이라 계속한다고 답합니다.
4. 두 쪽 모두 **`/pr-ready` 직전**(A는 구현과 커밋까지, B는 G3 보고까지)에서 멈추고 측정합니다. `/pr-ready`는 고른 쪽에서만 돌립니다.
5. 측정(아래 "지표")을 채운 뒤 하나를 골라 PR로 냅니다. 고르지 않은 쪽은 브랜치와 worktree를 지웁니다(`git worktree remove`, 원격 브랜치 삭제).

A와 B는 두 터미널에서 같은 날 진행합니다. 한쪽을 끝낸 뒤 다른 쪽을 하면 사람이 앞쪽에서 알게 된 것으로 뒤쪽에 다르게 답하기 쉽습니다.

### 답변 기록
두 쪽에 같은 답을 주기 위해 질문과 답을 여기에 적습니다.
| 질문 (요약) | 누가 물었나 (A/B) | 답 |
|---|---|---|
|브랜치명 확인 | A/B | -a / -b 붙여서 생성 하도록 요청|
|브랜치 feat/t2-p1-7.1-http-github-client-a를 만들어 push했습니다. 아래는 7.1의 계획입니다. 승인해 주시기 전에는 파일을 만들거나 고치지 않습니다.

인수 기준별 테스트 (HttpGitHubClientTest는 JDK com.sun.net.httpserver.HttpServer를 쓰므로 새 의존성이 없습니다. 7.6 통합 테스트는 이 PR 밖입니다.)
- 1.8·19.5: authorizesEveryRequestWithCredentialsHeader, debugLogMasksAuthorizationHeaderCaseInsensitively, debugLogOffByDefault
- 1.11: status401/404/plain403ThrowGitHubApiExceptionWithoutRetry(메시지에 상태 코드와 원인 후보를 담음), otherFourxxIsFatalNonRetryable
- 1.13·21.2: networkErrorIsRetried, timeoutIsRetriedWith30sRequestTimeout, status5xxIsRetried
- 21.9: retryAfterHeaderBecomesDelay
- 21.10: remainingZeroUsesResetAsDelay, pastResetDelayIsZero, resetOver60sThrowsWithResetTimeInMessage
- 21.11: secondaryRateLimitBodyWaits60s
- 21.5·21.9: retryAfter61sStopsImmediately
- 21.6·21.7: apiNameIsGitHubAndStatusIsNumericString
- 계약 테스트: getTreeNotFoundThrows, getTreeTruncatedIsPassedThrough, getFileFound/NotFound/IsDirectory/TooLarge(G-12), listFilesMapsRawEntriesAndHasNext(Link 헤더의 rel="next"로 판정), getPullRequestNullBodyKept

만들거나 고칠 파일 (src/main 약 250줄 예상, 400줄 이하)
- 고침: github/HttpGitHubClient.java (본문 구현, 기존 3인자 생성자는 그대로 둠)
- 만듦: github/GitHubApiException.java (비final, 상태 코드를 담음), github/GitHubRateLimitExceptisicGitHubCredentials.java (PAT용Authorization: Bearer <token>, GITHUB_TOKEN 읽기는 CLI가 함)                  침: support/RetryAfterTooLo 성자만 추가, 기존 동작은 그대로)
- 만듦(테스트): HttpGitHubClientTest와 BasicGitHubCredentialsTest
- JSON은 Spring Boot가 이미 가져오는 Jackson 3(tools.jackson)을 씁니다. 새 의존성은 없습니다.

이 작업에서 정하는 "미정" (제안값)                                              트리 항목 type이 blob/tree/c 너뜁니다. contents API의symlink와 submodule 응답은 FileFetch.NotFound로 보냅니다. 내용이 있는 파일이 아니라서 수집기가 건너뛰는 편이 안전합니다. 7.7에서 실제 응답으로 확인합니다.         요구사항 21.10 메시지는 Retr한 GitHubRateLimitException(api,seconds, resetAt)이 만듭니다. x-ratelimit-reset까지 60초를 넘으면 Retryable을 돌려주지 않고 클라이언트가 이 예외를 직접 던지며, 메시지에 한도가 풀리는 시각(UTC)을 넣그래서 support에 protected
3. retry-after가 정수 초가 아니면 헤더가 없는 것으로 보고 x-ratelimit-*, secondary 본문 순서로 계속 분류합니다. 어느 쪽에도 해당하지 않는 403은 권한 부족이고, 429는 Retryable(지수 백오프)입니다
4. 헤더 마스킹은 생성자 오버로드로 넘깁니다. HttpGitHubClient(HttpClient, GitHubCredentials, RetryExecutor, SecretMasker, Consumer<String> debugLog)를 더하고, 기존 3인자 생성자 디버그 로그를 끈 채로 둡니다
5. owner나 repo가 .이나 ..이면 요청을 보내지 않고 GitHubApiException(404)를 던집니다. 경로 이동이 일어나지 않게 하는 방법입니다.

코드로 확인한 "주의"
- 스펙과 다른 것은 없습니다. 본문에 적힌 그대로 getTree는 Optional이 아니고, delay는 retryAfter보다 우선하며, Atte니다.
- 확인한 사실: GitHubApiException은 아직 없어서 새로 만듭니다.

하지 않는 것
- 7.6 가짜 서버 통합 테스트의 나머지 항목(base/head ref 선택, 트리 truncated 대체 경로 등)
- 7.7 실제 GitHub 스모크
- CLI에서 GITHUB_TOKEN을 읽는 preflight 처리
- PrFetcher와 context의 변경

메모 파일: 미정 2번과 4번은 뒤 작업이 쓰는 생성자와 예외 구조를 design.md와 다르게 정하므로 handoff/7.1.md에 남깁니다. 영향받는 작업은 18.2(생성자 조립)와 7.6입니다.

이대로 진행할까요? 바꾸고 싶은 항목이 있으면 말씀해 주세요.|A|진행해줘|
|G2: 7.1 계획 요약

planner가 보고 없이 끝나서 계획 파일 docs/specs/pr-lens-p1-cli/handoff/7.1-plan.md를 직접 읽었다.

슬라이스: 1개 (7.1). 경계 안에서 아직 안 한 하위 작업이 7.1뿐이다. 7.6(가짜 서버 통합 테스트)과 7.7(실제 GitHub 스모크)은 다음 PR 경계다.

만들고 고칠 파일
- HttpGitHubClient.java: 네 메서드 구현, Options record, 오버로드 생성자.
- 새로 만드는 파일은 GitHubApiException, PatCredentials, RateLimitResetException이다.
- 테스트는 HttpGitHubClientTest, PatCredentialsTest, 도우미 FakeHttpClient다.
- design.md와 tasks.md도 고친다.
- src/main 변경은 약 310줄로 400줄 목표 안이다.

테스트 (28개, 전부 실패 테스트를 먼저 커밋)

┌───────────┬────────────────────────────────────────────────────────────────────┬────────────┐
│   영역    │                               테스트                               │ 인수 기준  │
├───────────┼────────────────────────────────────────────────────────────────────┼────────────┤
│           │ 요청마다 Authorization을 credentials에서 받음, Bearer 헤더와       │ 1.8, 19.6, │
│ 인증·요청 │ toString 토큰 숨김, 기준 URI와 credentials의 저장소, 30초 제한     │  21.2      │
│           │ 시간                                                               │            │
├───────────┼────────────────────────────────────────────────────────────────────┼────────────┤
│           │ 401/403/404는 재시도 없이 GitHubApiException, 트리 404는 예외이고  │            │
│ 오류 분류 │ getFile 404는 NotFound, 그 밖의 4xx는 NonRetryableApiException,    │ 1.11       │
│           │ .·.. 저장소는 요청 없이 404                                        │            │
├───────────┼────────────────────────────────────────────────────────────────────┼────────────┤
│ 재시도    │ 5xx는 "503"으로 재시도, 타임아웃·연결 실패·기타 IOException은 각각 │ 1.13       │
│           │  timeout·connection_failed·network_error, 재시도 소진              │            │
├───────────┼────────────────────────────────────────────────────────────────────┼────────────┤
│ 디버그    │ Authorization 마스킹, 기본은 꺼짐                                  │ 19.5       │
│ 로그      │                                                                    │            │
├───────────┼────────────────────────────────────────────────────────────────────┼────────────┤
│           │ retry-after 대기, 61초는 즉시 종료, retry-after가 reset보다 우선,  │            │
│ rate      │ 숫자가 아닌 retry-after는 60초, remaining 0이면 reset까지 대기,    │ 21.9~21.11 │
│ limit     │ 지난 reset은 즉시 재시도, 60초 넘는 reset은 시각이 든              │            │
│           │ RateLimitResetException, secondary는 60초, 단순 429는 지수 백오프  │            │
├───────────┼────────────────────────────────────────────────────────────────────┼────────────┤
│           │ PR 메타(body null), 파일 목록·Link next, 트리 truncated와 알 수    │ 본문(1.8,  │
│ 응답 해석 │ 없는 type 버림, base64 한글 디코딩과 경로 인코딩, 폴더·1MB         │ G-12)      │
│           │ 초과·symlink·submodule, 경로의 ..                                  │            │
└───────────┴────────────────────────────────────────────────────────────────────┴────────────┘

"미정"에 대한 planner의 결정 (확정은 사용자 몫이라 검토해 달라)
- SecretMasker·Clock·기준 URI 전달: 3인자 생성자는 그대로 두고, Options(apiBase, clock, masker, debugLog)를 받는 4인자 오버로드를 더한다. 3인자는 기본값으로 위임하고 디버그 로그는 꺼져 있다.
- 60초를 넘는 reset: HttpGitHubClient가 RetryAfterTooLongException을 상속한 RateLimitResetException을 직접 던진다. 메시지에 UTC ISO 시각을 넣는다(요구사항 21.10).
- 숫자가 아닌 retry-after: remaining/reset과 secondary 판정을 먼저 거치고, 둘 다 아니면 60초를 대기한다. 지수 백오프는 쓰지 않는다.
- contents 응답: submodule은 IsDirectory, symlink와 알 수 없는 type은 NotFound로 돌려준다.
- 트리의 알 수 없는 type: 경고 없이 항목을 버린다.
- .·.. owner·repo: 요청 없이 GitHubApiException(404)를 던지고 PullRequestUrl은 고치지 않는다.

주의·미정 대응: 본문의 주의·미정 줄 9개(153, 154, 156~162)가 모두 표에 있다. 코드와 다름이나 질문은 없다.

실패 테스트가 없는 슬라이스: 없음. 요구사항 변경: 없음(design.md와 tasks.md에 결정만 적는다). 질문: 없음.

자동이었다면 멈췄을 이유: 없음
- 결정 대기: 7.1은 "막는 작업" 표에 없고 G-12는 색인대로 TooLarge다.
- 선행 spike: 7.7이 7.1을 막는 줄은 없다.
- 슬라이스가 경계를 나눈 방식, 테스트 대응, 주의·미정 표: 모두 이상 없다.
- 의존성: Jackson 3는 spring-boot-starter-webmvc로 이미 들어와 있어 build.gradle.kts 변경이 없다.

확인이 필요한 점
- 위 결정 중 바꿀 것이 있나?
- 특히 symlink를 NotFound로 돌려주는 것과 알 수 없는 트리 type을 경고 없이 버리는 것이 괜찮은가?
- 이 계획으로 구현에 들어가도 되나?

승인하면 상태 줄을 계획 승인으로 고치고 implementer를 부르겠다|B|진행해줘|
|7.1 구현을 마쳤습니다. ./gradlew spotlessApply test 전체가 통과했습니다. 커밋과 PR은 만들지 않았고, PR 준비는 /pr-ready에서 합니다.

테스트
- 새 테스트 HttpGitHubClientTest는 38개이고 모두 통과합니다. 구현 전에 먼저 실행했을 때 30개가 실패했습니다.
- 그때의 실패는 UnsupportedOperationException이었고 컴파일 오류가 아니었습니다. 새 타입의 껍데기만 만들어 컴파일은 되게 해 둔 상태였습니다.
- 요구사항별 대응:
  - 1.8·19.5: authorizesEveryRequestWithCredentialsHeader, basicCredentialsUseBearerScheme, debugLogMasksAuthorizationHeader, debugLogIsOffByDefault
  - 1.11·21.8: status401And404ThrowWithCauseCandidatesWithoutRetry, plainForbiddenIsPermissionErrorWithoutRetry, otherFourxxIsFatalAndNotRetried
  - 1.13·21.2·21.6·21.7: status5xxIsRetriedAndApiNameAndStatusAreCleanStrings, exhaustedRetriesReportLastStatus, connectionFailureIsRetried, requestTimeoutIsRetried
  - 21.5·21.9~21.11: retryAfterHeaderBecomesDelayOn403And429, retryAfterOver60StopsImmediately, remainingZeroWaitsUntilResetWhenWithin60Seconds, retryAfterWinsOverReset, resetAlreadyPassedRetriesImmediately, resetOver60SecondsThrowsWithResetTimeInMessage, secondaryRateLimitBodyWaits60Seconds, nonIntegerRetryAfterIsIgnoredAndClassificationContinues
  - G-12 등 조회 계약: getFileOverOneMegabyteIsTooLargeNotEmptyString, getTreeNotFoundThrowsGitHubApiException, 그 밖의 조회·경로 테스트

승인받은 계획과 달라진 점
- 테스트용 생성자가 하나 늘었습니다. 기본 URL과 요청 제한 시간을 바꾸는 package-private 생성자입니다. 가짜 서버와 제한 시간 테스트에 필요했고, 이 파일 안의 변경입니다.
- requestTimeoutIsRetried는 처음에 서버가 받은 요청 수를 단언했다가 경합이 있어서, 시도 횟수(attempts() == 2)를 단언하도록 바꿨습니다. 단언을 지우거나 약하게 만든 것은 아닙니다.
- src/main 변경은 약 280줄입니다. 400줄 이하입니다.

설계와 달라진 점
- design.md를 고칠 곳은 없었습니다. 승인받은 "미정" 다섯 건을 tasks.md의 결정(7.1) 줄로 바꿨고, 7.1 체크박스를 켰습니다.
- "주의" 줄은 코드와 맞아서 그대로 뒀습니다.

뒤 작업에 넘길 것 (docs/specs/pr-lens-p1-cli/handoff/7.1.md에 4항목으로 적었습니다)
- 18.2: 5인자 생성자 조립과 BasicGitHubCredentials, debugLog를 마스킹된 stderr로 보낼 것
- 18.1: GitHubApiException과 GitHubRateLimitException을 종료 코드 2로 바꾸고 getMessage()를 그대로 쓸 것
- 8.1·8.2: 트리 404는 예외, 파일 404와 symlink·submodule은 NotFound
- 7.7: 트리 type 값, symlink·submodule 응답 모양, secondary rate limit 본문 문구("secondary rate limit" 포함 여부로 판별 중)를 실제 응답으로 확인할 것

제안값으로 구현한 결정 대기 항목: 없습니다.

다음 작업: /pr-ready로 PR을 준비하고, 이어서 7번의 다음 PR 경계 7.6(가짜 서버 통합 테스트)이나 7.7(실제 스모크)로 갑니다. 7.7은 GITHUB_TOKEN이 필요합니다.|A|pr-ready 진행해줘|
|슬라이스 1 구현 완료, 종료 검증은 통과하지 못함

구현 결과
- 커밋은 2개다.
  - 5e0f22d test: 실패 테스트
  - 3e7f9fd feat: 구현
- 작업 트리는 깨끗하다.
- 실패 테스트 커밋은 기준을 만족해서 상태 줄에 적었다.
  - 이 슬라이스의 첫 커밋이다.
  - 제목이 test:로 시작한다.
  - 바뀐 파일은 테스트 3개와 계획의 스텁 열에 있는 4개(HttpGitHubClient, PatCredentials, GitHubApiException, RateLimitResetException)뿐이다.
- implementer 보고에 따르면 커밋 직후 28건이 모두 실패했고, 구현 뒤에는 com.prlens.github.*, ArchitectureTest, BoundarySignatureTest가 통과했다.
- 계획과 달라진 점은 없다. test: 커밋 뒤 테스트 변경도 없고, 새 의존성도 없다.
- design.md와 tasks.md는 계획대로 고쳤다(7.1은 [x]).
- 다음 슬라이스에 미치는 영향은 없다.

종료 검증 (--check) 실패
결과 없음: implementer-result.json이 없습니다
build/claude-verify/에 결과 파일이 없다. implementer가 보고에서 전체 검증(spotlessApply test)을 돌리지 않았다고 밝혔고, 일부 테스트만 돌렸다. 그래서 spotless 포맷과 전체 테스트(다른 패키지 회귀)는 확인되지 않은 상태다.

계획한 28개 테스트 외에 다른 테스트가 깨졌는지는 아직 모른다. 이 문제는 내가 직접 고치거나 검증을 돌리지 않는다.

어떻게 할까?
1. 새 implementer로 이 슬라이스를 다시 하게 한다 (추천). 기존 커밋 위에서 spotlessApply test를 돌리고 필요한 것을 고치게 한다. 종료 검증 결과가 남으면 리뷰로 넘어간다.
2. 그대로 리뷰로 간다. 검증 증거 없이 진행하므로 G3에서 멈추고, 이유를 보고에 남긴다.
3. 중단한다. 브랜치와 계획 파일은 남는다.|B|1번으로 진행해줘|
|종료 검증은 통과했다.

ok: backend=pass-nothing-to-verify, web=pass-nothing-to-verify

다만 implementer 보고에 따르면 이번 spotlessApply test는 모든 태스크가 UP-TO-DATE였다. 같은 입력으로 앞서 빌드가 통과했다는 뜻이고, 테스트를 이번에 다시 실행한 것은 아니다. 새로 고친 것은 없고 작업 트리는 깨끗하다.

상태 줄은 리뷰 중으로 고쳤다. reviewer가 첫 리뷰를 하고 있고, 끝나면 결과를 보여 주겠다
(리뷰내용 중략)
질문

1. R-M1은 반드시 수정이다. 수정안은 (a)로 갈까, (b)로 갈까? 추천은 (a)다. design의 "파일 조회 API" 서술과 맞고, encoding: "none"이라는 GitHub의 정식 응답을 쓰기 때문이다. 단 폴더 응답 형태가 바뀌는 점과 실제 확인이 7.7로 미뤄지는 점은 같이 고려해 달라. (b)는 변경이 작지만 403 본문 문구에 기대는 방식이다.
2. 선택 R-S1~S5 중 고칠 것이 있나? 추천은 R-S1만 고치는 것이다. 없으면 "없음"이라고 답하면 된다.

답을 받으면 상태 줄의 고칠 선택에 적고, implementer를 수정 모드로 불러 고친 뒤 확인 리뷰를 돌리겠다.|B|추천대로 진행해줘|
|PR-READY 진행해줘|A|진행해|
|PR-READY 진행해줘|B|진행해|


## 지표

### 정확성
| 지표 | 측정 방법 | A `/task` | B `/task-flow` |
|---|---|---|---|
| 인수 기준 커버리지 | 7.1의 `_Requirements:` 9개(1.8, 1.11, 1.13, 19.5, 19.6, 21.2, 21.9, 21.10, 21.11) 가운데 테스트가 있는 수 | 7/9 (부분 2: 19.6, 21.2) | 7/9 (부분 2: 1.8, 19.6) |
| 교차 테스트 실패 | 상대 쪽 테스트를 내 구현에 돌린 실패 수(아래 "교차 테스트") | 11/28 (결함 0, 판단 차이 4, 어댑터 7) | 5/31 (결함 0, 판단 차이 4, 어댑터 1) |
| 독립 리뷰 "반드시 수정" | 이름을 가린 diff를 같은 reviewer에게(아래 "독립 리뷰") | 0 | 0 |
| 독립 리뷰 "선택" | 같은 리뷰 | 8 (추천 "고침" 3: reset 헤더 범위 초과 예외, 트리 `type` null NPE, 상수 이름) | 8 (추천 "고침" 1: 테스트 추가) |
| 미정 처리 | 본문 미정 5개 가운데 정하지 않았거나 설계·요구사항과 어긋나게 정한 수 | 0/5 (모두 "타당") | 0/5 (모두 "타당") |
| 주의 반영 | 본문 주의 4개 가운데 코드가 따르지 않은 수 | 0/4 | 0/4 |
| test-first 증거 | 실패 테스트 커밋이 있는가, 그 뒤 테스트가 약해졌는가(G3 조건과 같은 검사) | 커밋 없음(테스트와 구현이 `feat:` 한 커밋. `/task`는 커밋 형태를 정하지 않음). 대화 기록상 테스트를 먼저 쓰고 실패(30건)를 확인한 뒤 구현. 리뷰 뒤 테스트 커밋에서 지운 assertion 없음 | `test:` 커밋 `5e0f22d`(테스트 3개 + 스텁 4개). 그 뒤 바뀐 assertion 1줄(R-M1 수정으로 contents 요청의 `Accept` 기대값을 바꿈) |
| 전체 테스트 | `./gradlew spotlessApply test` 결과(테스트 수, 실패) | 376, 실패 0 (새 테스트 31) | 374, 실패 0 (새 테스트 29) |

### 컨텍스트 효율과 비용
토큰 집계 스크립트(`session_usage.py`)의 출력을 붙입니다. 세션을 `/clear`로 나눴으면 세션 파일을 모두 넘깁니다.

| 지표 | A `/task` | B `/task-flow` |
|---|---|---|
| 메인 세션 최대 컨텍스트 | 191,006 | 169,958 (A의 0.89배) |
| 그중 PR 준비 전(A는 커밋 직전, B는 G3) | 148,519 | 134,514 (0.91배) |
| 세션 시작 직후(시스템, `CLAUDE.md`, skill 등 고정분) | 57,813 | 65,620 (`/task` skill도 함께 불러옴, 아래 "진행 중 달라진 점") |
| 압축 횟수 | 0 | 0 |
| 요청 수(메인 + 서브에이전트) | 61 (메인 41, reviewer 20) | 123 (메인 55, planner 22, implementer 30, reviewer 16) |
| 입력 합계(캐시 포함) / 그중 캐시 읽기 | 6,304,030 / 6,053,560 | 10,264,031 / 9,788,456 |
| 출력 합계 | 71,720 | 100,948 |
| 비용 비율(단가 가중, 추정) | 1 | 약 1.6 |
| 걸린 시간(첫 요청~마지막 요청) | 34분 | 32분 |

- 비용 비율은 단가 비를 입력 1, 캐시 쓰기 1.25~2, 캐시 읽기 0.1, 출력 5로 가정한 추정입니다(캐시 쓰기 배율을 어느 쪽으로 잡아도 1.6배 근처). 실제 단가로 다시 계산할 수 있습니다.
- 커밋 뒤 자동으로 돈 보안 리뷰(플러그인) 세션은 두 쪽 다 있어 비교에서 뺐습니다(A 3세션 입력 416,251 / 출력 19,572, B 4세션 438,938 / 28,301).

### 사람 부담과 재작업
| 지표 | A `/task` | B `/task-flow` |
|---|---|---|
| 사람 입력 횟수(스크립트 추정 + 직접 센 값) | 9 / 7 (`/model` 제외) | 10 / 7 (`/model`과 실수로 친 `/task` 제외) |
| 그중 결정에 답한 횟수 | 3 (계획 승인, 리뷰 수정안 요청, 반영 범위) | 4 (G2 승인, 종료 검증 실패 뒤 선택, 리뷰 선택, 뒤 작업 반영) |
| Stop hook·종료 검증 실패 횟수 | 0 | 1 (첫 implementer가 `SubagentHandback` 없이 끝나 결과 파일이 없음 → implementer 재실행) |
| 리뷰 뒤 수정 라운드 | 1 (PR 준비의 리뷰 뒤 테스트 수정) | 1 (R-M1·R-S1 수정과 확인 리뷰) |

### 결과물
| 지표 | A `/task` | B `/task-flow` |
|---|---|---|
| `src/main` 변경 줄 수 | 340 (파일 5) | 429 (파일 4. 400줄 목표를 넘음) |
| 테스트 파일 수와 줄 수 | 1개, 565줄 (실제 HTTP 서버 `HttpServer`) | 3개, 684줄 (가짜 `HttpClient`) |
| 커밋 구성 | 4 (`feat:` 테스트+구현, `docs:` 메모, `test:` 리뷰 반영, `docs:` 스펙 반영) | 4 (`test:`, `feat:`, `fix:` R-M1·R-S1, `docs:` 스펙 반영) |
| 뒤 작업에 넘길 것 | `tasks.md` 결정 5, 주의 6, 미정 1. `design.md` 2곳, P2 문서 2곳 | `tasks.md` 결정 6(다른 작업 1 포함), 주의 3, 미정 2. `design.md` 1곳, P2 `tasks.md` 1곳 |

## 측정 방법

### 교차 테스트
두 구현의 공개 시그니처가 같다는 전제입니다. 미정을 서로 다르게 정해 시그니처가 다르면(예: `SecretMasker`를 생성자로 받는지) 그 테스트는 빼고, 그 차이를 "판단 차이"로 아래에 적습니다.
```
# A의 구현에 B의 테스트를 돌림 (prlen-a에서, 끝나면 되돌림)
git -C ../prlen-a checkout <B 브랜치> -- backend/src/test/java/com/prlens/github
cd ../prlen-a/backend && ./gradlew test --tests 'com.prlens.github.*'
git -C ../prlen-a checkout HEAD -- backend/src/test/java/com/prlens/github
git -C ../prlen-a clean -fd backend/src/test/java/com/prlens/github
```
반대 방향도 같은 방법으로 합니다. 테스트 패키지가 다르면 경로를 맞춥니다.

### 독립 리뷰
어느 방식인지 보이지 않게 diff를 파일로 뽑아 이름을 X, Y로 바꾼 뒤, 새 세션에서 `reviewer` 에이전트에게 두 파일과 스펙 경로(`docs/specs/pr-lens-p1-cli/`, 작업 7.1)를 넘겨 각각 리뷰하게 합니다. 커밋 메시지와 계획 파일, 메모 파일은 넘기지 않습니다(방식이 드러남).
```
git diff origin/main...<A 브랜치> -- backend > X.diff   # 어느 쪽을 X로 할지는 동전을 던져 정함
git diff origin/main...<B 브랜치> -- backend > Y.diff
```

### 판단 차이
미정 5개를 두 쪽이 각각 어떻게 정했는지 적습니다.

| 미정 (tasks.md 7.1) | A | B | 어느 쪽이 나은가와 이유 |
|---|---|---|---|
| 트리 항목 `type`이 그 밖의 값일 때, symlink·submodule | 트리의 그 밖의 `type`은 건너뜀. symlink·submodule은 `NotFound` | 같게 건너뜀. symlink는 `NotFound`, submodule은 `IsDirectory`. 폴더는 `Accept: application/vnd.github.object+json`으로 받아 `{"type":"dir"}`도 `IsDirectory`(리뷰 R-M1) | 비슷함. 둘 다 7.7에서 실제 응답으로 확인할 가정 |
| 요구사항 21.10의 "한도가 풀리는 시각" 메시지를 어디서 만드는가 | `GitHubRateLimitException`(`RetryAfterTooLongException` 하위 타입, `resetAt()`)을 클라이언트가 직접 던짐 | 같은 방식, 이름만 `RateLimitResetException` | 같음 |
| `retry-after`가 정수 초가 아닐 때 | 헤더가 없는 것으로 보고 분류를 계속함. 신호가 없으면 403은 권한 부족, 429는 지수 백오프 | 분류를 계속하고, 신호가 없으면 60초 대기(지수 백오프 안 씀) | B가 설계("GitHub rate limit에는 지수 백오프를 쓰지 않음")에 더 가까움. 블라인드 리뷰는 둘 다 "타당" |
| `SecretMasker`를 넘기는 방법 | 5인자 공개 생성자 `(…, SecretMasker, Consumer<String> debugLog)`. 테스트용 package-private 생성자로 기준 URL과 요청 제한 시간을 바꿈 | `Options(apiBase, clock, masker, debugLog)` record와 4인자 생성자. 요청 제한 시간은 바꿀 수 없음(7.6에 미정으로 넘김) | A는 7.6의 "짧게 주입한 타임아웃"을 바로 쓸 수 있고, B는 시계를 주입해 reset 테스트가 실제 시각에 기대지 않음 |
| owner·repo가 `.`·`..`일 때 URL 조립 | 요청 없이 `GitHubApiException(404)` | 같음. `getFile`의 경로에 `.`·`..`·빈 구간이 있으면 요청 없이 `NotFound`도 더함 | B가 더 방어적 |

## 결정 기준 (2026-10-18 확정)
표본이 1건이므로 굵은 기준을 씁니다. 정확성은 "나빠지지 않는가", 효과는 "뚜렷한가", 비용은 "감당할 만한가"만 봅니다.

**1. 필수 조건** (하나라도 어기면 대체하지 않음)
| 기준 | 값 | 이유 |
|---|---|---|
| 독립 리뷰 "반드시 수정" | B ≤ A | 정확성을 잃으면서 얻을 것은 없음 |
| 교차 테스트 | A의 테스트로 B 구현을 돌린 실패 ≤ B의 테스트로 A 구현을 돌린 실패 | B의 구현이 더 약하지 않은지 |
| 인수 기준 커버리지, 미정·주의 처리 | B가 A보다 나쁘지 않음 | 같은 이유 |
| test-first | B에 G3 조건(구현 섞임, 테스트 약화) 위반 없음 | `/task-flow`가 내세우는 장점 |

**2. 효과 조건** (있어야 대체할 의미가 있음)
| 기준 | 값 | 이유 |
|---|---|---|
| 메인 세션 최대 컨텍스트 | B ≤ A × 0.6 (40% 이상 감소) | `/task-flow`의 핵심 효과. 20~30% 차이는 대화 길이에 따른 흔들림과 가르기 어려움 |
| 압축 | A에서 압축이 있었다면 B에는 없음 | 큰 경계에서 차이가 드러나는 지점 |

**3. 허용 비용** (넘으면 조건부로만 씀)
| 기준 | 값 | 이유 |
|---|---|---|
| 비용 | B ≤ A × 2 | 서브에이전트가 스펙을 따로 읽어 늘어나는 것은 당연함. 2배를 넘으면 작은 작업에는 맞지 않음 |
| 사람 입력 | B ≤ A + 3회 | B는 게이트(G1~G3)가 원래 더 있음. 횟수가 작아 비율보다 차이로 봄 |
| 시간 | B ≤ A × 1.5 | 리뷰까지 하는 B가 당연히 김. 그 이상이면 대기가 문제 |

비용은 토큰 합이 아니라 단가를 곱한 값으로 비교합니다. 캐시 읽기는 일반 입력보다 훨씬 싸서 토큰 수와 비용의 비율이 다릅니다. `session_usage.py` 표의 입력, 캐시 쓰기, 캐시 읽기, 출력에 그때의 단가를 곱합니다(단가는 바뀔 수 있어 스크립트에 넣지 않음).

**결과별 결정**
| 결과 | 결정 |
|---|---|
| 필수, 효과, 비용 모두 충족 | `/task-flow`로 대체 ([task-flow-plan.md](../guide/task-flow-plan.md) "다음 PR"의 "대체") |
| 필수와 효과 충족, 비용 초과 | 대체하지 않고 둘 다 둠. `/task-flow`는 큰 경계(하위 작업 3개 이상이거나 `src/main` 200줄 이상 예상)에만 씀 |
| 필수 충족, 효과 미달 | 7.1이 작아서일 수 있음. 8.x 경계에서 교차 설계로 한 번 더 비교한 뒤 정함 |
| 필수 미충족 | 대체하지 않음. 원인(planner의 계획 품질, implementer의 맥락 부족 등)을 고친 뒤 다시 비교 |
| 경계값 근처(±10%) | 1건으로 판단하지 않고 2차 비교 |

## 결과
2026-10-18 실행. 시작 커밋 `beb2116`, A `feat/t2-p1-7.1-http-github-client-a`, B `feat/t2-p1-7.1-github-client-b`. 같은 모델로 두 터미널에서 동시에 진행했습니다(A 11:09~11:43, B 11:09~11:42 UTC).

### 결정 기준에 대어 본 결과
| 조건 | 결과 | 판정 |
|---|---|---|
| 필수: 독립 리뷰 "반드시 수정" B ≤ A | 0 = 0 | 충족 |
| 필수: 교차 테스트 | 두 쪽 다 실제 결함 0. 실패는 판단 차이와 어댑터 때문 | 충족 |
| 필수: 커버리지, 미정·주의 | 7/9(부분 2), 미정 5 "타당", 주의 4 따름. 두 쪽 같음 | 충족 |
| 필수: test-first | B에 G3 조건 위반 없음(`test:` 커밋 뒤 바뀐 assertion 1줄은 리뷰 수정으로 기대값을 바꾼 것) | 충족 |
| 효과: 메인 최대 컨텍스트 B ≤ A × 0.6 | 0.89배 (PR 준비 전 기준 0.91배) | **미달** |
| 효과: 압축 | 두 쪽 다 없음 | 해당 없음 |
| 비용: B ≤ A × 2 | 약 1.6배 | 충족 |
| 비용: 사람 입력 B ≤ A + 3 | 7 = 7 | 충족 |
| 비용: 시간 B ≤ A × 1.5 | 32분 / 34분 | 충족 |

결과별 결정 표의 **"필수 충족, 효과 미달"**에 해당합니다. 7.1은 하위 작업 하나라 효과가 작게 나올 수 있으므로, 8.x 경계에서 교차 설계로 2차 비교를 한 뒤 대체 여부를 정합니다.

### 드러난 것
- **메인 컨텍스트가 줄지 않은 이유**
  - 세션 시작 직후의 고정분이 약 6만 토큰입니다(시스템, `CLAUDE.md`, skill 목록). 7.1 크기의 작업에서는 이 고정분과 대화가 대부분을 차지합니다.
  - B의 메인은 PR 준비 단계(전체 테스트 결과 확인, 뒤 작업을 스펙 문서에 반영)에서 13.5만에서 17만으로 늘었습니다. `/pr-ready`를 메인 세션에서 돌리면 `/task-flow`가 아낀 몫이 다시 쓰입니다.
- **종료 검증이 빠질 수 있음 (B)**: 첫 implementer가 `SubagentHandback`을 부르지 않고 일반 응답으로 끝나, 그 도구에 걸린 종료 검증 hook이 돌지 않았습니다. 매니저가 `--check`의 "결과 없음"으로 잡아 implementer를 다시 띄웠습니다. 그사이 매니저 턴 끝의 메인 Stop이 backend를 검증해 두어, 재실행은 `pass-nothing-to-verify`로 끝났습니다. implementer가 반드시 `SubagentHandback`으로 끝나게 하거나, 결과 파일이 없을 때의 처리를 skill에 정해야 합니다.
- **400줄 초과 (B)**: B의 `src/main`은 429줄로 400줄 목표를 넘었습니다. 계획은 약 310줄이었고, G3 조건 "src/main 400줄 초과"에 걸리는 크기입니다.
- **판단 품질은 비슷함**: 미정 5개를 거의 같게 정했고, 차이는 submodule 분류, 숫자가 아닌 `retry-after`의 대기, 주입 지점(시계 대 요청 제한 시간), 파일 경로의 `..` 방어였습니다. 리뷰에서 B는 contents 폴더 응답(R-M1)을 고쳤고, A의 블라인드 리뷰는 비정상 헤더 강건성(선택 3건 "고침")을 짚었습니다.

### 진행 중 달라진 점 (측정에 주는 영향)
- 두 쪽 다 계획(4번: `/pr-ready` 직전에 멈춤)과 달리 같은 세션에서 PR 준비까지 진행했습니다. `/pr-ready`는 사람만 실행할 수 있어, 두 쪽 다 "pr-ready 진행해줘" 요청으로 같은 일을 했습니다. 두 쪽이 같은 조건이라 비교에는 쓰지만, 표의 토큰과 시간에는 PR 준비가 포함됩니다.
- B 세션은 `/task`를 먼저 실행한 뒤 `/task-flow`를 실행해 `/task` skill 본문도 컨텍스트에 들어갔습니다(시작 직후 컨텍스트가 A보다 약 8천 많은 원인 가운데 하나).
- X(B)의 블라인드 리뷰어가 근거 문서를 Grep하다가 이 문서의 일치 줄 몇 개를 보았다고 보고했습니다(파일은 열지 않음).
- 교차 테스트는 생성자, 예외 이름, 자격 증명 클래스 이름이 달라 테스트의 조립 코드만 바꾸는 어댑터를 썼습니다. 어댑터로 생긴 실패는 시계 주입 없음(A), 요청 제한 시간 주입 없음(B), 가짜 서버가 `Accept`를 보지 않음, 로그·메시지 문구 차이입니다.

### 고른 쪽
B를 골라 [#35](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/35)로 냈습니다. A의 브랜치와 worktree는 지웠습니다.
- B를 고른 이유: A의 약점은 정확성과 안전에 관한 것이었습니다(큰 `x-ratelimit-reset` 값과 null `type`에서 `PrLensException`이 아닌 예외, contents 경로의 `..`가 그대로 요청 URL로 나감). B의 약점은 크기(429줄)와 7.6 준비(요청 제한 시간 주입 없음)로, PR 설명의 이유와 `tasks.md` 7.6의 미정 줄로 다룰 수 있습니다.
- A가 나았던 점: 400줄 안, 요청 제한 시간 주입(7.6의 "짧게 주입한 타임아웃"), 실제 `HttpServer`를 거치는 테스트.
