# 06. P3 요구사항

[← 목차](README.md)

대상: `claude-team-prlen/docs/specs/pr-lens-p3-web/requirements.md`(335줄, 검토 당시 위치는 바깥쪽 `.kiro/specs/`). P3는 `design.md`와 `tasks.md`가 아직 없어서 요구사항만 검토했습니다. 이 문서에서 `P3-req`는 이 파일을 가리킵니다.

검토는 세 범위로 나눠 진행했습니다.

| 범위 | 판정 | 지적 수 |
|---|---|---|
| [A. 요구사항 자체](#a-요구사항-자체) (PRD 커버리지, 모호함, 모순, 통계 정의) | 작은 수정 후 설계 가능 | 치명 4, 중요 12, 경미 9 |
| [B. P1·P2·저장소 정합성](#b-p1p2저장소-정합성) | P1·P2 요구사항과는 대체로 일치, P2 설계·저장소와는 여러 곳 불일치 | 치명 1, 중요 13, 경미 8 |
| [C. 외부 기술 사실](#c-외부-기술-사실) | 24건 중 맞음 11 | 틀림 1, 부분적으로 맞음 12 |

## 결론

**작은 수정 후 설계에 들어갈 수 있습니다.** 지금 그대로는 안 됩니다.

- PRD의 FR-12~14는 빠짐없이 덮여 있고, 문서 안의 번호 참조도 모두 실재하는 항목을 가리킵니다. backend 쪽(요구사항 1~7)은 통계의 분자, 분모, 기간, 0 분모 처리까지 적혀 있어 거의 그대로 설계할 수 있습니다.
- 막히는 곳은 화면과 API가 만나는 지점, 그리고 Next.js 16의 동작과 충돌하는 요구입니다.
- P1·P2 검토에서 나온 두 문제(에러 형식 미정, `/api/v1` 없는 경로)를 그대로 반복합니다. "스타터가 없다"는 가정은 반복하지 않았습니다.
- P3는 "저장 스키마를 바꾸지 않는다"고 하면서 P2 설계와 공유 인터페이스에 생기는 변경은 적지 않았습니다.

### 세 검토에서 겹친 지적

| 지적 | 나온 곳 |
|---|---|
| 로딩 표시와 HTTP 404를 동시에 요구 (Next.js는 스트리밍이 시작되면 상태 코드가 200으로 굳음) | A-4, B-7, C-1 |
| 오류 화면에서 원인을 구분해 표시 (`error.tsx` 방식으로는 프로덕션에서 불가) | A-14, B-8, C-2 |
| 오류 본문 형식이 정해지지 않음 | A-13, B-1 |
| 웹 바인딩 주소 `127.0.0.1` (Next.js CLI 기본값은 `0.0.0.0`) | B-11, C-4 |
| 금액의 JSON 표현과 반올림 | A-12, C-6, C 누락 9 |
| 테스트·검증 도구가 설치되어 있지 않음 | B-12, C 설치 버전 |

---

## A. 요구사항 자체

대조한 문서: `docs/product/PRD.md`, `ROADMAP.md`, `PLAYBOOK.md` 전체.

### 총평

- 통계 정의에 구멍이 둘 있습니다. 같은 PR의 연속 실행이 규칙별·심각도 통계에 중복 집계되고, PRD 성공 지표인 근거 연결률이 화면에 나오지 않습니다.
- 월요일에 `types.ts`를 먼저 머지하려면 D-3, D-4, D-12와 아래 1~8번이 킥오프 전에 확정되어야 합니다.
- 1주 분량으로는 T2에 부하가 쏠려 있습니다(수용 기준 149개 중 70개).

### 치명

**A-1. [모순] 빈 필터 제출이 400이 됩니다.**
- 위치: `P3-req:246`(13.2) ↔ `:91`(3.4), `:92`(3.5)
- 문제: JS 없는 GET form은 빈 입력도 `repository=&from=&to=`로 보냅니다. 13.2는 "빈 값은 모든 저장소"인데, 3.5는 빈 `repository`를 문자 규칙 위반 400으로, 3.4는 빈 날짜를 400으로 처리합니다. 빈 값을 누가 버리는지 없습니다.
- 수정안: 13.12에 "Web_App은 빈 문자열 필터를 Stats_API에 보내지 않는다"를 추가하거나, 3.x에 "빈 값은 없는 것으로 처리"를 추가합니다.

**A-2. [모순] 404 처리 규칙이 서로 충돌합니다.**
- 위치: `P3-req:282`(15.7) ↔ `:255`(13.11)
- 문제: 15.7은 조건 없이 "Query_API가 404면 404 화면"입니다. 13.11은 통계에서 저장소 없음(3.6의 404)이면 404 화면이 아닌 필터 안내를 요구합니다. 13.11의 HTTP 상태도 없습니다.
- 수정안: 15.7을 "저장소 목록·PR 목록·PR 상세 화면에서"로 한정하고, 13.11에 응답 상태(예: 200)를 명시합니다.

**A-3. [누락] PR 상세의 페이지 크기와 범위 밖 페이지 처리가 없습니다.**
- 위치: `P3-req:217`(11.9), `:197`(10.3), `:201`(10.7)
- 문제: 11.9는 10.4와 10.6만 적용합니다. 10.3(`size` 20)과 10.7(범위 밖 페이지)은 빠져서, PR 상세의 실행 이력·지적 표 페이지 크기와 `runsPage`/`findingsPage`가 전체 페이지보다 클 때의 동작이 없습니다. 17.5의 "Finding 100개인 Review_Run"이 한 페이지 100개인지도 알 수 없습니다.
- 수정안: 11.9에 `size`(예: 이력 20, 지적 50)와 10.7 적용 여부를 명시합니다.

**A-4. [모호] 로딩 표시, HTTP 404, "JS 없이 동작"을 동시에 요구합니다.**
- 위치: `P3-req:279`(15.4) ↔ `:187`(9.7), `:212`(11.4), `:282`(15.7), `:246`(13.2)
- 문제: Next.js는 `loading.tsx`/Suspense로 스트리밍을 시작한 뒤에는 상태 코드를 못 바꾸고, 스트리밍된 본문 교체에 JS가 필요합니다. 팀 규칙 `.claude/rules/frontend/nextjs.md:11`은 `loading.tsx` 우선입니다.
- 수정안: 셋 중 우선순위를 정합니다. 예: "404 판정에 필요한 조회는 스트리밍 전에 끝낸다" 또는 15.4를 "클라이언트 전환 중"으로 한정.
- 참고: [C-1](#틀리거나-부분적으로-맞는-주장)에서 Next.js 번들 문서로 확인했습니다.

### 중요

**A-5. [누락] 같은 PR의 연속 실행이 통계에 중복 집계됩니다.**
- 위치: `P3-req:325`(D-3), `:31`(Counted_Run), `:107`(4.1), `:124`(5.1)
- 문제: 한 PR에 커밋이 추가될 때마다 `succeeded` 실행이 새로 생기고 같은 지적이 다시 나옵니다. 규칙별·심각도 통계는 Counted_Run의 모든 Stored_Finding을 세므로 커밋이 많은 PR이 순위를 부풀립니다. D-3은 CLI와 webhook 중복만 한계로 적었고, `superseded`를 뺀 이유("다음 실행과 겹침")는 연속 `succeeded`에도 똑같이 해당합니다.
- 수정안: D-3에 선택지를 명시합니다. (a) 실행 단위 그대로 두고 화면에 "실행 기준, 중복 포함" 표기, (b) PR별 최신 Counted_Run만, (c) Finding_Fingerprint로 PR 안 중복 제거.

**A-6. [누락] PRD 성공 지표 "근거 연결률"이 화면에 없습니다.**
- 위치: `P3-req:111`(4.5), `:248`(13.4), `:253`(13.9), PRD 2장
- 문제: 4.5가 전체·`general`·강등 수와 조합 수를 반환하지만 13.4는 이를 표시하지 않고, 13.9와 `:9`가 web 계산을 금지하므로 비율을 만들 수도 없습니다.
- 수정안: 4.5에 근거 연결률(분자 = rule+spec 수, 분모 = 전체, 0이면 null)을 추가하고 13.4에 표시 항목을 추가합니다. 뺄 거라면 범위 밖에 명시합니다.

**A-7. [모순] 비용 상한 초과 수의 집계 단위가 조항마다 다릅니다.**
- 위치: `P3-req:98`(3.11) ↔ `:145`~`:146`(6.6, 6.7), `:252`(13.8)
- 문제: 3.11은 상한 초과 Cost_Run 수와 토큰 합을 "기간 전체 값과 Cost_Bucket별 값"으로 다룹니다. 6.7은 초과 수를 Cost_Bucket별로만, 6.6은 기간 전체에 초과 수·토큰 합·null 비용 수를 담지 않습니다. 13.8의 "상한을 넘은 Cost_Run 수"가 전체인지 구간별인지 불명이고, 전체라면 web이 합산해야 해 13.9에 어긋납니다.
- 수정안: 6.6에 기간 전체 초과 수, 토큰 합, null 비용 Cost_Run 수를 추가하고 13.8에 표시 단위를 명시합니다.

**A-8. [누락] 적용된 `top`, `bucket` 값이 응답에 없습니다.**
- 위치: `P3-req:247`(13.3) ↔ `:95`(3.8), `:109`(4.3), `:141`(6.2)
- 문제: 13.3은 필터 입력란을 "실제 적용한 값"으로 채우라는데, 3.8은 `from`/`to`/`repository`만 돌려줍니다. `top`과 `bucket`은 응답에 없어 web이 기본값 10과 `day`를 따로 알아야 합니다. 400일 때 입력란을 무엇으로 채우는지도 없습니다.
- 수정안: 4.5와 6.6 응답에 적용된 `top`, `bucket`을 추가합니다. 13.11에 "입력란은 URL 값 그대로"를 명시합니다.

**A-9. [모순] FR-14를 뺄 때 빠지는 조항이 정확하지 않습니다.**
- 위치: `P3-req:12` ↔ `:158`·`:160`(7.2, 7.4), `:182`~`:185`(9.2, 9.3, 9.5), `:312`(17.2), `:314`(17.4), `:168`(8.1)
- 문제: FR-14를 뺄 때 요구사항 3~7과 13~14를 뺀다고 하지만, 7.2~7.4는 목록 API(요구사항 1~2)의 성능·500 응답·동등성도 담습니다. `/stats` 라우트, 헤더 "통계" 링크, 17.2의 "→ 통계 화면", "0개 통계" 픽스처, 8.1의 통계 스키마는 남습니다.
- 수정안: 축소 시 바뀌는 조항을 조항 단위로 나열합니다. 7.2~7.4는 "남은 엔드포인트에 적용"으로 유지합니다.

**A-10. [모호] 공통 요구사항 15~17의 소유자가 "전원"입니다.**
- 위치: `P3-req:10`, 요구사항 15~17, `:22`(API_Client)
- 문제: API_Client 함수(`web/src/lib/api/`), Error_Screen, 404 화면, 로딩, 건너뛰기 링크, 시각·금액 서식(12.8은 T1 조항인데 13.8에서 T3가 사용), 픽스처(17.4), lint 규칙(16.3), 설치 문서(17.1)를 누가 언제 머지하는지 없습니다. ROADMAP `:18`의 "같은 모듈을 두 트랙이 건드리지 않는다"와 어긋납니다.
- 수정안: 조항별 소유자 표를 추가합니다. 공용 화면·서식·`http.ts`는 월요일 골격 PR(T1), 통계 호출 함수는 T2 또는 T3로 못박습니다.

**A-11. [범위 초과] T2 한 명에게 분량이 쏠려 있습니다.**
- 위치: `P3-req:10`, `:158`(7.2), `:160`(7.4), `:168`(8.1), `:170`(8.3), `:79`(2.6), `:315`(17.5)
- 문제: T2(A 한 명)가 요구사항 1~8, 수용 기준 70개를 맡습니다(T1 36개, T3 17개, 공통 26개). 내용은 목록 4종 페이지네이션, 신규 엔드포인트 2개, 통계 4개, HEAD/405, 10만 건 성능 측정, 메모리·DB 동등성, 엔드포인트별 오류 응답 CI 검사, Type_Contract_Check입니다. PRD 8장은 "데모 가능 수준"이고 그 주 A는 리뷰어 역할도 겸합니다.
- 수정안: 7.2, 17.5, 2.6, 8.3을 "선택(동결 전 여유 시)"으로 표시하거나 5주차 안정화로 옮깁니다. FOR ALL 속성 20개 중 필수를 지정합니다.
- 참고: PRD의 "1인당 주 __시간"이 비어 있어 수용 기준 수와 작업 종류에 근거한 추정입니다.

**A-12. [누락] PR당 비용의 나눗셈 규칙과 금액의 JSON 표현이 없습니다.**
- 위치: `P3-req:143`(6.4), `:147`(6.8), `:236`(12.8)
- 문제: 합계는 "정확한 십진 합"이지만 PR당 비용(나눗셈)의 자릿수와 반올림 규칙이 없습니다(Adoption_Rate는 5.5에 있음). 금액을 JSON 숫자로 줄지 문자열로 줄지도 없어, JS가 double로 읽으면 6.8의 "자르지 않는다"가 의미를 잃습니다.
- 수정안: 6.4에 나눗셈 규칙(예: 소수 여섯째 자리 half up)과 JSON 표현을 명시합니다.

**A-13. [모호] 통계 화면의 오류 처리에 빈 곳이 넷 있습니다.**
- 위치: `P3-req:255`(13.11), `:257`(13.13), `:285`(15.10), `:93`(3.6)
- 문제
  - 네 호출 중 `top`만 틀려 하나만 400일 때 "통계 영역 대신"이 전체인지 해당 영역인지 불명입니다.
  - 3.6의 404는 변수 이름을 담는다는 말이 없는데 13.11은 "문제 필터 이름"을 요구합니다.
  - 15.10은 통계 화면을 빼서 통계의 다른 4xx 처리가 없습니다.
  - 날짜 오류(400)와 없는 저장소(404)가 겹칠 때 우선순위가 없습니다(목록은 1.7, 1.8에 있음).
- 수정안: 13.11을 "하나라도 400/404면 네 영역 전체 대신"으로 명시합니다. 3.6에 `repository` 이름을 포함합니다. 3.x에 검사 순서를, 15.10에 통계 화면을 추가합니다.

**A-14. [모호] Error_Screen의 HTTP 상태 코드와 런타임 검증 범위가 없습니다.**
- 위치: `P3-req:280`(15.5), `:283`(15.8), `:302`(16.10)
- 문제: Error_Screen의 HTTP 상태 코드가 없습니다(404 화면은 명시). 15.8의 런타임 검증 범위도 "JSON 해석 실패, 필수 필드 누락"만 예시라 타입 불일치·모르는 열거형 값 처리와 검증 수단(의존성 추가 여부)이 불명입니다.
- 수정안: Error_Screen 상태 코드(예: 500/502/504)와 15.8의 검증 범위를 명시합니다.

**A-15. [누락] 규칙별 영역이 비었을 때의 표시가 없습니다.**
- 위치: `P3-req:254`(13.10)
- 문제: 규칙별 영역의 Empty_State 기준이 심각도 응답의 전체 수입니다. 전체가 1 이상인데 모두 `general`이면 Rule_Stat이 빈 목록인 경우의 표시가 없습니다. 영역이 다른 엔드포인트 값에 의존하기도 합니다(4.5에 자체 전체 수가 있음).
- 수정안: 규칙별 영역은 자기 응답 기준으로 바꾸고 "rule/spec 근거 지적 없음" 문구를 추가합니다.

**A-16. [누락] 설계 전에 정해야 할 것이 결정 대기 표에 없습니다.**
- 위치: 결정 대기 표 `P3-req:321`~`:334`
- 문제: A-3, A-4, A-7, A-8, A-12, A-14는 설계 전에 정해야 하는데 결정 대기 표에 없습니다. D-3, D-4, D-12에는 기한이 없지만 월요일 `types.ts` 선머지(`:11`)가 통계 응답 타입을 필요로 합니다.
- 수정안: D-13 이하로 추가하고, D-3·D-4·D-12 기한을 "4주차 킥오프 전"으로 명시합니다.

### 경미

**A-17. [모호] "마지막 Review_Run"의 정의가 목록과 상세에서 묶여 있지 않습니다.**
- 위치: `P3-req:76`(2.3) ↔ P2 10.2
- 문제: 2.3에서는 Sort_Key 첫 항목으로 정의되지만, PR 목록(P2 10.2)의 상태·시각·심각도 수·정렬에 쓰는 "마지막 Review_Run"은 P3에서 같은 정의로 묶이지 않았습니다. 등록 시각이 같으면 목록과 상세가 다른 실행을 가리킬 수 있습니다.
- 수정안: Glossary에 Latest_Run을 정의하고 양쪽에서 참조합니다.

**A-18. [참조 오류] 시스템 요구사항이 아닌 것이 Web_App을 주어로 적혀 있습니다.**
- 위치: `P3-req:172`(8.5), `:311`(17.1)
- 문제: 주어가 Web_App인데 내용은 PR 템플릿 체크리스트와 설치 문서 작성입니다. `:11`과 `:13`이 스스로 "시스템 요구사항이 아님"으로 분류한 종류와 같습니다.
- 수정안: 주어를 "저장소"로 바꾸거나 Introduction의 프로세스 메모로 옮깁니다.

**A-19. [참조 오류] 접근성 요구의 출처가 PRD가 아닙니다.**
- 위치: `P3-req:5`, 요구사항 14·15 제목
- 문제: "PRD의 비기능 요구사항(보안, 접근성, 오류 처리, 이식성)"이라 하지만 PRD 6장에는 접근성이 없습니다(보안·비용·신뢰성·이식성).
- 수정안: 출처를 팀 frontend 규칙 등으로 고치거나 "P3 추가 요구"로 표기합니다.

**A-20. [누락] Glossary에 없는 용어가 있습니다.**
- 위치: Glossary `P3-req:19`~`:45`
- 문제: "404 화면"이 9.7, 11.4, 15.7, 9.5에서 쓰이지만 Glossary에 없습니다. "PR당 비용"(6.4 본문에만 정의)과 "GitHub PR 링크"(10.2 본문에만 정의)도 같습니다. "완전성 상태"는 P1 ReviewResult 정의 안에만 있고 독립 용어가 아닙니다.
- 수정안: Not_Found_Screen, Cost_Per_PR, PR_Link를 Glossary에 추가합니다. 완전성 상태 값(`complete`, `incomplete`)을 명시합니다.

**A-21. [모호] 상태 레이블의 실제 문구가 없습니다.**
- 위치: `P3-req:229`(12.1), `:214`(11.6)
- 문제: Status_Label의 실제 한국어 문구가 없고 "서로 다르다"만 있습니다. 모르는 열거형 값 처리, Feedback_Target이 아닌 지적의 Feedback_State `none` 표시("반응 없음"과 "대상 아님" 구분)도 없습니다.
- 수정안: 설계 부록으로 레이블 표를 요구하고 두 경우의 문구를 지정합니다.

**A-22. [누락] 긴 텍스트, 숫자 서식, null 표기 규칙이 없거나 제각각입니다.**
- 위치: 요구사항 10, 11, 13, `P3-req:267`(14.3)
- 문제: 긴 텍스트 처리 규칙이 없습니다(PR 제목, `message`, `summary`, 막대 레이블의 `basis.ref` 최대 50개). 개수·토큰 수의 천 단위 구분도 없습니다. null 표기는 `-`(15.9), "없음"(11.5, 12.5, 13.8), "알 수 없음"(12.7) 세 가지입니다.
- 수정안: 줄바꿈/말줄임 규칙과 숫자 서식을 한 줄 추가합니다. null 표기를 표로 정리합니다.

**A-23. [모호] `top`의 정수 형식이 느슨합니다.**
- 위치: `P3-req:110`(4.4)
- 문제: `top`의 정수 형식이 1.4만큼 정밀하지 않습니다(부호, 앞자리 0, `10.0`).
- 수정안: 1.4와 같은 문구를 씁니다.

**A-24. [모호] 네 번의 통계 호출이 서로 다른 기간을 쓸 수 있습니다.**
- 위치: `P3-req:89`(3.2), `:256`(13.12), `:129`(5.6)
- 문제: 기본 기간이 "요청 시점" 기준이라, 네 번의 독립 호출이 UTC 자정을 걸치면 서로 다른 기간이 적용됩니다. 조회 사이에 실행이 추가되면 13.10과 5.6이 전제하는 엔드포인트 간 일치도 깨집니다.
- 수정안: Web_App이 첫 응답의 `from`/`to`를 나머지 호출에 명시적으로 넘기거나, 통합 엔드포인트 하나로 제공합니다.

**A-25. [모호] 기간 포함 관계 속성이 구간별 값에는 정의되지 않습니다.**
- 위치: `P3-req:99`(3.12)
- 문제: 기간 포함 관계 속성을 "11번과 같은 값"에 적용하는데, Cost_Bucket별 값은 기간이 달라지면 구간 경계가 달라져 비교 대상이 정의되지 않습니다.
- 수정안: 3.12를 기간 전체 값으로 한정합니다.

### 결정 대기 항목

| ID | 내용 (제안값) | 막히는 요구사항 |
|---|---|---|
| D-1 | 페이지네이션 방식 (offset `page`/`size`) | 1 전체, 10.3~10.7, 11.9, 8.1 |
| D-2 | P2 10.5 응답 형식 교체 (버전 없이) | 1.1, 8.1 |
| D-3 | 지적 통계에 넣을 실행 (`succeeded`, `incomplete`) | 3.8, 3.11, 3.12, 4 전체, 5 전체, 13.4~13.7, 13.10, 7.4 |
| D-4 | 채택률 분모 (`line_comment_posted`만, adopted ÷ (adopted + rejected)) | 5.3~5.5, 5.7~5.9, 13.6, 13.7 |
| D-5 | 통계 기본 기간 (최근 30일, 최대 366일) | 3.2~3.4, 13.3, 7.2의 측정 조건 |
| D-6 | 표시 시간대 (시각은 KST, 통계는 UTC) | 15.9, 10.2, 11.2, 11.5, 13.3, Cost_Bucket |
| D-7 | Markdown 렌더링 (평문 + 줄바꿈) | 16.6, 16.11, 11.5, 11.6, 17.4 |
| D-8 | 외부 노출 (`127.0.0.1` 바인딩) | 16.8, 17.1 |
| D-9 | 차트 라이브러리 (미정) | 13.4, 13.5, 13.8, 14 전체, 17.5 |
| D-10 | 통계 규칙 링크 기준 (`blob/HEAD`) | 13.4 |
| D-11 | Type_Contract_Check 구현 방식 (미정) | 8.2, 8.3, 8.6, 16.9, 월요일 `types.ts` 선머지 |
| D-12 | 비용 상한 초과 판정 (요청 시점 Configuration) | 6.6, 6.7, 13.8 |

- 본문에서 인용되지 않는 항목: D-2(Introduction에서만 다룸), D-9, D-11. 8.3에 D-11을, 14에 D-9를 걸어 두는 편이 좋습니다.
- 표 밖의 가정: `:323`의 "조회 중 데이터가 추가되면 중복·누락 가능", `:328`의 KST/UTC 혼용 한계, `:332`의 링크 404 한계. TBD나 spike 표기는 없습니다.
- 표에 없지만 결정이 필요한 것: A-3, A-4, A-7, A-8, A-12, A-14와 A-5(D-3 확장), 그리고 B의 지적들(아래 [P2에 요구하는 추가·변경](#p3가-p2에-요구하는-추가변경) 참고).

### 통계 정의

| 지표 | 정의 위치 | 분자·분모 | 기간 | 묶음 | 0 분모 | 여러 실행 처리 | 빠진 것 |
|---|---|---|---|---|---|---|---|
| 규칙별 지적 상위 N | 4.1~4.7, Rule_Stat | 완전 (Counted_Run의 rule/spec 지적 수) | 완전 (Stats_Period, 등록 시각 UTC) | 완전 (`type`+`ref`, 저장소 구분 없음) | 완전 (빈 목록) | 모든 Counted_Run 합산 | 연속 실행 중복 (A-5), 적용 `top` 미반환 (A-8) |
| 심각도 분포 | 5.1, 5.2 | 완전 (개수와 전체 수) | 완전 | 완전 (4개 고정 순서, 0 포함) | 해당 없음 | 모든 Counted_Run 합산 | 연속 실행 중복 (A-5). 비율은 정의되지 않고 개수만 있음 |
| 채택률 | Adoption_Rate, 5.3~5.5 | 완전 (adopted ÷ (adopted + rejected), `line_comment_posted`만) | 완전 | 전체 1개 값 | 완전 (null) | `reused`·`duplicate_in_run` 제외로 중복 방지 | D-4 미확정. 분모에 `none`이 없어 리액션이 적으면 과대. PLAYBOOK 수기 "지적 n건 / 반영 m건"과 분모가 달라 비교 불가 (화면 안내는 13.6에 있음) |
| 비용 추이 (구간별 합) | 6.1~6.5, Cost_Bucket | 완전 (null 제외 합, null 개수 별도) | 완전 | 완전 (`day`/`week`, 빈 구간 포함) | 해당 없음 | `failed`·`superseded` 포함 전체 | JSON 표현 (A-12), 적용 `bucket` 미반환 (A-8) |
| PR당 비용 | 6.4, 6.6 | 완전 (합 ÷ 비용이 null이 아닌 실행을 가진 PR 수) | 완전 | 구간별과 기간 전체 | 완전 (null) | PR의 모든 Cost_Run 합산 | 나눗셈 정밀도 (A-12). 응답 없이 실패한 실행은 비용 0(P2 8.3)이라 분모에 들어감 |
| 상한 초과 실행 수 | 6.7, D-12 | 완전 (비용 > 요청 시점 상한) | 완전 | 구간별만 | 해당 없음 | 실행 단위 | 기간 전체 값 없음 (A-7), D-12 미확정 |
| 근거 연결률 (PRD 2장) | 정의 없음 (4.5에 재료만) | 없음 | — | — | — | — | 비율 정의와 화면 표시 모두 없음 (A-6) |
| 도그푸딩 80%, 응답 시간 2분 (PRD 2장) | 없음 | — | — | — | — | — | FR-14 목록에 없어 필수는 아님. 범위 밖에 명시 권장 |

### 커버리지 요약

- 요구사항 17개, 수용 기준 149개(요구사항별 12, 7, 12, 10, 9, 10, 4, 6, 7, 7, 13, 9, 13, 4, 10, 11, 5). 이 중 FOR ALL 속성 20개.
- P3 Glossary 용어 25개는 모두 본문에서 쓰입니다. 결정 대기 12개(D-1~D-12).
- **FR-12**: 목록 3종과 저장소 목록 페이지네이션(요구사항 1), 필드 보강과 단건 2종(2), 통계 API(3~6), 오류 형식, OpenAPI(8)로 충족.
- **FR-13**: PR 목록(10), 상세의 이력·지적·근거 링크(11), 상태 표시(12)로 충족.
- **FR-14**: 네 통계 모두 요구사항 13에 있음.
- **ROADMAP**: 트랙 배정, `types.ts` 규칙, 기능 동결, 완료 조건(17.2), FR-14 우선 축소가 반영됨(A-9의 불일치 제외).
- **PRD 8장**: 인증·멀티 테넌시·배포를 넘는 요구는 없음(`:15`, 16.8). 범위 부담은 A-11.
- **backend/web 구분**: 주어로 구분됩니다. Query_API, Stats_API, PR_Lens_Server가 요구사항 1~7과 8.1이고 나머지는 Web_App입니다. 예외는 8.5, 17.1(A-18)과 공통 15~17의 소유자(A-10).
- 문서 안 교차 참조(요구사항 번호와 D-번호)는 모두 실재 항목을 가리킵니다. P2 10.1~10.15, 7.5, 7.6, 7.9, 8.5, 16.6, 16.9, D-8, D-10과 P1 1.3, 18.3, 20.2, 22도 실재를 확인했습니다.

---

## B. P1·P2·저장소 정합성

경로 약어: `P2-req`/`P2-des`, `P1-req`/`P1-des`는 [목차](README.md#읽는-법)와 같습니다. `repo` = `claude-team-prlen/`, `Next 문서` = `repo/web/node_modules/next/dist/docs/01-app/`.

### 총평

- **P1·P2 요구사항 문서와는 대체로 맞습니다.** 용어, 열거형 값, 정렬 기준, "마지막 Review_Run" 정의가 일치하고, P2 10.5를 페이지네이션으로 대체한다는 점도 명시했습니다.
- **P2 설계와는 여러 곳에서 어긋납니다.** 오류 본문 형식, 목록 봉투 `total`, `ReviewStore` 시그니처, Publish_Outcome 기본값, 이미 있는 DTO 필드.
- **저장소와는 P2의 두 문제를 그대로 반복합니다.** `/api/...` 경로는 `/api/v1/` 규칙과 충돌하고, 오류 형식은 "Standard_Error_Response"라는 이름만 쓰고 필드 매핑을 정하지 않았습니다. ROADMAP이 T2에 준 "에러 포맷" 작업이 요구사항에 없습니다.
- **저장소 규칙과 맞는 것**: 메모 샘플 제거(9.1), `page`/`size`, Server Component 전용 호출, 계산은 backend에서만 한다는 원칙.

### 치명

**B-1. [누락/저장소 충돌] 오류 본문 형식이 정해지지 않았는데 웹이 그 내용을 읽어야 합니다.**
- P3: `P3-req:58`(1.4), `:91-93`(3.4~3.6), `:110`(4.4), `:142`(6.3)은 "잘못된 쿼리 변수 이름을 모두 담은 Standard_Error_Response". `:255`(13.11)은 웹이 "문제 필터 이름을 담은 안내"를 표시. `:8`은 "404·400·500 응답 … 유지".
- P2: `P2-des:21` "Spring의 `ProblemDetail`(RFC 9457) 형식을 가정", `P2-des:775` "400은 확장 필드 `invalidPathVariables`", `P2-des:780` "400/404/500 `ProblemDetail` 스키마".
- repo: `docs/adr/0002-standard-error-response.md:8` `ErrorResponse { code, message, details }`. `web/src/lib/api/http.ts:36-40`은 `code`·`message`가 문자열일 때만 본문을 인정하므로 ProblemDetail이 오면 `body = null`입니다. `web/src/lib/api/types.ts:13-17`은 `details: string[]`.
- ROADMAP: `ROADMAP.md:86`은 T2에 "조회 API 보강(페이지네이션, 에러 포맷)"을 줬는데 P3에는 해당 요구사항이 없습니다.
- 수정안: 요구사항 2 또는 8에 다음을 추가합니다.
  - Query_API·Stats_API 오류는 ADR 0002 `ErrorResponse`.
  - `code` 목록(UPPER_SNAKE_CASE: 잘못된 파라미터, 저장소·PR·Run 없음, 405, 500).
  - `details`에는 잘못된 변수 이름을 하나씩.
  - openapi 스키마 이름은 `ErrorResponse`.
  - `P2-des:21, 775, 780`을 대체한다고 명시.

### 중요

**B-2. [저장소 충돌] API 경로에 `/api/v1`이 없고 P3가 새 경로 6개를 더 추가합니다.**
- P3: `P3-req:28` `/api/stats/...`, `:76` `GET /api/repositories/{owner}/{repo}/pulls/{number}`, `:77` `GET /api/runs/{runId}`, `:107` `/api/stats/rules`, `:124` `/api/stats/severity`, `:126` `/api/stats/adoption`, `:140` `/api/stats/cost-trend`.
- P2: `P2-req:222-225`, `P2-des:718-721`도 `/api/repositories…`.
- repo: `.claude/rules/backend/api-design.md:6` "URL: `/api/v1/<복수형-kebab-case>`".
- `stats/severity`, `stats/adoption`, `pulls`는 복수형 리소스 규칙과도 맞지 않습니다.
- 수정안: P2·P3·openapi를 `/api/v1/...`로 통일하거나, 예외를 ADR로 남기고 P3 Introduction에 적습니다.

**B-3. [모순] Publish_Outcome "미정이면 null"이 P2 정의와 다릅니다.**
- P3: `P3-req:75`(2.2) "값이 정해지지 않은 경우(`queued`·`running`, 게시 없이 CLI `--save`로 저장된 경우 …)에는 null".
- P2: `P2-req:38` "`not_published`(기본값, 게시 전이거나 게시하지 않은 Review_Run)", `P2-req:176`, `P2-des:601` `NOT NULL DEFAULT 'not_published'`, `P2-des:699`.
- `queued`·`running`에는 Stored_Finding이 없어 그 경우는 성립하지 않습니다(`P2-des:483`).
- `P2-des:749-750` FindingDto에는 이미 `summaryOnlyReason`, `publishOutcome`이 있어 "P2 필드에 더해"라는 표현도 맞지 않습니다.
- `P3-req:229`(12.1)는 "Publish_Outcome의 모든 값"에 레이블을 요구합니다.
- 수정안: 2.2에서 Publish_Outcome은 P2 값 그대로(`not_published` 포함, null 없음)로 하고, null 허용은 Summary_Only 사유에만 둡니다.

**B-4. [누락(데이터)] `spec` 근거 링크가 깨지는 경우를 구분할 데이터가 P2에 없습니다.**
- P3: `P3-req:37`, `:215`(11.7) "base SHA 기준 Basis_Link".
- P1: `P1-req:176` "base SHA에서 404일 때만 같은 경로를 head SHA 기준으로 조회", `P1-des:409` `enum Revision { BASE, HEAD } // HEAD는 spec fallback만`.
- P2: `P2-des:472` `ContextFileRef(String path, ContextSource source)`, `P2-des:542` `context_files … [{"path","source"}]`. revision을 저장하지 않습니다.
- PR에서 새로 추가된 스펙을 근거로 한 지적은 링크가 404가 됩니다.
- 수정안 (둘 중 하나)
  - P2 `context_files`에 revision을 추가하고 RunDto로 노출합니다. 이 경우 `P3-req:8` "저장 스키마를 바꾸지 않는다"와 충돌하므로 명시가 필요합니다.
  - P3에 한계를 적고 `spec`은 head SHA 링크를 함께 제공합니다.

**B-5. [모순/누락] 목록 봉투와 `ReviewStore` 변경이 P2에 미치는 영향이 적혀 있지 않습니다.**
- P3: `P3-req:26` Page_Response `items, page, size, totalCount, totalPages`. `:157`(7.1) 집계를 Review_Store 인터페이스로 수행. `:160`(7.4) 메모리·DB 구현 동등. `:8` "저장 스키마와 webhook·게시 동작을 바꾸지 않습니다".
- P2: `P2-des:727` `{ "items": [...], "total": 137 }`, `P2-des:426-431` `listRepositories(int limit)` … `record Page<T>(List<T> items, long total)`, `P2-des:438`, `P2-des:1324-1326` Property 12 "앞 `min(100, n)`개 … `total`".
- 필드 이름(`total` → `totalCount`), 인터페이스 시그니처(offset 추가), 통계 메서드와 단건 조회(`findPullRequest`는 `PrKey`만 반환, `P2-des:425`), 메모리 구현, Property 12·14가 모두 바뀝니다.
- 수정안: P3에 "P2 산출물 변경 목록"을 두고 담당을 T2로 명시합니다.

**B-6. [누락] P2 8.5 등 목록 기반 속성의 페이지네이션 재해석이 빠졌습니다.**
- P3: `P3-req:8`은 10.13과 10.15만 다시 읽고, `:148`(6.9)은 "P2 요구사항 8.5와 같은 값 사용"이라고 인용만 합니다.
- P2: `P2-req:198`(8.5) "Review_Run 목록(요구사항 10.3)이 반환한 추정 비용의 합 … 저장된 … 합과 같고". 기본 `size` 20에서는 한 번의 응답으로 성립하지 않습니다. `P2-des:810`(G-8)이 같은 문제를 이미 지적했습니다.
- 수정안: Introduction에 "P2 8.5는 전 페이지를 이어 붙인 목록에 적용"을 추가합니다.

**B-7. [저장소 충돌] HTTP 404와 로딩 표시를 동시에 요구하면 상태 코드가 200이 됩니다.**
- P3: `P3-req:187`(9.7), `:212`(11.4), `:282`(15.7) "HTTP 상태 404와 함께". `:279`(15.4) "불러오는 동안 `role="status"` 로딩 표시".
- repo: `.claude/rules/frontend/nextjs.md:11` "로딩·에러 UI는 라우트 폴더의 `loading.tsx`, `error.tsx`를 우선".
- Next 문서: `03-api-reference/03-file-conventions/loading.md:103-111` "When streaming, a `200` status code will be returned", `02-guides/streaming.md:621` "place `notFound()` before any `await` or `<Suspense>`".
- 11.4와 15.7은 API를 호출한 뒤에야 404를 알 수 있습니다.
- 수정안: 404 상태가 필요한 라우트에는 `loading.tsx`·Suspense를 두지 않는다고 정하거나, 404 요구를 "404 화면 표시(스트리밍 시 상태 코드 200 허용)"로 낮춥니다. 15.4의 적용 범위도 함께 정합니다.

**B-8. [저장소 충돌] Error_Screen 요구가 `error.tsx` 관례로는 충족되지 않습니다.**
- P3: `P3-req:280`(15.5) "오류 원인(연결 실패, 시간 초과, 서버 오류)"과 "다시 시도" 링크. `:185` 제목 `오류 · PR Lens`. `:285`(15.10) "요청 오류". `:183` 404·Error_Screen에서는 `aria-current` 없음.
- repo: `nextjs.md:11`.
- Next 문서: `03-file-conventions/error.md:111` "Errors forwarded from Server Components show a generic message … `errors.digest`". 프로덕션에서는 원인을 구분할 수 없습니다.
- 현재 샘플 `web/src/app/memos/page.tsx:12-24`는 페이지 안에서 try/catch로 처리합니다.
- 수정안: Error_Screen은 Server Component 안에서 `ApiError`를 잡아 렌더링하고 `error.tsx`는 마지막 방어선으로만 쓴다고 P3에 적습니다. `nextjs.md:11`도 갱신 대상으로 명시합니다.

**B-9. [모순] 월요일 `types.ts` 선머지가 같은-PR 규칙·계약 검사와 충돌합니다.**
- P3: `P3-req:11` "A가 `types.ts`의 타입 정의를 먼저 머지 … 이후 API PR만 `types.ts`를 수정".
- 같은 P3의 `:168-170`(8.1~8.3): openapi와 타입이 다르면 CI 실패, 실제 응답을 스키마로 검사. `:172`(8.5) "같은 PR".
- P2: `P2-des:784` "`/api/**` 경로 집합과 문서의 `paths` 집합이 같은지 비교". 미구현 엔드포인트를 문서에 먼저 넣으면 실패합니다.
- repo: `CLAUDE.md:17`, `ROADMAP.md:89`.
- 추가 충돌 두 건
  - `P3-req:181`(9.1) "메모 타입을 제거"는 T1(화면 트랙, `P3-req:10`) 소관이라 화면 PR이 `types.ts`를 건드립니다.
  - `.claude/skills/new-page/SKILL.md:12`는 화면 작업에서 타입을 추가하라고 합니다.
- 수정안: 선머지 PR의 범위(타입 + openapi + 스텁, 또는 계약 검사 예외)와 메모 타입 삭제 담당을 명시하고, new-page 스킬을 갱신 대상에 넣습니다.

**B-10. [저장소 충돌] 환경변수 이름과 기본값이 스타터와 다릅니다.**
- P3: `P3-req:24`, `:294-295`, `:302` `PRLENS_API_BASE_URL`, 기본 `http://127.0.0.1:8080`.
- repo: `web/src/lib/api/memos.ts:6` `process.env.API_BASE_URL ?? "http://localhost:8080"`, `web/CLAUDE.md:21` "`API_BASE_URL`".
- 수정안: 이름 변경을 요구사항으로 적고 `web/CLAUDE.md` 갱신을 포함하거나, `API_BASE_URL`을 유지합니다.

**B-11. [저장소 충돌] Web_App `127.0.0.1` 바인딩은 현재 스크립트로 충족되지 않습니다.**
- P3: `P3-req:300`(16.8), `:315`(17.5) `npm run start`.
- repo: `web/package.json:6,8` `"dev": "next dev"`, `"start": "next start"`.
- Next 문서: `03-api-reference/06-cli/next.md:71,122` "Default: 0.0.0.0".
- 수정안: 16.8에 "`dev`·`start` 스크립트에 `-H 127.0.0.1`"을 명시합니다.

**B-12. [저장소 충돌/누락] 검증 수단과 의존성 추가가 승인 규칙과 연결되지 않았습니다.**
- P3가 요구하는 것
  - 속성 검증: `P3-req:221`(11.13), `:303`(16.11), `:268`(14.4).
  - 브라우저 수준 검증: `:277-278`(포커스 2px·대비 3:1·Tab 순서).
  - 런타임 응답 검증: `:283`(15.8). 현재 `http.ts:22`는 `as T` 캐스팅뿐입니다.
  - Type_Contract_Check: `:170`, D-11 `:333`.
  - 차트: D-9 `:331`.
  - 성능: `:315`.
- repo
  - `web/vitest.config.mts:6` `environment: "node"`.
  - `web/package.json:16-31`에는 fast-check, jsdom, testing-library, Playwright, axe, OpenAPI 도구, 차트 라이브러리, 스키마 검증 라이브러리가 없습니다.
  - `CLAUDE.md:23`, `web/CLAUDE.md:28` "의존성 추가는 사람의 승인".
- 수정안: 결정 대기 항목에 "웹 테스트·검증 도구와 추가 의존성 목록(승인 필요)"을 넣고, 각 접근성·성능 요구를 자동·수동 중 무엇으로 검증하는지 적습니다.

**B-13. [모호] 통계 기준이 여전히 미결이고 P2 결정 대기 항목 의존이 일부만 언급됩니다.**
- P3: `P3-req:31`, `:34-35`, `:325-326` Counted_Run, Feedback_Target, Adoption_Rate가 모두 "제안값".
- P2: `P2-req:415`(D-10) "P3 통계 API 스펙 전까지 확정".
- 언급되지 않은 의존
  - P2 D-1(`P2-req:406`)과 `ROADMAP.md:120`: FR-11이 축소되면 채택률은 항상 null입니다.
  - P2 D-12(`P2-req:417`): Publish_Error가 목록으로 바뀌면 `P3-req:74`, `:233`이 바뀝니다.
  - P2 D-9(`P2-req:414`): `superseded` 뒤 같은 SHA는 다시 리뷰되지 않아 Counted_Run에서 영구히 빠집니다.
- 수정안: D-3·D-4를 킥오프에서 확정하고, P2 D-1·D-9·D-12 의존을 표에 추가합니다.

**B-14. [모호] `basis.ref` 원문이 정규화되어 있지 않을 수 있습니다.**
- P3: `P3-req:107`(4.1) "코드 포인트 단위로 정확히 같을 때만 같은 묶음". `:221`(11.13)은 Normalized_Repo_Path를 전제.
- P1: `P1-req:234-235` 정규화해 "비교"하고 일치하면 "해당 Basis를 그대로 유지". `./.claude/rules/java.md`나 `.claude\rules\java.md`가 그대로 저장될 수 있습니다.
- 그러면 같은 규칙이 통계에서 갈라지고 Basis_Link가 깨집니다.
- 수정안: 4.1의 묶음 기준과 Basis_Link 입력을 "Normalized_Repo_Path로 정규화한 `basis.ref`"로 바꾸거나, P1·P2에서 정규화 저장을 확인해 명시합니다.
- 참고: P1 설계가 저장 전에 ref를 정규화하는지는 확인하지 못했습니다.

### 경미

**B-15. [누락] CI 체크, PR 템플릿, `docs/api/openapi.yaml`이 저장소에 없고 `verify` 구성이 다릅니다.**
- P3: `P3-req:301`(16.9) "`npm run verify`(lint, 타입 검사, 테스트, Type_Contract_Check 포함)를 CI 필수 체크 `web`으로". `:172`(8.5) PR 템플릿. `:168`.
- repo
  - `.github/`가 없습니다.
  - `docs/`에는 `adr`, `specs`만 있습니다.
  - `web/package.json:14`는 `format`(`prettier --write`) → lint → typecheck → test입니다. CI에서 포맷 위반으로 실패하지 않고, 계약 검사 단계도 없습니다.
- 수정안: 16.9에 "verify 스크립트에 계약 검사 추가, CI 워크플로 신설(또는 P1/P2 산출물 참조)"을 명시합니다.

**B-16. [누락] 메모 샘플 제거 범위가 web 코드에만 한정됩니다.**
- P3: `P3-req:181`(9.1)은 화면·라우트·`memos.ts`·메모 타입 제거를 명시합니다(양호).
- 남는 참조
  - `.claude/skills/new-page/SKILL.md:8` "기준 예시: `src/app/memos/page.tsx`, `src/lib/api/memos.ts`".
  - `.claude/skills/new-endpoint/SKILL.md:8`.
  - `repo/CLAUDE.md:2` "샘플 도메인은 메모".
  - `web/src/app/layout.tsx:5` `title: "starter web"`.
  - `web/src/lib/api/http.test.ts:6` 등의 `/api/v1/memos` 문자열.
- `P3-req:13`은 `web/CLAUDE.md`와 `rules/frontend`만 갱신 대상으로 적었습니다. `ErrorResponse` 타입을 유지한다는 것도 적혀 있지 않습니다.
- 수정안: 9.1에 "`ErrorResponse`·`http.ts`는 유지"를 넣고, 13행의 갱신 대상에 스킬과 루트 CLAUDE.md를 추가합니다.

**B-17. [모순] P3가 "추가"한다는 필드 일부가 P2 DTO에 이미 있고 JSON 이름이 없습니다.**
- P3: `P3-req:74`(2.1) "P2 필드에 더해 … Changed_Line_Count".
- P2: `P2-des:740` `"stats": { "mode", "changedLineCount", … }`.
- base SHA, `summary`, 오류, Publish_Error, Summary_Comment ID, Chunk 파일 경로는 실제로 RunDto에 없습니다(`P2-des:738-744`).
- P3는 새 필드의 JSON 이름을 정하지 않아 `stats.changedLineCount`와 중복될 수 있습니다.
- 수정안: 2.1~2.4에 필드 이름 표를 추가합니다(예: `baseSha`, `summary`, `error{kind,message}`, `publishError{kind,githubStatus}`, `summaryCommentId`, `incompleteDetails.chunks[].files`. 마지막은 `P1-des:448-449` 형식 재사용).

**B-18. [저장소 충돌] 웹이 경로·소속 검증 로직을 중복 구현하고 `number` 규칙이 P2 설계와 다릅니다.**
- P3: `P3-req:187`(9.7) "P2 10.8 형식 규칙을 어기거나 `number`가 0으로 시작하면 Query_API를 호출하지 않고 404". `:212`(11.4) 웹이 run 소속을 비교.
- P1: `P1-req:63` "앞자리 0이 없는 1 이상 2,147,483,647 이하".
- P2: `P2-des:774` `number`는 `^[0-9]+$`이고 1 이상이며 범위 초과는 404. 앞자리 0을 허용합니다.
- repo: `CLAUDE.md:18` "web은 조회와 표시만".
- 수정안: P2 10.8을 P1 1.3과 같게 고치고(P3가 P2 변경으로 명시), 소속 검증은 backend에서 PR 범위 run 조회 엔드포인트로 처리하는 방안을 검토합니다.

**B-19. [모순] base SHA가 null인 경우는 P2 스키마상 존재하지 않습니다.**
- P3: `P3-req:215` "base SHA가 null이면 링크 없는 텍스트", `:74` "값이 없으면 null".
- P2: `P2-des:526` `base_sha CHAR(40) NOT NULL`.
- 수정안: 해당 분기를 삭제하거나 근거를 적습니다.

**B-20. [모호] 저장소 이름 충돌(P2 G-9)에서 정렬 기준과 속성이 정의되지 않습니다.**
- P3: `P3-req:57` "GitHub 저장소 ID 오름차순", `:66`(1.12), `:98`(3.11) "저장소별 합 = 전체".
- P2: `P2-des:811`(G-9) 같은 `lower(owner/name)`인 두 행이 가능하고 조회는 최신 행만 씁니다. `P2-des:730` RepositoryDto에는 ID가 없습니다.
- 수정안: G-9 상황을 속성의 제외 조건으로 적거나 RepositoryDto에 `id`를 추가합니다.

**B-21. [누락] Review_Mode·Run_Trigger 표시 규칙과 `no_target` 모드가 없습니다.**
- P3: `P3-req:210`은 Review_Mode·Run_Trigger를 표시하지만 `:229`(12.1) 레이블 목록에는 없습니다. `:234`는 요약 전용만 다룹니다.
- 모드 값: `P2-des:534` `('no_target','single','split','summary_only')`, `P1-des:428`. `P1-req:44` Glossary에는 세 모드만 있습니다.
- 수정안: 12.1에 Review_Mode 네 값과 Run_Trigger 세 값의 레이블을 추가합니다.

**B-22. [모호] 성능 요구와 "스키마 불변"이 맞물립니다.**
- P3: `P3-req:158`(7.2) 100,000 Finding·366일 통계를 2초 안에. `:8` "저장 스키마 … 바꾸지 않습니다".
- P2: `P2-des:580-582, 612` 인덱스는 PR별 정렬·복구·line_comment뿐이고 `registered_at` 범위나 `basis` 집계용은 없습니다.
- 수정안: "인덱스 추가 마이그레이션(V2)은 허용"을 명시합니다.

### P3가 P2에 요구하는 추가·변경

| 항목 | P3 위치 | P2 현재 상태 | 담당 |
|---|---|---|---|
| 목록 `page`/`size`, Page_Response | `:55-66`, `:26` | `limit=100`, `{items,total}` (`P2-req:226`, `P2-des:727, 426-431`) | P3 T2. P3에 명시됨(요구사항만). 설계·ReviewStore·Property 12 변경은 미명시 |
| RunDto 필드 추가(baseSha, summary, error, publishError, summaryCommentId, chunk 파일) | `:74` | DB에는 있음(`P2-des:526, 539-540, 560-564`), DTO에는 없음 | P3 T2. 명시됨, 이름 미정 |
| FindingDto Publish_Outcome·Summary_Only 사유 | `:75` | 이미 있음(`P2-des:749-750`), null 의미 충돌 | P3 문구 수정 |
| PR 단건 `GET …/pulls/{number}` | `:76` | 없음(`findPullRequest`는 `PrKey`만) | P3 T2. 명시됨 |
| Run 단건 `GET /api/runs/{runId}` | `:77` | 엔드포인트 없음(`findRun`은 있음) | P3 T2. 명시됨 |
| HEAD 처리, 405(OPTIONS 포함) | `:79` | 정의 없음 | P3 T2. 명시됨 |
| 통계 4종과 Stats_Filter | `:88-149` | 범위 밖(`P2-req:12`) | P3 T2. 명시됨 |
| ReviewStore 집계 메서드와 메모리 구현 | `:157, 160` | 조회 메서드만 있음 | P3 T2. 인터페이스 변경 미명시 |
| 오류 본문 형식(ErrorResponse) | `:58` 등 | ProblemDetail 가정(`P2-des:21, 775`) | 미정. P2 설계 수정과 P3 요구사항 추가 필요 |
| `/api/v1` 접두사 | 전 엔드포인트 | `/api/...` | 미정. P2·P3 공동 |
| openapi 확장과 405 | `:168` | 네 엔드포인트, 400/404/500 | P3 T2. 명시됨 |
| P2 8.5 재해석 | (없음) | `P2-req:198` | P3에 추가 필요 |
| 컨텍스트 파일 revision | `:215` | 저장 안 함(`P2-des:472, 542`) | 결정 필요(스키마 변경이거나 한계 명시) |
| `number` 앞자리 0 | `:187` | 허용(`P2-des:774`) | P2 수정 |
| 통계용 인덱스 | `:158` | 없음 | P3 T2. 허용 여부 명시 필요 |
| 비용 상한 판정 | `:145-146` | 저장 안 함(`P2-des:802`) | P3 D-12로 자체 처리(스키마 불변) |

**데이터 가용성**: 다음은 P2 DDL에서 확인했습니다.
- 규칙별 집계: `basis_type`, `basis_ref`, `demotion_*` (`P2-des:593-596`)
- 심각도: `P2-des:589`
- 채택률: `feedback_state`, `publish_outcome` (`P2-des:601-604`)
- 비용·토큰·등록 시각: `P2-des:546-553`

계산할 수 없는 것은 B-4(revision)뿐이고, B-14는 값의 품질 문제입니다. "마지막 Review_Run" 정의는 `P3-req:76`과 `P2-des:764`(`registered_at DESC, attempt DESC, id DESC`)가 일치합니다.

### 저장소 충돌

| 항목 | P3 쪽 | 저장소 쪽 | 고칠 곳 |
|---|---|---|---|
| 오류 본문 | 형식 미정(`:58, :255`) | `ErrorResponse`(ADR 0002, `http.ts:36-40`) | P3 요구사항 추가, P2 설계 |
| URL 접두사 | `/api/...` | `api-design.md:6` `/api/v1/` | P2·P3 또는 ADR |
| 페이지네이션 변수 | `page`/`size`, 1부터 | `api-design.md:10` `page`, `size` | 일치(시작 값만 규칙에 없음) |
| base URL 환경변수 | `PRLENS_API_BASE_URL`, `127.0.0.1` | `API_BASE_URL`, `localhost`(`memos.ts:6`, `web/CLAUDE.md:21`) | P3 또는 web/CLAUDE.md |
| 웹 바인딩 | `127.0.0.1`(`:300`) | `next dev/start` 기본 0.0.0.0 | package.json 스크립트, P3 명시 |
| 404 상태 + 로딩 | `:187, :279, :282` | `nextjs.md:11`, Next 스트리밍은 200 | P3 |
| Error_Screen | 원인 표시(`:280`) | `error.tsx` 우선(`nextjs.md:11`) | P3와 규칙 |
| types.ts | 월요일 선머지, 화면 PR 수정 금지(`:11`), T1이 메모 타입 제거(`:181`) | `CLAUDE.md:17` 같은 PR, `new-page/SKILL.md:12` | P3와 스킬 |
| 테스트 도구 | 속성·접근성·성능 요구 | Vitest `node` 환경뿐 | P3 결정 항목, 의존성 승인 |
| 차트·계약 검사 도구 | D-9, D-11 | 의존성 승인 규칙(`CLAUDE.md:23`) | P3 결정 항목 |
| verify·CI | `:301` | `package.json:14`, `.github` 없음 | P3, 스크립트·CI 신설 |
| 메모 샘플 | web 코드 제거(`:181`) | 스킬·루트 CLAUDE.md·layout 제목이 메모 참조 | P3 `:13` 갱신 대상 확대 |
| 캐시 | 요청마다 무캐시(`:297`) | `await connection()`(`web/CLAUDE.md:22`) | 일치 |
| 서버 전용 호출 | `:293-295` | `server-only`, `src/lib/api/`만(`nextjs.md:7`) | 일치. lint 규칙 추가 필요(의존성 없이 가능) |
| `next/font/google` | 언급 없음 | 금지(`web/CLAUDE.md:29`) | 충돌 없음 |
| 문서 언어 | `ko`(`:186`) | `layout.tsx:11` `lang="ko"` | 이미 충족 |

---

## C. 외부 기술 사실

`DOCS` = `claude-team-prlen/web/node_modules/next/dist/docs/01-app` (설치된 Next.js 16.3.7에 번들된 문서).

### 총평

이 문서는 대부분이 자체 도메인 규칙(집계 정의, 속성, 정렬)이고, 외부 기술에 대한 명시적 주장은 적습니다. 확인 가능한 24건만 검증했습니다. 명백히 틀린 사실 서술은 없지만, Next.js 16.3.7 기본 동작과 충돌해 그대로는 동시에 만족할 수 없는 요구사항이 4군데 있습니다(C-1~C-4).

### 틀리거나 부분적으로 맞는 주장

설계 피해가 큰 순서입니다.

**C-1. 로딩 표시와 HTTP 404** — 부분적으로 맞음 (동시 충족 불가)
- 주장: 로딩 중 `role="status"` 표시를 보여 주면서, 같은 화면이 Query_API 404나 잘못된 `run`에 HTTP 404를 반환한다.
- 위치: `P3-req:279`(15.4), `:282`(15.7), `:212`(11.4), `:187`(9.7)
- 근거: "Next.js will return a `200` HTTP status code for streamed responses, and `404` for non-streamed responses" (`DOCS/03-api-reference/03-file-conventions/not-found.md:13`). "it is not possible to change the status code after streaming started", "The response body starts streaming when a Suspense fallback renders (for example, a `loading.tsx`)… Place `notFound()` before those boundaries and before any `await` that may suspend" (`.../loading.md:101-120`). `loading.tsx`를 두면 backend 조회 뒤의 `notFound()`는 200이 됩니다. 문서가 권하는 `proxy` 사전 검사는 9.7(형식 검사)에만 맞고, backend 조회가 필요한 11.4·15.7에는 "avoid fetching full content there"와 어긋납니다.

**C-2. Error_Screen의 오류 원인 구분** — 부분적으로 맞음
- 주장: Error_Screen이 오류 원인(연결 실패, 시간 초과, 서버 오류, 요청 오류)을 구분해 보여 준다.
- 위치: `P3-req:280`(15.5), `:285`(15.10), `:283`(15.8)
- 근거: 프로덕션에서 "Errors forwarded from Server Components show a generic message with an identifier(digest)" (`.../error.md:106-111`). throw 후 `error.tsx` 방식으로는 원인을 구분할 수 없습니다. API_Client가 오류를 값으로 돌려주고 Server Component가 Error_Screen을 정상 렌더링해야 합니다(이 경우 HTTP 200). 15.6(스택·내부 URL 비노출)은 같은 근거로 프로덕션 기본 동작과 일치합니다.

**C-3. 오류·404 화면의 문서 제목** — 부분적으로 맞음
- 주장: Error_Screen 제목 `오류 · PR Lens`, 404 화면 제목 `찾을 수 없음 · PR Lens`.
- 위치: `P3-req:185`(9.5)
- 근거: "Error boundaries must be Client Components" (`error.md:21`). "`metadata` object and `generateMetadata` function exports are **only supported in Server Components**" (`.../04-functions/generate-metadata.md:110`). `error.tsx`에서는 metadata export를 쓸 수 없고, 문서가 제시하는 대안은 React `<title>` 컴포넌트입니다(`error.md:167`). 404 제목은 `global-not-found.js`(experimental)의 metadata만 문서화돼 있습니다(`not-found.md:183-187`). 일반 `not-found.tsx`의 metadata 지원은 확인 불가입니다.

**C-4. 기본 바인딩 주소** — 틀림 (프레임워크 기본값과 반대)
- 주장: Web_App이 기본 바인딩 주소 `127.0.0.1`에서 요청을 받는다.
- 위치: `P3-req:300`(16.8), `:330`(D-8)
- 근거: `next dev`와 `next start`의 `-H` 기본값은 `0.0.0.0`입니다(`DOCS/03-api-reference/06-cli/next.md:71`, `:122`). 현재 `package.json` 스크립트는 `next dev`, `next start`뿐이라 모든 인터페이스에 노출됩니다. `-H 127.0.0.1`을 스크립트에 명시해야 합니다.

**C-5. `KST` 표기** — 부분적으로 맞음
- 주장: 시각을 `Asia/Seoul`로 `YYYY-MM-DD HH:mm` + `KST` 표기.
- 위치: `P3-req:284`(15.9), `:328`(D-6)
- 근거: 설치 런타임(Node v24.14.1, ICU 78.2)에서 직접 실행한 결과입니다. `Intl.DateTimeFormat(…,{timeZone:"Asia/Seoul", timeZoneName:"short"})`는 ko-KR, en-US, en-KR 모두 `GMT+9`를 내고 `KST`는 나오지 않습니다(long은 "한국 표준시"). ko-KR 기본 날짜 형식은 `2026. 10. 04. 12:07`입니다. 시간대 변환은 가능하지만 `KST`는 상수 문자열로, 날짜 형식은 `formatToParts` 조립으로 만들어야 합니다.

**C-6. 금액·비율 반올림** — 부분적으로 맞음
- 주장: 금액 half up 소수 4자리(`$0.0123`), 채택률 half up 소수 1자리(`12.3%`).
- 위치: `P3-req:236`(12.8), `:250`(13.6)
- 근거: 직접 실행 결과 `(0.01235).toFixed(4)` → `0.0123`, `(0.2845*100).toFixed(1)` → `28.4`로 half up에 어긋납니다. `Intl.NumberFormat`(기본 `roundingMode: halfExpand`)은 `$0.0124`, `28.5%`를 냅니다. MDN도 `(2.55).toFixed(1) // '2.5'`를 명시합니다(https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Number/toFixed). 문서의 예시 값 자체는 맞습니다. 구현 수단을 정하지 않으면 경계값 테스트가 깨집니다.

**C-7. 405 처리** — 부분적으로 맞음
- 주장: `OPTIONS` 포함 `GET`·`HEAD` 외 메서드에 405 + Standard_Error_Response.
- 위치: `P3-req:79`(2.6)
- 근거: Spring MVC 기본은 "HTTP OPTIONS is handled by setting the `Allow` response header"로 405가 아닙니다(https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html). OPTIONS를 405로 만들려면 명시적 재정의가 필요합니다. RFC 9110 15.5.6은 "The server MUST generate an Allow header field in a 405 response"인데 문서는 `Allow`를 언급하지 않습니다(https://www.rfc-editor.org/rfc/rfc9110.html).

**C-8. 캐시 없는 조회** — 부분적으로 맞음
- 주장: 화면 요청마다 Query_API를 캐시 없이 조회.
- 위치: `P3-req:297`(16.5)
- 근거: 기본값 `auto no cache`는 "fetch once during `next build` because the route will be statically prerendered. If Request-time APIs are detected… fetch on every request" (`DOCS/03-api-reference/04-functions/fetch.md:52-53`). 네 라우트 모두 `searchParams`를 읽으면 동적이 되지만(`.../page.md:119`), 이는 암묵적 의존입니다. `cache: 'no-store'`나 `await connection()`(`web/CLAUDE.md` 규칙)을 명시해야 합니다. 개발 모드에서는 HMR 캐시가 `no-store`에도 적용됩니다(`fetch.md:101-105`).

**C-9. 차트 텍스트 대체** — 부분적으로 맞음
- 주장: 차트 텍스트 대체를 `aria-label` 또는 `<figcaption>`으로 붙인다.
- 위치: `P3-req:266`(14.2)
- 근거: `aria-label`은 `generic`(role 없는 `<div>`, `<span>`) 등에서 지원되지 않습니다(https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Attributes/aria-label). 차트 요소에 `role="img"` 같은 역할을 주거나 `<figure>`+`<figcaption>`을 써야 유효합니다.

**C-10. 포커스 표시 기준** — 부분적으로 맞음
- 주장: 포커스 표시 두께 2px 이상, 인접 색과 대비 3:1 이상.
- 위치: `P3-req:277`(15.2)
- 근거: WCAG 수준을 밝히지 않고 두 기준을 섞었습니다. 2px 둘레는 SC 2.4.13 Focus Appearance(AAA)이고, 그 3:1은 "between the same pixels in the focused and unfocused states"입니다(https://www.w3.org/WAI/WCAG22/Understanding/focus-appearance.html). "인접 색 대비 3:1"은 SC 1.4.11(AA)입니다(https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast.html). 수치는 실재하지만 측정 기준을 정해야 합니다.

**C-11. 로딩 표시의 `role="status"`** — 부분적으로 맞음
- 위치: `P3-req:279`(15.4)
- 근거: `role=status`는 암묵적으로 `aria-live=polite`, `aria-atomic=true`입니다(https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Roles/status_role). 역할은 유효합니다. 다만 최초 HTML에 이미 들어 있는 fallback이 스크린 리더에 낭독되는지는 문서로 확인하지 못했습니다.

**C-12. 공통 헤더의 `aria-current`** — 부분적으로 맞음
- 주장: 공통 헤더가 라우트별로 `aria-current="page"`를 붙이고, 404·Error 화면에서는 붙이지 않는다.
- 위치: `P3-req:183`(9.3)
- 근거: "Layouts do not re-render on navigation, so they do not access pathname", 대안은 Client Component의 `usePathname`입니다(`DOCS/03-api-reference/03-file-conventions/layout.md:238-242`). 404·오류 상태는 pathname만으로 알 수 없습니다(예: `/repositories/x/y`의 404). 경로 접두사 방식만으로는 요구를 충족하지 못합니다.

**C-13. GitHub 코드 링크의 줄 앵커** — 부분적으로 맞음
- 주장: Code_Link `blob/{SHA}/{file}#L{line}`, Basis_Link `blob/{base SHA}/{ref}`.
- 위치: `P3-req:37-38`, `:214-215`(11.6, 11.7)
- 근거: `blob/<commit_SHA>/…`와 `#L` 앵커 형식은 맞습니다. 다만 Markdown 파일은 `?plain=1`이 있어야 줄 앵커가 동작합니다(https://docs.github.com/en/get-started/writing-on-github/working-with-advanced-formatting/creating-a-permanent-link-to-a-code-snippet). `.md` 대상 Finding의 `#L{line}`은 렌더링 화면에서 무시됩니다.

### 맞는 주장

| # | 주장 | 위치 | 근거 |
|---|---|---|---|
| 14 | URL의 `page`가 두 번 이상 있는 경우를 감지 | `:200`(10.6), `:217`(11.9) | `/shop?a=1&a=2` → `{ a: ['1','2'] }`, 타입은 `Promise<{[key]: string \| string[] \| undefined}>`입니다(`DOCS/03-api-reference/03-file-conventions/page.md:113-117`). `await`가 필수입니다. |
| 15 | 라우트 경로 변수(`owner`, `repo`, `number`)를 쓰는 화면 | `:182`(9.2), `:187`(9.7) | `params`는 Promise이며 `await`해야 합니다(`page.md:38-64`, `.../dynamic-routes.md:148`). 경로 변수의 퍼센트 디코딩 여부는 번들 문서에서 찾지 못했습니다. |
| 16 | Backend_Base_URL을 서버 측 환경변수로만 읽고 번들에 넣지 않는다 | `:294`(16.2), `:24` | "By default, environment variables are only available on the server". 인라인되는 것은 `NEXT_PUBLIC_` 접두어뿐입니다(`DOCS/02-guides/environment-variables.md:156-198`). `server-only` 0.0.1이 설치돼 있습니다. |
| 17 | 필터를 `GET` form으로 제출해 JavaScript 없이 적용 | `:246`(13.2) | 네이티브 `<form method="get">`은 JS 없이 동작합니다. `next/form`도 "behaves like a native HTML form that uses a GET method… progressive enhancement"입니다(`DOCS/03-api-reference/02-components/form.md:6`, `:86`). |
| 18 | `web/src/lib/api/` 밖의 `fetch`·환경변수 참조를 lint로 실패시킨다 | `:295`(16.3) | 설치된 ESLint 9.39.5에 `no-restricted-globals`, `no-restricted-properties`, `no-restricted-syntax`, `no-process-env` 규칙이 있습니다. 현재 `eslint.config.mjs`에는 설정이 없어 추가해야 합니다. |
| 19 | 문자열 필드를 이스케이프한 텍스트로 표시, `javascript:` 링크 없음 | `:298`(16.6), `:303`(16.11) | 설치된 react-dom 19.2.8의 서버·클라이언트 프로덕션 빌드 모두에 "React has blocked a javascript: URL" 차단 코드가 있습니다. 줄바꿈 유지는 CSS로 처리해야 하며 문서에는 방법이 없습니다. |
| 20 | breadcrumb `<nav aria-label>`, 마지막 항목은 링크 없는 텍스트 + `aria-current="page"` | `:184`(9.4) | "The landmark region is labelled via aria-label or aria-labelledby", "If the element representing the current page is not a link, aria-current is optional" (https://www.w3.org/WAI/ARIA/apg/patterns/breadcrumb/). |
| 21 | 선택한 Review_Run 행에 `aria-current="true"` | `:210`(11.2) | 허용 값은 `page, step, location, date, time, true, false`이고 "Only mark one element in a set"입니다(https://developer.mozilla.org/en-US/docs/Web/Accessibility/ARIA/Reference/Attributes/aria-current). |
| 22 | `HEAD`를 같은 경로 `GET`과 같은 상태 코드로 본문 없이 처리 | `:79`(2.6) | "`@GetMapping`… support HTTP HEAD transparently" (Spring 문서, C-7과 같은 URL). |
| 23 | 문서 언어 `ko` | `:186`(9.6) | 루트 layout의 `<html lang>`으로 지정하며 현재 `web/src/app/layout.tsx`가 이미 `lang="ko"`입니다. |
| 24 | 10초 안에 응답이 없으면 시간 초과로 처리 | `:280`(15.5) | `AbortSignal.timeout()`은 `TimeoutError` DOMException을 내며 `AbortError`와 구분됩니다(https://developer.mozilla.org/en-US/docs/Web/API/AbortSignal/timeout_static). |

### 구현에 영향이 큰 누락

요구사항이 언급하지 않은 외부 제약입니다.

1. **스트리밍 메타데이터**: 동적 페이지의 `generateMetadata` 결과는 일반 브라우저 UA에서 `<head>`가 아니라 `<body>` 끝에 붙을 수 있습니다(`generate-metadata.md:1240-1270`). 9.5의 `<title>` 검사와 17.5의 "마지막 바이트" 측정에 영향을 줍니다. 끄려면 `htmlLimitedBots: /.*/`를 씁니다.
2. **`<Link>` 프리페치**: 프로덕션에서 뷰포트에 들어온 링크는 자동 프리페치됩니다. 동적 라우트는 가장 가까운 `loading.js` 경계까지입니다(`DOCS/03-api-reference/02-components/link.md:298-304`). 목록의 PR 링크 20개, Review_Run 선택 링크, 페이지 링크가 서버 요청을 유발합니다. `prefetch={false}` 여부를 정해야 합니다.
3. **클라이언트 라우터 캐시**: `staleTimes.dynamic` 기본값은 0초지만 loading 경계는 `static` 기간(5분) 동안 재사용됩니다(`.../next-config-js/staleTimes.md:27-34`). 새로고침은 16.5를 만족하지만, 뒤로·앞으로 이동 때의 신선도는 문서가 정하지 않았습니다.
4. **Vitest는 async Server Component 미지원**: "Vitest currently does not support them… we recommend using E2E tests for `async` components" (`DOCS/02-guides/testing/vitest.md:9`). 11.13, 14.4, 16.11, 17.4의 화면 HTML 속성은 동기 표시 컴포넌트와 순수 함수로 분리하거나 E2E를 도입해야 검증됩니다.
5. **`error.tsx` 복구 방식**: 16.3에서 `retry` prop이 stable이 됐습니다(`error.md:117-121`, `:331`). 15.5의 "다시 시도 링크"는 버튼이 아니라 현재 URL의 `<a>`로 직접 만들어야 하고, URL은 Client Component에서 `usePathname`과 `useSearchParams`로 얻어야 합니다. 루트 layout 오류는 `global-error.tsx`가 처리하며 자체 `<html>`, `<body>`를 가져 공통 헤더(9.3)가 빠집니다(`error.md:163-167`).
6. **빌드 시 backend 의존**: `searchParams`나 `connection()` 없이 fetch하는 경로가 생기면 `next build`가 backend를 호출하고 결과를 고정합니다(`fetch.md:52`). 17.5의 `npm run build`와 CI 빌드에 영향을 줍니다.
7. **405의 `Allow` 헤더와 Spring의 OPTIONS 기본 처리**: C-7과 같습니다. OpenAPI 계약 검사(8.1)에 405를 넣으려면 헤더까지 정해야 합니다.
8. **Spring의 정수 바인딩**: 1.4의 "부호와 앞자리 0 없는 십진수, 중복 금지"를 Spring 기본 `@RequestParam int` 바인딩이 그대로 강제하는지는 문서로 확인하지 못했습니다. 설계에서 커스텀 검증을 전제해야 합니다.
9. **JSON 숫자 정밀도**: 6.8은 "정확한 십진 합"을 요구하지만, 웹은 JSON 숫자를 IEEE 754 double로 받습니다(실행 확인: `0.1+0.2 → 0.30000000000000004`). 12.8·13.6의 half up 판정이 경계에서 흔들리므로 금액을 문자열로 보낼지 정해야 합니다.
10. **시각 포맷은 서버에서만**: 15.9의 포맷을 Client Component에서 `timeZone` 없이 하면 서버와 브라우저 결과가 달라질 수 있습니다. `timeZone`을 명시한 서버 포맷으로 고정해야 합니다. 이 항목은 외부 문서를 인용하지 않은 일반 원칙입니다.

### 설치된 버전

출처: `claude-team-prlen/web/package.json`과 `node_modules/*/package.json`.

| 패키지 | 설치 버전 |
|---|---|
| next | 16.3.7 (Turbopack이 기본 번들러) |
| react / react-dom | 19.2.8 |
| typescript | 5.9.3 |
| eslint | 9.39.5 |
| eslint-config-next | 16.3.7 |
| eslint-plugin-jsx-a11y | 6.10.2 (eslint-config-next의 전이 의존) |
| vitest | 5.0.2 (`environment: "node"`, `src/**/*.test.{ts,tsx}`) |
| vite | 8.3.1 |
| prettier | 3.9.9 |
| server-only | 0.0.1 |
| @types/node / @types/react | 24.19.0 / 19.3.0 |
| axe-core | 4.13.0 (전이 의존) |
| Node | v24.14.1 (engines `>=24`) |

backend는 Spring Boot 4.1.1, Java 17, `spring-boot-starter-webmvc`입니다.

**요구사항이 전제하지만 설치돼 있지 않거나 없는 것**

- **DOM·컴포넌트 테스트 도구**: jsdom, happy-dom, @testing-library/react, @vitejs/plugin-react가 없습니다.
- **E2E**: Playwright가 없습니다.
- **속성 기반 테스트**: fast-check가 없습니다. "FOR ALL" 속성 요구 20개가 여기에 걸립니다.
- **Type_Contract_Check(8.3)**: openapi-typescript 같은 OpenAPI 타입 생성 도구가 없습니다.
- **차트 라이브러리(D-9)**: 없습니다.
- **backend OpenAPI**: springdoc 의존성이 없고, `claude-team-prlen/docs` 아래에 `api/openapi.yaml`이 없습니다.
- **환경변수 이름**: 현재 코드는 `API_BASE_URL`과 기본값 `http://localhost:8080`을 씁니다. 요구사항은 `PRLENS_API_BASE_URL`과 `http://127.0.0.1:8080`입니다.
- 의존성 추가는 `CLAUDE.md` 규칙상 사람 승인이 필요합니다.

### 확인 불가 항목

- **Line_Comment_Link `…/pull/{n}#discussion_r{id}`, Summary_Comment_Link `…/pull/{n}#issuecomment-{id}`** (`:39-40`, 11.5, 11.6): docs.github.com REST 문서 두 페이지를 조회했지만 `html_url`은 "string, format: uri" 스키마만 나오고 예시 값을 얻지 못했습니다. 형식을 조립하기보다 GitHub 응답의 `html_url`을 저장해 쓰는 편이 안전합니다(P3는 저장 스키마 변경 금지라 별도 결정이 필요).
- **`blob/HEAD/{path}`가 기본 브랜치로 해석됨** (`:248` 13.4, D-10): GitHub 공식 문서에서 근거를 찾지 못했습니다.
- **`basis.ref`가 디렉터리일 때의 `blob` 링크 동작**: 문서 근거가 없습니다.
- **일반 `not-found.tsx`의 `metadata` export 지원 여부**: 번들 문서는 `global-not-found.js`만 명시합니다. 실제 빌드로 확인해야 합니다.
- **`loading.tsx`가 있는 세그먼트에서 fetch 이전 `notFound()`(9.7)가 404를 유지하는지**: 문서는 "before any await that may suspend"라고만 하고, `await params`가 이에 해당하는지는 밝히지 않습니다. 실측이 필요합니다.
- **동적 세그먼트 값의 퍼센트 디코딩 규칙**: `dynamic-routes.md`에 기술이 없습니다.
- **Vitest 5(node 환경)에서 `.tsx`와 `react-dom/server`로 동기 컴포넌트를 렌더링할 수 있는지**: 실행해 보지 않았습니다.
- **성능 기준(7.2의 2초, 17.5의 3초)**: LCP 같은 명명된 지표가 아닌 자체 기준이라 외부 검증 대상이 아닙니다.
- **skip link와 `<main>` 포커스 이동(15.3), `<table>`·`<caption>`·`scope`(15.1)**: W3C 기법 문서를 열어 보지 않아 판정에서 뺐습니다.

---

## 확인하지 못한 것

- **A**: P1·P2 스펙은 전체를 읽지 않았습니다. P2 4.5, 11.4, 13.3, 15.8, 16.2, 18.2와 P1 16.3은 내용이 인용 취지와 맞는지 확인하지 못했습니다. 1주 분량 판단(A-11)은 추정입니다.
- **B**: P1 설계 전체와 P2 `tasks.md`, P2 요구사항 11~12·18~20의 본문, P2 설계 T3 절, `backend/` 소스를 읽지 않았습니다. `ErrorResponse`의 실제 구현과 `details` 타입은 ADR과 `types.ts`로만 확인했습니다.
- **C**: 빌드와 테스트는 실행하지 않았습니다. Next.js 동작 판정은 번들 문서에 근거하며, C-5와 C-6만 설치된 Node에서 직접 실행해 확인했습니다.
- 세 검토 모두 파일을 만들거나 고치지 않았습니다.
