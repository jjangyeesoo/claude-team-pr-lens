# Requirements Document

## Introduction

PR Lens P3(4주차 마일스톤 M3)는 P2가 DB에 쌓은 리뷰 데이터를 웹에서 조회하는 기능입니다. 범위는 PRD의 FR-12(조회 API 보강과 통계 집계 API), FR-13(PR 리뷰 화면), FR-14(통계 화면)와 웹에 해당하는 비기능 요구사항(보안, 접근성, 오류 처리, 이식성)입니다.

- 기반: P1 스펙(`docs/specs/pr-lens-p1-cli/requirements.md`)과 P2 스펙(`docs/specs/pr-lens-p2-automation/requirements.md`)의 용어와 규칙을 그대로 씁니다. 이 문서에서 "P1 요구사항 N", "P2 요구사항 N"은 각 스펙의 요구사항 N을 가리킵니다. 특히 Review_Run, Run_Status, Stored_Finding, Publish_Outcome, Feedback_State, Query_API(P2 요구사항 10), Standard_Error_Response, 서버 바인딩 주소(P2 요구사항 10.11), Severity_Threshold는 P2 정의를 따릅니다.
- P2와의 관계: P3는 Query_API를 보강하고(목록 페이지네이션, 응답 필드 추가, 통계 API) P2 요구사항 10.5(최대 100개 잘라내기)를 요구사항 1의 페이지네이션으로 대체합니다. P2 요구사항 10.13(빈 목록)은 페이지네이션 아래에서 `items` `[]`, `totalCount` 0, `totalPages` 0으로 읽고, P2 요구사항 10.15(round-trip)는 1페이지부터 `totalPages`페이지까지의 `items`를 이어 붙인 목록에 적용합니다. 그 밖의 P2 요구사항 10 항목(정렬, 대소문자 무시 조회, 404·400·500 응답, OpenAPI 문서, 필드 이름 규칙, 바인딩 경고)은 유지합니다. P3는 저장 스키마와 webhook·게시 동작을 바꾸지 않습니다.
- 조회 전용: Web_App과 Query_API는 데이터를 읽기만 합니다. 개수, 비율, 합계 같은 계산은 backend(Stats_API)에서만 하고, Web_App은 받은 값을 표시합니다.
- 트랙 분할(4주차): T1 C(기능 리드) 메모 샘플 화면 제거, 화면 골격(레이아웃·내비게이션·라우트 폴더), PR 목록·상세 화면, 상태 표시(요구사항 9~12). T2 A 조회 API 보강, 통계 집계 API, API 계약과 `web/src/lib/api/types.ts`(요구사항 1~8). T3 B 통계 화면, 차트 접근성(요구사항 13~14). 공통(전원) 요구사항 15~17.
- 일정 메모: 월요일에 리드 C가 화면 골격과 라우트 폴더를, A가 `types.ts`의 타입 정의를 먼저 머지합니다. 이후 API PR만 `types.ts`를 수정하고 화면 PR은 수정하지 않습니다(ROADMAP 4주차 규칙). 금요일은 기능 동결이며 이후에는 버그 수정만 합니다. 이는 작업 순서 계획이며 시스템 요구사항이 아닙니다.
- 범위 축소 순서: 일정이 밀리면 FR-14 통계(통계 API와 그 구현 경계 요구사항 3~7, 통계 화면 요구사항 13~14)를 가장 먼저 뺍니다(ROADMAP 일정 위험). 이 경우 채택률과 비용 지표는 PLAYBOOK 5장 방식(DB 직접 집계, PR 설명 수기 기록)으로 모읍니다.
- 컨텍스트 관리 실험(ROADMAP 4주차, 컨텍스트 담당 B): `web/CLAUDE.md`와 `.claude/rules/frontend/`를 PR Lens 화면에 맞게 고치고, FE 작업 세션에서 BE 규칙이 로드되지 않는지 `/context`로 확인합니다. 이는 개발 프로세스이며 시스템 요구사항이 아닙니다.
- 완료 조건(ROADMAP): 로컬에서 BE와 web을 띄워 3주차부터 쌓인 실제 리뷰 데이터를 화면으로 본다(요구사항 17).
- 범위 밖: 사용자 인증과 멀티 테넌시, 운영 배포와 가용성 보장, 데이터 수정(웹에서 채택/기각 변경, 리뷰 재실행, 삭제 등), 실시간 갱신(자동 새로고침, 푸시), PLAYBOOK 수기 기록의 화면 집계.

## Glossary

P1 Glossary와 P2 Glossary의 모든 용어(Finding, Basis, Demotion_Record, Inline_Eligible, Summary_Only, Incomplete_Reason, Review_Mode, Changed_Line_Count, Normalized_Repo_Path, Secret_Value, PR_Lens_Server, Review_Run, Run_Trigger, Run_Status, Stored_Finding, Publish_Outcome, Publish_Error, Feedback_State, Summary_Comment, Line_Comment, Severity_Threshold, Review_Store, Query_API, Standard_Error_Response, Configuration, Config_Loader 등)는 P1·P2 정의를 그대로 따릅니다. 아래는 P3에서 추가하거나 확장하는 용어입니다.

- **Web_App**: `web/`의 Next.js(App Router, TypeScript) 조회 전용 웹 애플리케이션. Server Component에서 Query_API를 호출한다
- **API_Client**: `web/src/lib/api/` 안에서 Query_API를 호출하고 응답을 `types.ts` 타입으로 돌려주는 Web_App 구성 요소. Web_App에서 Query_API를 호출하는 유일한 모듈이다
- **API_Types**: `web/src/lib/api/types.ts`에 정의한 Query_API 요청·응답 TypeScript 타입
- **Backend_Base_URL**: API_Client가 Query_API 호출에 쓰는 기본 URL. 서버 측 환경변수 `PRLENS_API_BASE_URL`로 받고 기본값은 `http://127.0.0.1:8080`
- **Page_Request**: 목록 조회의 페이지 지정. 쿼리 변수 `page`(1부터, 기본 1)와 `size`(기본 20, 허용 1~100)
- **Page_Response**: 목록 응답 형식. `items`(해당 페이지 항목), `page`, `size`, `totalCount`(전체 항목 수), `totalPages`(`totalCount`를 `size`로 나눈 값의 올림, `totalCount`가 0이면 0)
- **Sort_Key**: 목록마다 정한 전체 순서(total order). P2 요구사항 10.1~10.4의 정렬 기준 뒤에 유일한 값을 마지막 기준으로 붙여 같은 순위의 항목이 없게 한 정렬 기준(요구사항 1.3)
- **Stats_API**: 통계 집계 값을 반환하는 Query_API의 엔드포인트 묶음(`/api/stats/...`, T2)
- **Stats_Filter**: Stats_API의 공통 필터. 저장소(`repository`, `owner/repo`, 선택)와 기간(`from`, `to`, UTC 날짜 `YYYY-MM-DD`, 양 끝 포함)으로 이루어진다
- **Stats_Period**: Stats_Filter의 기간. Review_Run의 등록 시각(UTC)이 `from` 날짜 00:00:00 이상, `to` 다음 날 00:00:00 미만인 범위
- **Counted_Run**: 지적 통계(규칙별, 심각도별, 채택률)에 포함하는 Review_Run. Stats_Filter를 만족하고 Run_Status가 `succeeded` 또는 `incomplete`인 Review_Run (D-3 제안값)
- **Cost_Run**: 비용 통계에 포함하는 Review_Run. Stats_Filter를 만족하고 Run_Status가 `succeeded`, `incomplete`, `failed`, `superseded` 중 하나인 Review_Run
- **Rule_Stat**: 규칙별 지적 통계 항목. `basis.type`(`rule` 또는 `spec`), `basis.ref`, 지적 수로 이루어진다
- **Feedback_Target**: 채택률 집계 대상 Stored_Finding. Counted_Run에 속하고 Publish_Outcome이 `line_comment_posted`인 Stored_Finding (D-4 제안값)
- **Adoption_Rate**: `adopted` Feedback_Target 수 ÷ (`adopted` 수 + `rejected` 수). 분모가 0이면 null (D-4 제안값)
- **Cost_Bucket**: 비용 추이의 시간 구간. `day`(UTC 하루) 또는 `week`(월요일 00:00 UTC에 시작하는 7일, Stats_Period 경계에서는 기간 안의 날만 포함)
- **Basis_Link**: `basis.type`이 `rule` 또는 `spec`인 Finding의 근거 파일로 가는 GitHub URL `https://github.com/{owner}/{repo}/blob/{base SHA}/{basis.ref}`. `basis.ref`의 경로 구간마다 퍼센트 인코딩한다
- **Code_Link**: Finding의 대상 코드로 가는 GitHub URL `https://github.com/{owner}/{repo}/blob/{head SHA}/{file}`와, `line`이 있으면 `#L{line}`
- **Line_Comment_Link**: Line_Comment로 가는 GitHub URL `https://github.com/{owner}/{repo}/pull/{PR 번호}#discussion_r{Line_Comment ID}`
- **Summary_Comment_Link**: Summary_Comment로 가는 GitHub URL `https://github.com/{owner}/{repo}/pull/{PR 번호}#issuecomment-{Summary_Comment ID}`
- **Status_Label**: Run_Status, 완전성 상태, Feedback_State, Publish_Outcome을 사람이 읽는 한국어 텍스트로 보여 주는 화면 요소. 색은 보조 수단으로만 쓴다
- **Error_Screen**: Query_API 호출 실패 시 Web_App이 보여 주는 화면. 오류 요약과 "다시 시도" 링크를 담는다
- **Empty_State**: 표시할 항목이 0개일 때 Web_App이 목록·차트 대신 보여 주는 안내 문구
- **Chart_Data_Table**: 차트와 같은 값을 담은 표. 차트의 텍스트 대체 수단이다
- **Type_Contract_Check**: CI에서 API_Types와 `docs/api/openapi.yaml`의 스키마가 일치하는지 검사하는 자동 검사

