# Spike: 실제 API 확인에 쓸 고정 PR 픽스처 (2026-10-14)

P1 작업 7.7(실제 GitHub 스모크), 20.4(M1 데모 리허설), 요구사항 9.7(400줄 실행 시간 측정)이 쓸 PR을 하나 만들어 고정했습니다. 그때그때 PR을 고르면 결과를 비교할 수 없기 때문입니다.

- PR: [#15](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/15) (Draft, 머지하지 않음)
- head: `chore/smoke-fixture-pr`, base: `fixture/base` (2026-10-14의 `main`, `67951db`에 고정)
- base를 `main`으로 두지 않은 이유: PR Lens는 base 커밋의 `CLAUDE.md`와 `.claude/rules/`를 읽습니다. base가 `main`을 따라가면 rules가 바뀔 때마다 같은 PR의 리뷰 결과가 달라집니다. 새 rules로 다시 확인하려면 `fixture/base`를 그 시점의 `main`으로 옮기고 이 문서에 날짜와 SHA를 적습니다
- 정답표(아래 "심어 둔 지적")는 PR 본문에 두지 않았습니다. PR 본문은 모델 입력에 들어가기 때문입니다

## 구성 (12개 파일, 추가 326, 삭제 77)

| 파일 | status | patch | 확인하는 것 |
|---|---|---|---|
| `backend/.../common/api/*.java` 5개 (main) | `added` | 있음 | 9.7 분량, 규칙 근거 지적 |
| `backend/.../common/error/ApiExceptionHandler.java` | `modified` | 있음 | 수정 파일 hunk 해석 |
| `backend/.../common/api/*Test.java` 2개 | `added` | 있음 | testing.md 근거 지적 |
| `docs/generated/review-request-schema.json` | `added` | 있음 | 생성 코드 기본 제외(D-6 `**/generated/**`) |
| `docs/images/review-request-flow.png` | `added` | **없음** | 바이너리 기본 제외, patch 없는 파일 |
| `docs/spec-review/06-p3-requirements.md` → `06-p3-web-requirements.md` | `renamed` | **없음** | 내용 변경 없는 이름 변경 |
| `docs/spec-review/README.md` | `removed` | 있음(삭제 77줄) | 삭제 파일 |

GitHub API로 확인한 사실 (`gh api .../pulls/15/files`, 2026-10-14):

- **바이너리뿐 아니라 내용이 같은 이름 변경도 `patch` 필드가 없습니다.** 지금 스펙은 patch가 없으면 `Absent`(작업 7.4)로 두고, 필터는 `Absent`를 `binary_or_too_large`로 제외합니다(작업 6). 그래서 이 파일은 "바이너리 또는 너무 큼"으로 표시됩니다. 리뷰할 줄이 없으니 결과는 같지만 사유가 틀립니다. 사유를 따로 둘지(예: `renamed_only`)는 정하지 않았습니다(T2에서 결정)
- `changed_files`(12)가 파일 목록 길이(12)와 같습니다
- 픽스처 브랜치에서도 `./gradlew spotlessApply test`가 통과합니다. 규칙 위반은 ArchUnit에 걸리지 않게 `common` 아래에 두었습니다

## 심어 둔 지적 (20.4 판정용)

20.4는 `basis.type`이 `rule`인 지적이 1건 이상 나오면 통과입니다. 아래 표는 그 판정과 누락 비교에 씁니다. 모델이 모두 찾아야 하는 것은 아닙니다.

| 근거 | 위치 | 내용 |
|---|---|---|
| `.claude/rules/backend/api-design.md` | `ReviewRequestController` | URL이 `/api/v1/...`가 아님 (`/reviews`, `/stats/review_requests`) |
| 〃 | `CreateReviewRequest`, `ReviewRequestView` | JSON 필드가 snake_case (`pr_url` 등) |
| 〃 | `CreateReviewRequest`, `create` | Bean Validation과 `@Valid`가 없고 손으로 검사함 |
| 〃 | `create` | 생성 응답이 `200`, `Location` 헤더 없음 |
| 〃 | `get` | 없는 id에 `200`과 임의의 Map을 돌려줌 (`404` + `ErrorResponse`가 아님) |
| 〃 | `list` | 페이지네이션 없음 |
| 〃 | `DuplicateReviewRequestException` | `ApiExceptionHandler`에 매핑되지 않아 500이 됨 |
| 〃, ADR 0002 | `ApiExceptionHandler.handleIllegalArgument` | `ErrorResponse`가 아닌 문자열, code가 소문자 |
| `.claude/rules/backend/testing.md` | `*Test.java` | assertion이 없는 테스트 |
| 〃 | `*Test.java` | `Thread.sleep`으로 실제로 기다림 (`Clock`·`Sleeper` 주입 아님) |
| 〃 | `*Test.java` | 웹 계층 테스트에 `@SpringBootTest` (`@WebMvcTest`가 아님) |
| 〃 | `*Test.java` | 메서드 이름이 camelCase 문장이 아님 (snake_case) |
| `backend/CLAUDE.md` | `common/api/` | 새 코드를 `common` 아래에 둠 |

규칙과 무관한 결함 (`basis.type`이 `rule`이 아닌 지적으로 나올 수 있음):

- `ReviewRequestStats.stats`: 끝난 요청이 없으면 0으로 나눔 (`ArithmeticException`)
- `ReviewRequestStore.updateStatus`: 없는 id면 `NullPointerException`
- `ReviewRequestStore`: `HashMap`과 `nextId++`가 동시 요청에 안전하지 않음
- `updateStatus` 컨트롤러: `Status.valueOf`에 소문자가 오면 예외 (조회 응답은 소문자를 내보냄)
- `create`: `dry_run`이면 지운 항목의 id를 돌려줌

## 결과 기록

7.7과 20.4를 실행한 결과는 이 아래에 날짜별로 덧붙입니다.

### 2026-10-21 작업 7.7 실제 GitHub 스모크

`GitHubSmokeTest`(`PRLENS_SMOKE=1`과 `GITHUB_TOKEN`이 모두 있을 때만 실행)를 PR #15에 실행했습니다. base `67951dbc5bd88af91969d30214129a82b6503d6d`, head `8d5e338c73e79e5d0590404b570d4b642d57fd10`. 9개 중 통과 5, 건너뜀 3, 실패 1입니다.

| 확인 항목 | 결과 |
|---|---|
| PR 메타데이터 `title`·base/head SHA, 파일 항목 `filename`·`status`가 항상 채워짐 | 확인됨 (`PrFetcher`가 null 거부 record를 통과) |
| `changed_files` 12 == 받은 파일 12, 경고 없음, 추가/삭제 326/77 | 확인됨 |
| 상태 ADDED/MODIFIED/REMOVED/RENAMED, 이름 변경 이전 경로, patch가 `Absent`인 것은 png와 이름 변경뿐, `Unparseable` 없음 | 확인됨 |
| base 재귀 트리가 잘리지 않음, `CLAUDE.md`·`.claude/rules/backend/testing.md`(BLOB)·`backend`(TREE) 포함 | 확인됨 |
| 트리 항목 `path`가 항상 있음, `type`이 blob/tree/commit 안에 있음 | 확인됨 (다른 값 없음) |
| contents: 파일 `Found`, 폴더 `IsDirectory`(`application/vnd.github.object+json`의 객체 응답), 없는 경로 `NotFound` | 확인됨 |
| **없는 SHA의 트리 조회가 404** | **다름: 422**. 본문 `Invalid object requested. SHA must identify a commit or a tree.` (`0`x40과 임의의 40자리 16진수 둘 다). 클라이언트는 이를 `NonRetryableApiException`(상태 422)으로 던지고 `GitHubApiException`(404)이 아닙니다 |
| 1MB 초과 blob이 `TooLarge`로 매핑 | 대상 없어 확인 못 함 (base 트리에 1MB 초과 blob 없음) |
| symlink(mode 120000)가 `NotFound`로 매핑 | 대상 없어 확인 못 함 |
| submodule(type commit)이 `IsDirectory`로 매핑 | 대상 없어 확인 못 함 |
| secondary rate limit 본문 문구 | 확인 못 함 (일부러 일으키지 않음) |
| 이름이 바뀐 저장소의 301 처리 | 확인 못 함 (이 스모크의 범위 밖) |

조치: 이 PR은 `src/main`을 고치지 않고 `treeOfUnknownShaThrowsNotFound`의 assertion도 바꾸지 않았습니다(승인된 방침). 이 테스트는 opt-in이라 CI와 Stop hook에는 영향이 없고, 스모크를 실행하면 실패합니다. 실제 동작이 422이므로 `getTree`가 422를 어떻게 분류할지(`GitHubApiException` 404 가정 변경 여부)는 별도 `fix/` 브랜치에서 재현 테스트를 먼저 써서 정합니다.

### 2026-10-26 작업 20.4 M1 데모 리허설 (일부만 실행)

API 크레딧이 소진됐고 팀이 추가 결제를 하지 않기로 해서, 크레딧이 필요한 항목은 실행하지 않았습니다. 실행한 것은 아래 두 번과 그 출력의 확인입니다. 실행 환경은 Windows 11의 PowerShell이고, `backend/scripts/prlens.cmd`로 실행했습니다. jar를 만든 시점의 커밋과 Java 버전은 기록하지 않았습니다.

대상은 PR #15이고 base SHA는 `67951dbc5bd88af91969d30214129a82b6503d6d`입니다(디버그 로그의 트리 조회 요청에서 읽음). 이번 실행의 head SHA는 확인하지 않았습니다(2026-10-21 스모크 때 head는 `8d5e338c73e79e5d0590404b570d4b642d57fd10`).

#### 실행 1: 실제 리뷰 (`--format json`, 성공)

| 항목 | 결과 |
|---|---|
| `status`, `stats` | `complete`, 모드 `single`, `changedLineCount` 387, `sizeLimit` 400, `chunkCount` 1 |
| Finding 수 | 23건. `basis.type`별로 `rule` 17, `general` 4, `spec` 2 |
| 심각도 | `blocker` 1, `major` 14, `minor` 8 |
| 강등(`demotion`) | 23건 모두 `null` |
| `verdict` | 22건 `inline_eligible`, 1건 `summary_only`(`line_missing`, 삭제된 `docs/spec-review/README.md`) |
| 제외 파일 3개 | `docs/generated/review-request-schema.json`(`**/generated/**`), `docs/images/review-request-flow.png`(`**/*.png`), `docs/spec-review/06-p3-web-requirements.md`(`binary_or_too_large`) |
| `usage` | 입력 9,870, 출력 8,746, 캐시 쓰기 45,066, 캐시 읽기 0, 모델 `claude-opus-5-5`, `estimatedCostUsd` 0.4397 |

- **판정**: `basis.type`이 `rule`인 지적이 17건이라 ROADMAP 2주차 완료 조건(1건 이상)을 충족합니다.
- 비용 0.4397은 CLI가 계산한 추정값입니다. 실제 청구액은 확인하지 않았습니다. D-3의 제안값 $0.50보다 낮습니다.
- 이 실행의 종료 코드, 소요 시간, stderr 전체, 재시도 여부는 기록하지 않았습니다. 그래서 요구사항 9.7(120초)은 측정하지 않았습니다.
- 이름만 바뀐 `06-p3-web-requirements.md`의 제외 사유가 `binary_or_too_large`로 나왔습니다. 위 "구성"의 설명대로이고, 사유를 따로 둘지는 여전히 정하지 않았습니다.

"심어 둔 지적" 대조 (정답표 13건, 규칙 외 5건):

| 심어 둔 지적 | 대응한 Finding (파일:줄) |
|---|---|
| URL이 `/api/v1/`가 아님 | `ReviewRequestController`:19, `ReviewRequestStats`:21 |
| JSON 필드 snake_case | `CreateReviewRequest`:7, `ReviewRequestView`:9 |
| Bean Validation·`@Valid` 없음 | `CreateReviewRequest`:7, `ReviewRequestController`:29 |
| 생성 응답 200, `Location` 없음 | `ReviewRequestController`:29 |
| 없는 id에 200과 Map | `ReviewRequestController`:45 |
| 목록 페이지네이션 없음 | `ReviewRequestController`:51 |
| `DuplicateReviewRequestException` 미매핑(500) | `ReviewRequestStore`:42 |
| `handleIllegalArgument`가 `ErrorResponse`가 아님 | `ApiExceptionHandler`:61 (`basis.ref`는 `docs/adr/README.md`) |
| assertion 없는 테스트 | `ReviewRequestControllerTest`:15, `ReviewRequestStatsTest`:15 |
| `Thread.sleep` | `ReviewRequestControllerTest`:42, `ReviewRequestStatsTest`:15 |
| `@SpringBootTest` | `ReviewRequestControllerTest`:7 |
| 메서드 이름 snake_case | `ReviewRequestControllerTest`:15 |
| 새 코드를 `common` 아래에 둠 | `ReviewRequestController`:1 |
| (규칙 외) 0으로 나눔 | `ReviewRequestStats`:34 (`blocker`) |
| (규칙 외) `updateStatus` NPE | `ReviewRequestStore`:62 |
| (규칙 외) `HashMap`·`nextId++` 동시성 | `ReviewRequestStore`:36 |
| (규칙 외) `Status.valueOf` 대소문자 | `ReviewRequestController`:61 |
| (규칙 외) `dry_run`이 지운 id를 돌려줌 | `ReviewRequestController`:35 |

정답표 밖의 지적도 있었습니다(스펙에 없는 기능, 삭제 응답 200, `Instant.now()` 직접 호출, `docs/spec-review` 링크 깨짐 등). 일부는 P2 스펙의 항목을 직접 인용합니다(D-17, "통계 API는 P3 범위", 요구사항 5.3·16.6). 그 인용이 실제 스펙과 맞는지는 대조하지 않았습니다.

#### 실행 2: Claude 4xx (크레딧 소진 상태)

`PRLENS_DEBUG=1`로 같은 PR을 실행했습니다(`cmd /c`로 stdout과 stderr를 파일에 리다이렉트).

- 종료 코드 2, stdout 0바이트(요구사항 15.11).
- Claude 응답의 오류 메시지는 다음과 같이 나왔습니다. 터미널 줄바꿈 때문에 `Please`와 `go` 사이에 줄이 바뀌어 있었고, 아래는 공백으로 이은 것입니다.
  `Claude 요청이 실패했습니다 (상태 코드 400, invalid_request_error: Your credit balance is too low to access the Anthropic API. Please go to Plans & Billing to upgrade or purchase credits.)`
- 13.5의 사유 추출이 실제 4xx 응답에서도 `invalid_request_error: …` 형태로 동작합니다. 이 본문은 잔액 부족의 고정 문구라 입력 내용은 섞여 있지 않았습니다. 입력 내용이 섞여 올 수 있는 다른 4xx(잘못된 모델 이름, 요청 크기 초과 등)는 확인하지 않았습니다.
- 디버그 로그에 GitHub 요청 11건(`GET`)이 모두 `< 200`으로 나왔고, 11건 모두 `Authorization: ***`로 가려졌습니다. Claude 요청은 `POST messages model=claude-opus-5-5 effort=medium max_tokens=16000` 한 줄만 나오고 요청 본문과 API 키는 출력되지 않았습니다. 4xx 뒤 재시도 요청은 없었고(POST 1건) 응답 헤더에 `x-should-retry: false`가 있었습니다.
- GitHub 응답은 상태 코드만, Claude 오류 응답은 헤더 전체를 출력합니다. 의도된 차이인지는 확인하지 않았습니다.
- 응답 헤더에는 조직·워크스페이스 ID와 `request-id`가 있으므로 이 문서에는 옮기지 않았습니다.

#### 래퍼와 인코딩

- Windows에서 PowerShell의 `.\prlens.cmd`(실행 1)는 인자가 전달되어 JSON이 출력됐습니다. 종료 코드 2의 전달은 `cmd /c`로 실행한 실행 2에서만 `$LASTEXITCODE`로 확인했고, PowerShell에서 직접 실행했을 때의 종료 코드는 기록하지 않았습니다.
- 위 두 번 외에도 실행했지만 결과를 기록하지 않은 것이 있습니다. `...`를 주소 자리에 그대로 넣은 실행(주소 형식 오류 메시지가 나옴)과, PowerShell에서 `2>`로 stderr를 파일로 보낸 실행입니다. 이 실행들의 종료 코드는 기록하지 않았습니다.
- 주소 형식 오류가 났던 첫 실행은 `chcp 65001` 없이 실행해 한글이 깨졌습니다. 이후 출력에서는 한글이 정상으로 보였습니다(그 사이 `chcp 65001`을 실행했는지는 기록하지 않았습니다).
- PowerShell 5.1에서 `2>`로 stderr를 리다이렉트하면 `NativeCommandError`가 붙습니다. CLI가 아닌 PowerShell의 동작이라 `cmd /c`로 리다이렉트했습니다.

#### 확인하지 않은 것 (크레딧이 필요하거나 환경이 없음)

| 항목 | 상태 |
|---|---|
| 400줄 PR의 실행 시간 (첫 호출과 뒤 호출, 요구사항 9.7) | 측정하지 않음 |
| 캐시 읽기 토큰이 0보다 큰지 | 확인하지 않음 (실행 1의 `cacheReadTokens`는 0) |
| 인젝션 픽스처를 실제 모델로 실행 | 실행하지 않음 (방법도 정하지 않음) |
| 실제 결과에서의 종료 코드 1 | 확인하지 않음 (실행 1의 종료 코드를 기록하지 않음) |
| 실제 결과가 stdout에 BOM 없는 UTF-8로 끝까지 나오는지 | 확인하지 않음 |
| macOS의 `scripts/prlens` | 실행하지 않음 |
| 재시도 대기 중단(`IllegalStateException`) 경로 | 확인하지 않음 |
| 이름이 바뀐 저장소 PR URL의 3xx 오류 | 확인하지 않음 |
| 입력 내용이 섞이는지 확인할 다른 4xx | 확인하지 않음 |

이 기록을 근거로 작업 20.4를 닫았습니다. 위 "확인하지 않은 것"의 처리는 `tasks.md` 20.4의 `결정(20.4)`에 있습니다.
