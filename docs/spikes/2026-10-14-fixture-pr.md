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