## Requirements

### Requirement 1: 목록 페이지네이션 (FR-12)

**User Story:** P3 웹 개발자로서, 목록이 100개를 넘어도 모든 항목을 페이지로 나눠 보고 싶다. 그래야 3주차부터 쌓인 데이터가 잘리지 않고 화면에 나온다.

#### Acceptance Criteria

1. THE Query_API SHALL P2 요구사항 10.1~10.4의 모든 목록 엔드포인트(저장소, PR, Review_Run, Stored_Finding 목록)에 Page_Request를 받고 Page_Response 형식으로 응답한다 (P2 요구사항 10.5 대체)
2. WHEN `page`나 `size`가 없으면, THE Query_API SHALL `page` 1, `size` 20으로 처리한다
3. THE Query_API SHALL 목록마다 Sort_Key를 쓴다. 저장소 목록은 P2 요구사항 10.1 기준 뒤에 GitHub 저장소 ID 오름차순, PR 목록은 P2 요구사항 10.2 기준(마지막 기준이 PR 번호로 저장소 안에서 유일), Review_Run 목록은 P2 요구사항 10.3 기준 뒤에 Review_Run ID 내림차순, Stored_Finding 목록은 P2 요구사항 10.4 기준(순번이 Review_Run 안에서 유일)이다
4. IF `page`가 부호와 앞자리 0이 없는 ASCII 십진수로 쓴 1 이상 2,147,483,647 이하의 정수가 아니거나, `size`가 부호와 앞자리 0이 없는 ASCII 십진수로 쓴 1 이상 100 이하의 정수가 아니거나, `page`나 `size`가 두 번 이상 있으면, THEN THE Query_API SHALL 목록 없이 400과 잘못된 쿼리 변수 이름을 모두 담은 Standard_Error_Response 형식의 본문을 반환한다
5. WHEN `page`가 `totalPages`보다 크면, THE Query_API SHALL 200과 빈 `items`, 실제 `totalCount`와 `totalPages`를 반환한다
6. THE Query_API SHALL `items`를 Sort_Key 순서로 (`page` − 1) × `size`번째 항목(0부터)부터 최대 `size`개 반환한다
7. IF 경로 변수가 P2 요구사항 10.8의 형식 규칙을 어기면, THEN THE Query_API SHALL 쿼리 변수 검사 결과와 관계없이 400과 잘못된 경로 변수 이름을 담은 Standard_Error_Response 형식의 본문을 반환한다
8. IF 경로 변수가 P2 요구사항 10.8의 형식 규칙을 지키지만 대상(저장소, PR, Review_Run)이 Review_Store에 없으면(P2 요구사항 10.7), THEN THE Query_API SHALL 쿼리 변수를 검사하기 전에 404와 Standard_Error_Response 형식의 본문을 반환한다
9. WHEN 목록 요청에 `page`, `size` 외의 쿼리 변수가 있으면, THE Query_API SHALL 해당 쿼리 변수를 무시하고 해당 쿼리 변수가 없는 요청과 같은 응답을 반환한다
10. FOR ALL 저장된 데이터(조회 중 변경 없음)와 `size` S, 1페이지부터 `totalPages`페이지까지의 `items`를 이어 붙인 목록 SHALL Sort_Key로 정렬한 전체 목록과 같고, 중복 항목이 없고, 길이가 `totalCount`와 같다 (페이지 완전성 속성)
11. FOR ALL 저장된 데이터(조회 중 변경 없음)와 Page_Request, 같은 요청을 두 번 보낸 응답 SHALL 같다 (정렬 안정성·멱등 속성)
12. FOR ALL 목록 응답, 인접한 두 항목 SHALL Sort_Key 순서를 지키고 Sort_Key 값이 같지 않다 (전체 순서 불변 속성)

### Requirement 2: 조회 응답 필드 보강 (FR-12, FR-13)

**User Story:** P3 웹 개발자로서, PR 상세 화면에 필요한 값을 API 한 곳에서 받고 싶다. 그래야 웹에서 값을 조합하거나 계산하지 않는다.

#### Acceptance Criteria

1. THE Query_API SHALL Review_Run 목록(P2 요구사항 10.3)의 각 항목에 P2 필드에 더해 base SHA, `summary`, 오류 종류, 치환된 오류 메시지(P2 요구사항 4.5), Publish_Error, Summary_Comment ID, Changed_Line_Count, 불완전 상세 정보의 Chunk 파일 경로 목록을 담는다 (값이 없으면 null)
2. THE Query_API SHALL Stored_Finding 목록(P2 요구사항 10.4)의 각 항목에 P2 필드에 더해 Publish_Outcome과 Summary_Only 사유를 담고, 값이 정해지지 않은 경우(Review_Run이 `queued`·`running`인 경우, 게시 없이 CLI `--save`로 저장된 경우, Summary_Only가 아닌 Finding의 사유)에는 null로 담는다
3. THE Query_API SHALL `GET /api/repositories/{owner}/{repo}/pulls/{number}`로 PR 한 건(저장소 owner·이름, PR 번호, 제목, 마지막으로 본 head SHA(P2 요구사항 7.5), Review_Run 수, 마지막 Review_Run ID)을 반환하고, 마지막 Review_Run ID는 해당 PR의 Review_Run 목록을 Sort_Key로 정렬한 첫 번째 항목의 ID로 하며, 경로 변수 형식 오류에 400(P2 요구사항 10.8), 대상 없음에 404(P2 요구사항 10.7)를 반환한다
4. THE Query_API SHALL `GET /api/runs/{runId}`로 Review_Run 한 건을 1번 필드와 소속 저장소 owner·이름, PR 번호와 함께 반환하고, 경로 변수 형식 오류에 400(P2 요구사항 10.8), 대상 없음에 404(P2 요구사항 10.7)를 반환한다
5. THE Query_API SHALL 응답에 컨텍스트 파일 내용, diff 원문, Secret_Value를 포함하지 않는다 (P2 요구사항 7.9, 18.2)
6. THE Query_API SHALL `HEAD` 요청을 같은 경로의 `GET`과 같은 상태 코드로 본문 없이 처리하고, `GET`·`HEAD` 외의 HTTP 메서드(`OPTIONS` 포함) 요청에 405와 Standard_Error_Response 형식의 본문을 반환하며, 두 경우 모두 저장된 데이터를 바꾸지 않는다
7. FOR ALL 저장된 Review_Run, `GET /api/runs/{runId}` 응답에서 소속 저장소 owner·이름과 PR 번호를 뺀 필드 SHALL 같은 Review_Run의 Review_Run 목록 항목과 필드 집합과 값이 같다 (일관성 속성)

### Requirement 3: 통계 공통 필터 (FR-12, FR-14)

**User Story:** 연구회 팀으로서, 통계를 저장소와 기간으로 좁혀 보고 싶다. 그래야 주차별, 저장소별 지표를 발표 자료에 쓸 수 있다.

#### Acceptance Criteria

1. THE Stats_API SHALL 모든 통계 엔드포인트에서 Stats_Filter(`repository`, `from`, `to`)를 받는다
2. WHEN `from`과 `to`가 없으면, THE Stats_API SHALL `to`를 요청 시점의 UTC 날짜로, `from`을 `to`의 29일 전 날짜로 처리한다 (최근 30일, D-5 제안값)
3. WHEN `from`과 `to` 중 하나만 있으면, THE Stats_API SHALL `to`만 있을 때 `from`을 `to`의 29일 전 날짜로, `from`만 있을 때 `to`를 `from`의 29일 후 날짜로(요청 시점의 UTC 날짜보다 늦어도 그대로) 정한다
4. IF `from`이나 `to`가 `YYYY-MM-DD` 형식의 유효한 그레고리력 날짜가 아니거나, `from`이 `to`보다 늦거나, 양 끝을 포함한 기간 일수가 366일을 넘거나, 해당 엔드포인트가 받는 쿼리 변수(`repository`, `from`, `to`, `top`, `bucket`)가 두 번 이상 있으면, THEN THE Stats_API SHALL 400과 잘못된 쿼리 변수 이름을 모두 담은 Standard_Error_Response 형식의 본문을 반환한다
5. IF `repository`가 P1 요구사항 1.3의 `owner/repo` 문자 규칙을 어기면, THEN THE Stats_API SHALL 400과 쿼리 변수 이름 `repository`를 담은 Standard_Error_Response 형식의 본문을 반환한다
6. IF `repository`로 지정한 저장소가 Review_Store에 없으면, THEN THE Stats_API SHALL 404와 Standard_Error_Response 형식의 본문을 반환한다
7. THE Stats_API SHALL `repository`를 대소문자를 무시해 조회하고(P2 요구사항 10.6), `repository`가 없으면 모든 저장소를 대상으로 한다
8. THE Stats_API SHALL 모든 통계 응답에 실제로 적용한 `from`, `to`, `repository`(Review_Store에 저장된 대소문자 그대로, 없으면 null)를 담고, 요구사항 4·5의 응답에는 Counted_Run 수를 함께 담는다
9. THE Stats_API SHALL Stats_Period를 Review_Run의 등록 시각 기준으로 판정한다
10. WHEN 통계 요청에 해당 엔드포인트가 받는 쿼리 변수(`repository`, `from`, `to`, `top`, `bucket`) 외의 쿼리 변수가 있으면, THE Stats_API SHALL 해당 쿼리 변수를 무시하고 해당 쿼리 변수가 없는 요청과 같은 응답을 반환한다
11. FOR ALL 저장된 데이터와 Stats_Filter, 저장소별로 조회한 값의 합 SHALL `repository` 없이 조회한 같은 기간의 값과 같다 (메타모픽 속성). 적용 대상 값: 전체 Stored_Finding 수, `general` Stored_Finding 수, 강등된 Stored_Finding 수, 심각도별 개수, 네 Feedback_State 개수, Feedback_Target이 아닌 Stored_Finding 수, Counted_Run 수, Cost_Run 수, 서로 다른 PR 수, 추정 비용 합, 토큰 수 합, 비용 상한 초과 Cost_Run 수(기간 전체 값과 Cost_Bucket별 값). 제외 값: Rule_Stat 목록, `(basis.type, basis.ref)` 조합 수, Adoption_Rate, PR당 비용
12. FOR ALL 저장된 데이터와 두 기간 P1 ⊆ P2, P1으로 조회한 각 값 SHALL P2로 조회한 같은 값 이하이다 (메타모픽 속성, 11번과 같은 적용 대상 값에 적용)

### Requirement 4: 규칙별 지적 통계 (FR-12, FR-14)

**User Story:** 컨텍스트 담당으로서, 어떤 규칙과 스펙이 자주 위반되는지 보고 싶다. 그래야 반복되는 지적을 규칙으로 흡수하거나 불필요한 규칙을 정리할 수 있다.

#### Acceptance Criteria

1. THE Stats_API SHALL `GET /api/stats/rules`로 Counted_Run의 Stored_Finding 중 `basis.type`이 `rule` 또는 `spec`인 것을 `(basis.type, basis.ref)`로 묶은 Rule_Stat 목록을 반환하고, `basis.ref`가 코드 포인트 단위로 정확히 같을 때만 같은 묶음으로 보며, 저장소가 달라도 같은 묶음으로 센다
2. THE Stats_API SHALL Rule_Stat 목록을 지적 수 내림차순, 같으면 `basis.type` 오름차순, 같으면 `basis.ref` 코드 포인트 오름차순으로 정렬하고 앞에서부터 `top`개를 반환한다
3. WHEN `top`이 없으면, THE Stats_API SHALL `top`을 10으로 처리한다
4. IF `top`이 1 이상 50 이하의 정수가 아니면, THEN THE Stats_API SHALL 400과 쿼리 변수 이름 `top`을 담은 Standard_Error_Response 형식의 본문을 반환한다
5. THE Stats_API SHALL 응답에 Rule_Stat 목록과 함께 잘라내기 전 전체 `(basis.type, basis.ref)` 조합 수, Counted_Run의 전체 Stored_Finding 수, `basis.type`이 `general`인 Stored_Finding 수, 그중 Demotion_Record가 있는 Stored_Finding 수를 담는다
6. THE Stats_API SHALL Basis_Validator가 강등한 Finding(Demotion_Record 있음)을 강등 후 `basis.type`인 `general`로 집계한다
7. WHEN Counted_Run이 없거나 Counted_Run에 `basis.type`이 `rule` 또는 `spec`인 Stored_Finding이 없으면, THE Stats_API SHALL 200과 빈 Rule_Stat 목록, 조합 수 0을 반환하고, Counted_Run이 없으면 응답의 모든 개수를 0으로 반환한다
8. FOR ALL 저장된 데이터와 Stats_Filter, 각 Rule_Stat의 지적 수 SHALL Counted_Run의 Stored_Finding을 직접 세어 같은 `(basis.type, basis.ref)`인 것의 개수와 같다 (모델 기반 속성)
9. FOR ALL 저장된 데이터와 두 값 N1 ≤ N2, `top` N1의 Rule_Stat 목록 SHALL `top` N2 목록의 앞부분(prefix)과 같다 (메타모픽 속성)
10. FOR ALL 응답 중 `top`이 전체 `(basis.type, basis.ref)` 조합 수 이상인 응답, Rule_Stat 지적 수의 합과 `general` Stored_Finding 수의 합 SHALL 전체 Stored_Finding 수와 같다 (불변 속성)

### Requirement 5: 심각도별 분포와 채택률 (FR-11, FR-12, FR-14)

**User Story:** 연구회 팀으로서, 지적의 심각도 분포와 채택률을 자동으로 보고 싶다. 그래야 5주차 발표의 채택률 지표(목표 50%)를 수기 집계 없이 확인할 수 있다.

#### Acceptance Criteria

1. THE Stats_API SHALL `GET /api/stats/severity`로 Counted_Run의 Stored_Finding 수를 `blocker`, `major`, `minor`, `nit` 순서로, 0개인 심각도까지 모두 담아 반환하고, 전체 Stored_Finding 수를 함께 담는다
2. THE Stats_API SHALL 심각도별 분포를 Severity_Threshold와 관계없이 모든 Stored_Finding으로 센다 (P2 요구사항 13.3)
3. THE Stats_API SHALL `GET /api/stats/adoption`으로 Feedback_Target의 `adopted`, `rejected`, `conflicted`, `none` 개수(0개 포함), Adoption_Rate, Feedback_Target이 아닌 Counted_Run Stored_Finding 수를 반환한다
4. IF Adoption_Rate의 분모(`adopted` 수 + `rejected` 수)가 0이면, THEN THE Stats_API SHALL Adoption_Rate를 null로, 분자와 분모를 0으로 반환한다
5. THE Stats_API SHALL Adoption_Rate를 분자를 분모로 나눈 IEEE 754 배정밀도 나눗셈 결과로 소수 자릿수 반올림 없이 계산하고, 분자와 분모 정수와 함께 반환한다
6. FOR ALL 저장된 데이터와 Stats_Filter, 심각도별 개수의 합 SHALL 전체 Stored_Finding 수와 같고, 전체 Stored_Finding 수는 규칙별 통계(요구사항 4.5)의 전체 Stored_Finding 수와 같다 (불변 속성)
7. FOR ALL 저장된 데이터와 Stats_Filter, Adoption_Rate SHALL null이거나 0 이상 1 이하이고, null이 아니면 분자 ÷ 분모와 같다 (불변 속성)
8. FOR ALL 저장된 데이터와 Stats_Filter, 네 Feedback_State 개수 SHALL Feedback_Target을 직접 세어 Feedback_State별로 나눈 개수와 같다 (모델 기반 속성)
9. FOR ALL 저장된 데이터와 Stats_Filter, 네 Feedback_State 개수와 Feedback_Target이 아닌 Counted_Run Stored_Finding 수의 합 SHALL 전체 Stored_Finding 수와 같다 (불변 속성)

### Requirement 6: PR당 비용 추이 (FR-12, FR-14)

**User Story:** 연구회 팀으로서, 기간별 리뷰 비용과 PR당 비용을 보고 싶다. 그래야 월 예산과 1회 비용 상한($0.50)을 지키는지 확인할 수 있다.

#### Acceptance Criteria

1. THE Stats_API SHALL `GET /api/stats/cost-trend`로 Stats_Period를 Cost_Bucket으로 나눈 목록을 시작 날짜 오름차순으로 반환한다
2. WHEN `bucket`이 없으면, THE Stats_API SHALL `bucket`을 `day`로 처리한다
3. IF `bucket`이 `day`, `week` 중 하나와 정확히 일치하지 않으면, THEN THE Stats_API SHALL 400과 쿼리 변수 이름 `bucket`을 담은 Standard_Error_Response 형식의 본문을 반환한다
4. THE Stats_API SHALL Cost_Run을 등록 시각(UTC)으로 Cost_Bucket에 배정하고, 각 Cost_Bucket에 시작 날짜와 종료 날짜(양 끝 포함, Stats_Period 경계로 잘라냄), Cost_Run 수, Cost_Run의 서로 다른 PR 수(`(저장소, PR 번호)` 기준), 추정 비용 합(null 제외, 없으면 0), 추정 비용이 null인 Cost_Run 수, 입력·출력·캐시 쓰기·캐시 읽기 토큰 수 합, PR당 비용(추정 비용 합 ÷ 추정 비용이 null이 아닌 Cost_Run이 하나 이상 있는 서로 다른 PR 수, 이 PR 수가 0이면 null)을 담는다
5. THE Stats_API SHALL Cost_Run이 없는 Cost_Bucket도 개수와 합을 0으로, PR당 비용을 null로 해 목록에 포함한다
6. THE Stats_API SHALL 응답에 기간 전체의 Cost_Run 수, 추정 비용 합, 서로 다른 PR 수, PR당 비용(요구사항 6.4와 같은 분모 규칙)과 요청 시점 Configuration의 1회 리뷰 비용 상한(P1 요구사항 18.3)을 담는다
7. THE Stats_API SHALL 추정 비용이 null이 아니고 요청 시점 Configuration의 1회 리뷰 비용 상한보다 큰(같은 값 제외) Cost_Run 수를 Cost_Bucket마다 담는다 (D-12)
8. THE Stats_API SHALL 비용 합을 저장된 소수점 넷째 자리 추정 비용(P1 요구사항 20.2)의 정확한 십진 합으로 계산하고, 응답에서 소수 자릿수를 잘라내지 않는다
9. FOR ALL 저장된 데이터와 Stats_Filter, Cost_Bucket의 추정 비용 합의 합계 SHALL Cost_Run 추정 비용(null 제외)을 직접 더한 값과 같고, Cost_Bucket의 Cost_Run 수 합계는 기간 전체 Cost_Run 수와 같다 (모델 기반·불변 속성, P2 요구사항 8.5와 같은 값 사용)
10. FOR ALL 응답, Cost_Bucket의 [시작 날짜, 종료 날짜] 구간 SHALL 서로 겹치지 않고 Stats_Period를 빈틈없이 덮으며, `bucket`이 `day`이면 각 구간이 1일이고, `week`이면 잘라낸 첫 구간과 마지막 구간을 뺀 각 구간이 월요일에 시작하는 7일이며, 모든 비용과 토큰 수는 null이거나 0 이상이다 (불변 속성)

### Requirement 7: 통계 집계 구현 경계와 성능

**User Story:** 4주차 T2 개발자로서, 통계 로직이 테스트 가능하고 빠르게 동작하기를 원한다. 그래야 DB 없이 집계 규칙을 검증하고 화면이 느려지지 않는다.

#### Acceptance Criteria

1. THE PR_Lens_Server SHALL Stats_API의 집계를 Review_Store 인터페이스(P2 요구사항 16.2)를 통해 수행하고, Review_Store의 메모리 구현(P2 요구사항 16.6)으로도 같은 집계를 실행할 수 있게 한다
2. WHILE Review_Store에 저장소 10개, PR 1,000개, Review_Run 10,000개, Stored_Finding 100,000개가 있는 동안, THE Query_API SHALL 로컬 환경에서 요구사항 1~6의 각 엔드포인트 요청(목록은 `size` 100, 통계는 366일 기간, `repository` 없음, `top` 50, `bucket` `day`)에 같은 기기에서 요청 전송부터 응답 본문 수신 완료까지 2초 이내에 응답한다 (예열 요청 1회 제외, 엔드포인트마다 5회 측정 모두 2초 이하여야 통과)
3. IF 조회나 집계 중 Review_Store 오류가 발생하면, THEN THE Stats_API SHALL 500과 DB 비밀번호가 치환된 Standard_Error_Response 형식의 본문을 반환한다 (P2 요구사항 10.14와 같은 규칙)
4. FOR ALL 연산 순서열(Review_Run 저장, Run_Status 변경, Stored_Finding 저장, Publish_Outcome과 Feedback_State 갱신), Stats_Filter, 고정한 요청 기준 시각 T, Review_Store의 메모리 구현과 DB 구현 SHALL 시각 T 기준으로 평가한 요구사항 1~6의 모든 조회에 같은 응답을 반환한다 (모델 기반 속성, P2 요구사항 16.9 확장)

### Requirement 8: API 계약 문서와 타입 일치 (FR-12)

**User Story:** 4주차 FE 개발자로서, 웹의 타입이 실제 API 응답과 항상 같기를 원한다. 그래야 화면 PR이 API 변경 때문에 런타임에 깨지지 않는다.

#### Acceptance Criteria

1. THE Query_API SHALL 요구사항 1~6의 엔드포인트, 쿼리 변수, Page_Response, 통계 응답 스키마, 오류 응답(400, 404, 405, 500)을 `docs/api/openapi.yaml`에 추가하고, CI에서 엔드포인트마다 200 응답과 문서화한 각 오류 응답을 최소 1회씩 호출해 실제 응답이 문서 스키마를 따르는지 검사한다 (P2 요구사항 10.9 확장)
2. THE Web_App SHALL API_Types에 `docs/api/openapi.yaml`의 응답 스키마마다 같은 이름의 타입을 둔다
3. THE Type_Contract_Check SHALL API_Types와 `docs/api/openapi.yaml`의 스키마 이름, 필드 이름, 필드 타입, 필드의 필수·선택·null 허용 여부, 열거형 값이 다르면 CI 빌드를 실패시킨다
4. THE Web_App SHALL API_Types의 필드 이름과 열거형 값을 Query_API JSON과 같은 이름으로 쓴다 (P2 요구사항 10.10)
5. THE Web_App SHALL 저장소의 PR 템플릿에 체크리스트 항목 "API_Types 변경이 Query_API DTO와 openapi.yaml 변경과 같은 PR에 있음"을 둔다
6. FOR ALL `docs/api/openapi.yaml`의 응답 스키마 S, API_Types의 대응 타입 T SHALL S와 같은 필드 집합, 같은 필드 타입, 같은 열거형 값 집합을 가진다 (모델 기반 속성, Type_Contract_Check로 검사)

### Requirement 9: 화면 골격과 내비게이션 (FR-13)

**User Story:** 리뷰어로서, 모든 화면에서 저장소 목록과 통계로 바로 이동하고 싶다. 그래야 PR 리뷰 이력과 통계를 오가며 볼 수 있다.

#### Acceptance Criteria

1. THE Web_App SHALL 스타터의 메모 샘플 화면, 라우트, 메모 API 호출 코드, 메모 타입을 제거한다
2. THE Web_App SHALL 라우트 `/`(저장소 목록), `/repositories/{owner}/{repo}`(PR 목록), `/repositories/{owner}/{repo}/pulls/{number}`(PR 상세), `/stats`(통계)를 제공한다
3. THE Web_App SHALL 모든 화면에 제품 이름과 "저장소", "통계" 링크를 담은 공통 헤더를 두고, 라우트 `/`와 `/repositories/`로 시작하는 라우트에서는 "저장소" 링크에만, 라우트 `/stats`에서는 "통계" 링크에만 `aria-current="page"`를 붙이고, 404 화면과 Error_Screen에서는 두 링크를 모두 `aria-current` 속성 없이 보여 준다
4. THE Web_App SHALL PR 목록 화면에 "저장소 › owner/repo", PR 상세 화면에 "저장소 › owner/repo › PR #번호" 형식의 경로 표시(breadcrumb)를 `aria-label`을 가진 `<nav>` 요소로 두고, 마지막 항목은 링크 없는 텍스트에 `aria-current="page"`를 붙이고 나머지 항목은 해당 화면 링크로 보여 준다
5. THE Web_App SHALL 화면마다 `<h1>` 하나와 다음 문서 제목(`<title>`)을 둔다: `/`는 `저장소 · PR Lens`, PR 목록은 `owner/repo · PR Lens`, PR 상세는 `PR #번호 · owner/repo · PR Lens`, `/stats`는 `통계 · PR Lens`, 404 화면은 `찾을 수 없음 · PR Lens`, Error_Screen은 `오류 · PR Lens`
6. THE Web_App SHALL 문서 언어를 `ko`로 지정한다
7. IF 라우트 경로 변수가 P2 요구사항 10.8의 형식 규칙을 어기거나 `number`가 0으로 시작하면, THEN THE Web_App SHALL Query_API를 호출하지 않고 HTTP 상태 404와 함께 404 화면(요구사항 15.7)을 보여 준다

### Requirement 10: 저장소 목록과 PR 목록 화면 (FR-13)

**User Story:** 리뷰어로서, 저장소별로 리뷰된 PR과 최근 상태를 한눈에 보고 싶다. 그래야 확인할 PR을 빨리 찾는다.

#### Acceptance Criteria

1. THE Web_App SHALL 저장소 목록 화면에 저장소마다 `owner/repo`(PR 목록 화면 링크)와 PR 수를 Query_API 순서대로 표로 보여 준다
2. THE Web_App SHALL PR 목록 화면에 PR마다 번호, 제목(PR 상세 화면 링크), 마지막 Review_Run의 Run_Status(Status_Label), 마지막 Review_Run 등록 시각(요구사항 15.9 형식), `blocker`·`major`·`minor`·`nit` 순서의 Finding 수, GitHub PR 링크(`https://github.com/{owner}/{repo}/pull/{PR 번호}`)를 Query_API 순서대로 표로 보여 준다
3. THE Web_App SHALL 목록 화면의 페이지를 URL 쿼리 변수 `page`로 지정하고 Page_Request `size` 20으로 Query_API를 호출한다
4. WHEN 목록 전체 항목이 1개 이상이면, THE Web_App SHALL 목록 아래에 전체 항목 수, 현재 페이지와 전체 페이지 수, 이전·다음 페이지 링크를 보여 주고, 첫 페이지의 이전 항목과 마지막 페이지의 다음 항목은 `<a>` 요소가 아닌 비활성 텍스트로 보여 준다 (전체 항목이 0개이면 페이지 이동 영역 없음)
5. WHEN 목록 항목이 0개이면, THE Web_App SHALL 표 대신 Empty_State(저장소 목록: "아직 저장된 리뷰가 없습니다"와 CLI `--save` 또는 webhook 설정 안내, PR 목록: "이 저장소에 저장된 PR이 없습니다")를 보여 준다
6. IF URL의 `page`가 0으로 시작하지 않는 1 이상 2,147,483,647 이하의 10진 정수가 아니거나 두 번 이상 있으면, THEN THE Web_App SHALL URL을 바꾸지 않고 `page` 1로 Query_API를 호출하고 페이지 이동 링크를 1페이지 기준으로 만든다
7. WHEN URL의 `page`가 전체 페이지 수보다 크고 전체 항목이 1개 이상이면, THE Web_App SHALL 빈 표 대신 "해당 페이지가 없습니다" 안내와 1페이지 링크를 보여 준다

### Requirement 11: PR 상세 화면 (FR-13)

**User Story:** 리뷰어로서, PR의 리뷰 실행 이력과 실행별 지적을 근거와 함께 보고 싶다. 그래야 지적을 반영할지 판단하고 GitHub의 해당 코멘트로 바로 이동한다.

#### Acceptance Criteria

1. THE Web_App SHALL PR 상세 화면 상단에 `owner/repo`, PR 번호, 제목, 마지막으로 본 head SHA 앞 7자, GitHub PR 링크(요구사항 10.2 형식)를 보여 준다
2. THE Web_App SHALL 리뷰 실행 이력 표에 Review_Run마다 ID, head SHA 앞 7자, Run_Trigger, Run_Status, 시도 번호, Review_Mode, 완전성 상태, 입력·출력·캐시 쓰기·캐시 읽기 토큰 수, 추정 비용, 등록 시각을 Query_API 순서대로 보여 주고, 행마다 해당 Review_Run을 선택하는 링크를 두고, 선택한 Review_Run의 행에는 "선택됨" 텍스트와 `aria-current="true"`를 붙인다
3. THE Web_App SHALL 선택한 Review_Run을 URL 쿼리 변수 `run`으로 지정하고, `run`이 없으면 `runsPage` 값과 관계없이 PR 한 건 응답(요구사항 2.3)의 마지막 Review_Run ID를 선택한다
4. IF URL의 `run`이 Review_Run ID 형식이나 범위를 벗어나거나, `GET /api/runs/{runId}` 응답의 저장소 owner·이름(대소문자 무시)이나 PR 번호가 URL과 다르거나, Query_API가 해당 Review_Run에 404를 반환하면, THEN THE Web_App SHALL HTTP 상태 404와 함께 404 화면(요구사항 15.7)을 보여 준다
5. THE Web_App SHALL 선택한 Review_Run 영역에 Run_Status, `summary`, Summary_Comment_Link(Summary_Comment ID가 있을 때), 단계별 시각(webhook 수신, 등록, 실행 시작, 리뷰 완료, 게시 완료, 종료)을 보여 주고, null인 시각과 `summary`는 "없음"으로 보여 준다
6. THE Web_App SHALL 선택한 Review_Run의 지적 표에 Stored_Finding마다 심각도, 카테고리, 파일과 줄(Code_Link), 메시지, 제안(있을 때), 근거, 라인 판정과 Summary_Only 사유, Publish_Outcome, Feedback_State, Line_Comment_Link(Line_Comment ID가 있을 때)를 Query_API 순서대로 보여 준다
7. WHEN Finding의 `basis.type`이 `rule` 또는 `spec`이면, THE Web_App SHALL 근거 칸에 근거 종류와 `basis.ref`를 선택한 Review_Run의 base SHA 기준 Basis_Link로 보여 주고, base SHA가 null이면 `basis.ref`를 링크 없는 텍스트로 보여 준다
8. WHEN Finding의 `basis.type`이 `general`이면, THE Web_App SHALL 근거 칸에 링크 없이 "일반 원칙"을 보여 주고, Demotion_Record가 있으면 "강등됨"과 원래 `type`, 원래 `ref`를 텍스트로 함께 보여 준다
9. THE Web_App SHALL 리뷰 실행 이력과 지적 표의 페이지를 URL 쿼리 변수 `runsPage`, `findingsPage`로 각각 지정하고 요구사항 10.4와 10.6을 적용하며, 페이지 이동 링크는 다른 쿼리 변수를 유지하고, Review_Run 선택 링크는 `runsPage`를 유지하고 `findingsPage`를 뺀다
10. WHEN 선택한 Review_Run의 Stored_Finding이 0개이면, THE Web_App SHALL 지적 표 대신 Run_Status에 맞는 Empty_State(`succeeded`·`incomplete`·`superseded`: "지적이 없습니다", `failed`: "리뷰 결과가 없습니다", `queued`·`running`: "리뷰가 진행 중입니다")를 보여 준다
11. THE Web_App SHALL Basis_Link, Code_Link, Line_Comment_Link, Summary_Comment_Link, GitHub PR 링크를 `https://github.com/` 으로 시작하는 URL로만 만들고, `owner`, `repo`, 파일 경로와 `basis.ref`의 각 경로 구간을 퍼센트 인코딩한다
12. THE Web_App SHALL 지적 표의 라인 판정 칸에 Inline_Eligible은 "라인 코멘트 대상", Summary_Only는 "요약 전용"이라는 한국어 레이블로 보여 준다
13. FOR ALL Normalized_Repo_Path P(한글, 공백, `#`, `?`, `%` 포함), Basis_Link와 Code_Link의 경로 부분을 구간별로 퍼센트 디코딩한 결과 SHALL P와 같다 (round-trip 속성)

### Requirement 12: 실행 상태 표시 (FR-13, 신뢰성)

**User Story:** 리뷰어로서, 불완전하거나 실패했거나 대체된 리뷰를 정상 결과로 오해하지 않기를 원한다. 그래야 "지적 없음"을 잘못 믿지 않는다.

#### Acceptance Criteria

1. THE Web_App SHALL Status_Label 종류마다 모든 값에 서로 다른 한국어 텍스트를 쓴다: Run_Status 여섯 값(`queued`, `running`, `succeeded`, `incomplete`, `failed`, `superseded`), 완전성 상태의 모든 값, Feedback_State 네 값(`adopted`, `rejected`, `conflicted`, `none`), Publish_Outcome의 모든 값
2. WHEN 선택한 Review_Run이 `incomplete`이면, THE Web_App SHALL 선택한 Review_Run 영역 맨 앞에 불완전 경고, 모든 Incomplete_Reason, 불완전 Chunk 파일 경로 목록을 보여 준다 (P1 요구사항 16.3, P2 요구사항 11.4와 같은 정보)
3. WHEN 선택한 Review_Run이 `failed`이면, THE Web_App SHALL 선택한 Review_Run 영역 맨 앞에 실패 표시, 오류 종류, 치환된 오류 메시지를 보여 준다
4. WHEN 선택한 Review_Run이 `superseded`이면, THE Web_App SHALL "새 커밋으로 대체되어 PR에 게시되지 않음" 안내와 해당 Review_Run의 head SHA 앞 7자를 보여 준다
5. WHEN 선택한 Review_Run에 Publish_Error가 있으면, THE Web_App SHALL 게시 오류 종류와 GitHub 상태 코드를 보여 주고, 상태 코드가 null이면 "없음"을 보여 준다
6. WHEN 선택한 Review_Run의 Review_Mode가 요약 전용 모드이면, THE Web_App SHALL "PR을 나누세요" 안내와 Changed_Line_Count를 보여 준다
7. WHEN Review_Run의 추정 비용이 null이면, THE Web_App SHALL 리뷰 실행 이력 표와 선택한 Review_Run 영역의 비용 칸에 "알 수 없음"을 보여 준다
8. THE Web_App SHALL 추정 비용을 `$` 접두어와 함께 USD 소수점 다섯째 자리에서 반올림(half up)해 항상 소수점 네 자리로 표시한다 (예: `$0.0123`)
9. THE Web_App SHALL Status_Label, 심각도, Feedback_State를 텍스트로 표시하고 색만으로 구분하지 않는다

### Requirement 13: 통계 화면 (FR-14)

**User Story:** 연구회 팀으로서, 규칙별 지적, 심각도 분포, 채택률, 비용 추이를 한 화면에서 보고 싶다. 그래야 5주차 발표 지표를 바로 확인한다.

#### Acceptance Criteria

1. THE Web_App SHALL `/stats` 화면에 규칙별 지적 상위 N(요구사항 4), 심각도별 분포(요구사항 5.1), 채택률(요구사항 5.3), 비용 추이(요구사항 6) 네 영역을 이 순서로, 영역마다 `<h2>` 제목과 함께 보여 준다
2. THE Web_App SHALL 통계 화면의 필터(저장소, `from`, `to`, 상위 N, `bucket`)를 URL 쿼리 변수로 유지하고, 필터를 `GET` 방식의 form으로 제출해 JavaScript 없이도 필터를 적용할 수 있게 하며, 저장소 필터는 `owner/repo`를 입력하는 자유 텍스트 입력(빈 값은 모든 저장소)으로 둔다
3. THE Web_App SHALL 필터 입력란을 Stats_API가 실제로 적용한 값(요구사항 3.8)으로 채워 화면에 표시하고, 기간이 UTC 날짜 기준임을 함께 표시한다
4. THE Web_App SHALL 규칙별 지적 영역에 Rule_Stat마다 순위, 근거 종류, `basis.ref`, 지적 수를 막대 차트와 Chart_Data_Table로 보여 주고, `repository` 필터가 있으면 `basis.ref`에 `https://github.com/{owner}/{repo}/blob/HEAD/{basis.ref}` 링크(기본 브랜치 기준, 요구사항 11.11의 인코딩 규칙)를 단다
5. THE Web_App SHALL 심각도 영역에 `blocker`·`major`·`minor`·`nit` 순서의 개수를 차트와 Chart_Data_Table로 보여 준다
6. THE Web_App SHALL 채택률 영역에 Adoption_Rate를 백분율로 소수점 둘째 자리에서 반올림(half up)해 소수점 첫째 자리까지(예: 0.12345 → `12.3%`), 분자와 분모, `conflicted`·`none` 개수, Feedback_Target이 아닌 Stored_Finding 수를 보여 주고, 수기 기록(PLAYBOOK 5장)은 포함하지 않았다는 안내를 붙인다
7. WHEN Adoption_Rate가 null이면, THE Web_App SHALL 채택률 대신 "채택/기각 리액션이 아직 없습니다"를 보여 준다
8. THE Web_App SHALL 비용 추이 영역에 Cost_Bucket별 추정 비용 합과 PR당 비용을 차트와 Chart_Data_Table로 보여 주고, 1회 리뷰 비용 상한과 상한을 넘은 Cost_Run 수를 표시하며, 금액은 요구사항 12.8 형식으로, null인 PR당 비용은 "없음"으로 보여 준다
9. THE Web_App SHALL 통계 값(개수, 비율, 합계, PR당 비용)을 Stats_API 응답 값 그대로 표시하고 Web_App에서 다시 계산하지 않는다 (표시용 백분율 변환과 자릿수 서식 제외)
10. IF 영역의 집계 대상 개수(규칙별·심각도·채택률 영역은 심각도 응답(요구사항 5.1)의 전체 Stored_Finding 수, 비용 추이 영역은 기간 전체 Cost_Run 수)가 0이면, THEN THE Web_App SHALL 해당 영역의 차트와 표 대신 Empty_State("선택한 기간에 리뷰 데이터가 없습니다")를 보여 준다
11. IF Stats_API가 필터 값 때문에 400 또는 404를 반환하면, THEN THE Web_App SHALL 통계 영역 대신 문제 필터 이름을 담은 안내와 기본 필터 링크(`/stats`)를 보여 준다
12. THE Web_App SHALL Stats_Filter를 네 통계 엔드포인트 모두에 보내고, `top`은 `GET /api/stats/rules`에만, `bucket`은 `GET /api/stats/cost-trend`에만 보낸다
13. IF 네 Stats_API 호출 중 하나라도 요구사항 15.5의 실패에 해당하면, THEN THE Web_App SHALL 일부 영역만 표시하지 않고 화면 전체를 Error_Screen으로 보여 준다

### Requirement 14: 차트 접근성 (FR-14, 접근성)

**User Story:** 키보드나 스크린 리더를 쓰는 팀원으로서, 차트의 값을 텍스트로 확인하고 싶다. 그래야 시각 차트를 보지 못해도 같은 정보를 얻는다.

#### Acceptance Criteria

1. THE Web_App SHALL 차트마다 같은 값을 담은 Chart_Data_Table을 차트와 같은 `<h2>` 영역 안에 두고, Chart_Data_Table을 보조 기술에 노출한다
2. THE Web_App SHALL 차트 요소에 차트 제목과 핵심 값 요약을 담은 텍스트 대체(`aria-label` 또는 `<figcaption>`)를 붙인다: 규칙별 차트는 항목 수, 1위 `basis.ref`와 지적 수, 심각도 차트는 네 심각도의 개수, 비용 추이 차트는 Cost_Bucket 수와 기간 전체 추정 비용 합
3. THE Web_App SHALL 막대마다 보이는 범주 레이블(규칙별: `basis.ref`, 심각도: 심각도 이름, 비용 추이: Cost_Bucket 시작 날짜)을 붙이고, 계열이 둘 이상인 차트에는 계열 이름을 텍스트로 담은 범례를 둔다
4. FOR ALL 통계 응답, Chart_Data_Table의 행 SHALL 응답 항목과 하나씩 대응하고 값이 같으며, 값이 null인 데이터 점은 표에 "없음"으로 표시되고 차트에 막대가 그려지지 않는다 (일관성 속성)

### Requirement 15: 공통 접근성과 상태 화면 (접근성, 신뢰성)

**User Story:** 팀원으로서, 키보드만으로 모든 화면을 쓰고 로딩·오류 상태를 명확히 알고 싶다. 그래야 backend가 멈춰도 화면이 깨지지 않고 원인을 안다.

#### Acceptance Criteria

1. THE Web_App SHALL 목록과 지적을 `<table>`, `<caption>`, `scope` 속성을 가진 `<th>`로 된 표로 보여 준다
2. THE Web_App SHALL 모든 링크, 버튼, form 요소를 Tab 키로 문서 순서대로 도달하게 하고, 포커스된 요소에 두께 2px 이상, 인접 색과 명도 대비 3:1 이상의 포커스 표시를 둔다
3. THE Web_App SHALL 모든 화면의 첫 번째 Tab 대상으로 `<main>` 요소로 이동하는 본문 건너뛰기 링크를 두고, 포커스를 받으면 화면에 보이게 한다
4. WHILE 화면 데이터를 불러오는 동안, THE Web_App SHALL `role="status"`를 가진 로딩 표시를 보여 준다
5. IF Query_API 연결이 실패하거나, 10초 안에 응답하지 않거나, 5xx를 반환하면, THEN THE Web_App SHALL 프로세스를 중단하지 않고 Error_Screen을 보여 주고, 오류 원인(연결 실패, 시간 초과, 서버 오류)과 쿼리 변수를 포함한 현재 URL을 다시 요청하는 "다시 시도" 링크를 담는다
6. THE Web_App SHALL Error_Screen에 스택 트레이스, 내부 URL(Backend_Base_URL 포함), Query_API 응답 원문을 포함하지 않고, 서버 로그에 오류 원인과 요청 경로를 남긴다
7. WHEN Query_API가 404를 반환하면, THE Web_App SHALL HTTP 상태 404와 함께 대상(저장소, PR, Review_Run)을 찾을 수 없다는 404 화면과 저장소 목록 링크를 보여 주고, 요구사항 9.7과 11.4의 404 화면도 같은 화면을 쓴다
8. IF Query_API 응답이 API_Types와 맞지 않으면(JSON 해석 실패, 필수 필드 누락), THEN THE Web_App SHALL Error_Screen을 보여 주고 응답 불일치를 서버 로그에 남긴다
9. THE Web_App SHALL 요구사항 11.5의 단계별 시각을 제외한 시각을 D-6에서 정한 시간대(제안값 `Asia/Seoul`)로 `YYYY-MM-DD HH:mm` 형식과 시간대 표기(예: `KST`)를 붙여 보여 주고, 값이 null이면 "-"를 보여 준다 (요구사항 11.5의 단계별 시각은 같은 형식에 null이면 "없음")
10. IF 저장소 목록, PR 목록, PR 상세 화면의 Query_API 호출이 404 외의 4xx를 반환하면, THEN THE Web_App SHALL 오류 원인 "요청 오류"를 담은 Error_Screen을 보여 주고 서버 로그에 응답 상태 코드와 요청 경로를 남긴다

### Requirement 16: 조회 전용 구조와 보안 (FR-13, FR-14, 보안)

**User Story:** 팀원으로서, 웹이 데이터를 바꾸거나 리뷰 결과 속 문자열로 공격받지 않기를 원한다. 그래야 인증 없는 로컬 데모 환경에서도 안전하게 쓴다.

#### Acceptance Criteria

1. THE Web_App SHALL Query_API를 Server Component와 서버 측 코드에서만 API_Client로 호출하고, 브라우저에서 Query_API를 직접 호출하지 않는다
2. THE Web_App SHALL Backend_Base_URL을 서버 측 환경변수 `PRLENS_API_BASE_URL`에서만 읽고, Backend_Base_URL 값과 환경변수 이름을 브라우저로 보내는 JavaScript 번들과 HTML 응답에 포함하지 않는다
3. THE Web_App SHALL `web/src/lib/api/` 밖의 모듈이 `fetch`나 `PRLENS_API_BASE_URL`을 참조하면 `npm run verify`의 lint 검사가 실패하게 한다
4. THE Web_App SHALL Query_API의 `GET` 엔드포인트만 호출하고, 데이터를 바꾸는 화면 요소(채택/기각 변경, 재실행, 삭제), Server Action, `POST` form, `GET` 외 메서드를 처리하는 route handler를 두지 않는다
5. THE Web_App SHALL 화면 요청마다 Query_API를 캐시 없이 조회해, 새로고침하면 그 시점의 저장 데이터를 보여 준다
6. THE Web_App SHALL `summary`, `message`, `suggestion`, 오류 메시지, PR 제목, `basis.ref`, 파일 경로를 HTML이나 Markdown으로 해석하지 않고 이스케이프한 텍스트로 보여 주고, 줄바꿈만 유지한다 (D-7)
7. THE Web_App SHALL `GITHUB_TOKEN`, `ANTHROPIC_API_KEY`, `GITHUB_WEBHOOK_SECRET`, `GITHUB_APP_PRIVATE_KEY`, `PRLENS_DB_PASSWORD`를 읽지 않는다
8. THE Web_App SHALL 기본 바인딩 주소 `127.0.0.1`에서 요청을 받고, 시작 방법을 담은 문서에 Web_App과 Query_API가 인증 없이 동작한다는 경고를 적는다 (P2 요구사항 10.11, D-8과 같은 기준)
9. THE Web_App SHALL `npm run verify`(lint, 타입 검사, 테스트, Type_Contract_Check 포함)를 CI 필수 체크 `web`으로 통과한다
10. IF `PRLENS_API_BASE_URL`이 `http` 또는 `https` 스킴의 절대 URL이 아니면, THEN THE Web_App SHALL Query_API를 호출하지 않고 Error_Screen을 보여 주고, 서버 로그에 환경변수 이름과 형식 오류를 남기되 환경변수 값은 남기지 않는다
11. FOR ALL 문자열 S(`<script>`, HTML 태그, `javascript:` URL, Markdown 링크 포함)와 요구사항 16.6의 모든 필드, S를 해당 필드로 표시한 화면 HTML SHALL S에서 온 실행 가능한 요소, 이벤트 속성, `javascript:` 링크를 포함하지 않고, 화면 텍스트는 줄바꿈을 제외하고 S와 코드 포인트 단위로 같다 (불변 속성)

### Requirement 17: 로컬 실행과 완료 조건 (ROADMAP 완료 조건)

**User Story:** 4주차 기능 리드로서, 팀원 누구나 로컬에서 BE와 web을 띄워 실제 리뷰 데이터를 보기를 원한다. 그래야 금요일 데모와 5주차 발표를 같은 방법으로 준비한다.

#### Acceptance Criteria

1. THE Web_App SHALL DB(Docker Compose), PR_Lens_Server, Web_App을 로컬에서 띄우는 명령과 필요한 환경변수 이름(값 제외)을 설치 문서에 적는다
2. WHEN 저장소 1개 이상, Review_Run이 1개 이상인 PR, `basis.type`이 `rule` 또는 `spec`인 Stored_Finding, Line_Comment ID가 있는 Stored_Finding을 포함해 3주차부터 쌓인 리뷰 데이터가 있는 DB로 PR_Lens_Server와 Web_App을 로컬에서 실행하면, THE Web_App SHALL 화면의 링크만으로 저장소 목록 → PR 목록 → PR 상세(리뷰 실행 이력, 지적 목록, Basis_Link, Line_Comment_Link) → 통계 화면까지 이동하는 동안 Error_Screen과 404 화면 없이 각 화면을 보여 준다
3. THE Web_App SHALL Windows, macOS, Linux에서 같은 `npm` 명령으로 설치, 실행, `npm run verify`를 수행할 수 있게 한다 (P1 요구사항 22와 같은 기준)
4. THE Web_App SHALL API_Types를 따르는 테스트 픽스처(Query_API 응답 샘플: 빈 목록, 여러 페이지, 여섯 Run_Status, Chunk 파일 경로가 있는 `incomplete`, 강등된 Finding, Publish_Error, 요약 전용 모드, null 추정 비용, 한글·공백·`#`·`?`·`%`를 포함한 경로, 요구사항 16.11의 공격 문자열, 0개 통계)를 포함하고, 픽스처의 비밀값과 개인정보를 가짜 값으로 둔다
5. WHILE Review_Store에 Review_Run 10,000개와 Stored_Finding 100,000개가 있고 Web_App을 프로덕션 빌드(`npm run build` 후 `npm run start`)로 실행한 동안, THE Web_App SHALL 로컬 환경에서 각 화면(PR 상세는 Finding 100개인 Review_Run 선택)의 HTML 응답을 요청 전송부터 마지막 바이트 수신까지 3초 이내에 완료한다 (워밍업 1회 제외, 화면마다 5회 측정 모두 충족해야 통과)

## 결정 대기 항목

PRD가 정하지 않은 항목은 설정 가능하거나 교체 가능하게 두고, 결정 전까지 아래 기본값으로 진행합니다. 결정되면 이 문서와 OpenAPI 문서를 함께 고칩니다.

| ID | 항목 | 결정 주체 | 이 스펙의 처리 |
|---|---|---|---|
| D-1 | 페이지네이션 방식 | 4주차 T2 스펙 검토 | 제안: `page`/`size`(offset, `size` 최대 100, 기본 20). 조회 중 데이터가 추가되면 페이지 사이에 항목이 밀려 중복·누락될 수 있음(요구사항 1.10은 변경 없는 경우만 보장). 대안은 Sort_Key 기반 cursor. 데이터가 적고 실시간 갱신이 범위 밖이라 offset 제안 |
| D-2 | P2 요구사항 10.5 응답 형식 변경 | 4주차 킥오프 | P2 초안의 "최대 100개 + 전체 수"를 Page_Response로 바꿈. P3 화면 외 소비자가 없으므로 버전 없이 교체 |
| D-3 | 지적 통계에 포함할 Review_Run (P2 D-10) | 4주차 T2·PLAYBOOK 지표 담당 | 제안: `succeeded`, `incomplete`만(Counted_Run). `superseded`는 게시되지 않았고 같은 PR의 다음 Review_Run과 지적이 겹치므로 제외. 한계: 같은 head SHA를 CLI `--save`와 webhook이 모두 리뷰하면 두 번 집계됨. 대안은 Dedupe_Key별 최신 Review_Run만 집계 |
| D-4 | 채택률 분모 (P2 D-10) | 4주차 T2·PLAYBOOK 지표 담당 | 제안: Feedback_Target은 Publish_Outcome `line_comment_posted`만(`line_comment_reused`, `duplicate_in_run`은 같은 Line_Comment를 두 번 세지 않도록 제외), Adoption_Rate = adopted ÷ (adopted + rejected). `conflicted`와 `none`은 개수만 표시. Summary_Only·하한 미만 지적은 PLAYBOOK 수기 기록으로 집계(P2 요구사항 15.8) |
| D-5 | 통계 기본 기간 | 4주차 T3 스펙 검토 | 제안: 최근 30일(UTC 날짜 기준), 최대 366일. 발표용으로는 3주차 시작일부터 지정 |
| D-6 | 화면 시각 표시 시간대 | 4주차 킥오프 | 제안: 시각은 `Asia/Seoul`(KST 표기), 통계 기간과 Cost_Bucket은 UTC 날짜(화면에 UTC 표기). 두 기준이 섞이는 한계는 도그푸딩 후 재검토 |
| D-7 | `summary`·`message`·`suggestion` Markdown 렌더링 | 4주차 T1 스펙 검토 | 제안: 이스케이프한 평문 + 줄바꿈 유지(요구사항 16.6). LLM 출력은 diff 속 지시문 영향을 받을 수 있어 신뢰하지 않는 입력으로 다룸. Markdown이 필요하면 raw HTML과 `javascript:` 링크를 막는 렌더러를 별도 결정 |
| D-8 | Web_App 외부 노출 | 4주차 킥오프 | P2 D-8과 같이 `127.0.0.1` 바인딩 기본. 데모 환경에서 외부 노출이 필요하면 인증 도입과 함께 별도 결정 |
| D-9 | 차트 라이브러리 | 4주차 T3 | 설계 단계에서 결정(후보: 서버에서 그리는 SVG 직접 구현, 경량 차트 라이브러리). 조건: 서버 렌더링 가능, 요구사항 14의 텍스트 대체 지원, 의존성 버전 고정 |
| D-10 | 통계 규칙 링크의 기준 커밋 | 4주차 T3 | 제안: 통계는 여러 base SHA에 걸치므로 `blob/HEAD` 기본 브랜치 기준 링크(요구사항 13.4, API 변경 불필요). 규칙 파일이 삭제·이동되면 링크가 404가 되는 한계. PR 상세의 Basis_Link는 Review_Run의 base SHA 기준(요구사항 11.7) |
| D-11 | Type_Contract_Check 구현 방식 | 4주차 T2 | 설계 단계에서 결정(후보: OpenAPI에서 타입을 생성해 `types.ts`와 비교, 또는 `types.ts`를 생성 파일로 전환). 어느 방식이든 API PR만 `types.ts`를 바꾸는 규칙은 유지 |
| D-12 | 비용 상한 초과 판정 기준 | 4주차 T2 스펙 검토 | 제안: 요청 시점 Configuration의 1회 리뷰 비용 상한으로 판정(요구사항 6.6, 6.7, 저장 스키마 변경 불필요). 한계: 과거 Review_Run 실행 당시 상한과 다를 수 있어 상한을 바꾸면 과거 구간의 초과 수도 바뀜. 대안은 실행 당시 상한을 Review_Run에 저장(저장 스키마 변경 필요, P3 범위 밖). 유지 여부 결정 필요 |
