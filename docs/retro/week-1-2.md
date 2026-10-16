# 1~2주차 회고

- 대상: PR #1~#26 (2026-10-07~10-16). 주차는 머지일(KST) 기준으로 나눴습니다. 1주차는 10/11까지(#1~#11), 2주차는 10/12부터(#12~#26)입니다
- 역할 순환은 실제로 운영되지 않아 역할 표기와 역할별 인수인계는 뺐습니다(PR 26건 중 25건을 한 사람이 작성)
- 출처: `gh pr list --state all --limit 500 --json number,state,createdAt,mergedAt,closedAt,reviews`, 각 PR 설명의 `AI 리뷰 지적 n건 / 반영 m건`

## 결과

### 1주차: M0 기반과 합의 — 부분 완료
- 완료: 저장소 생성과 패키지 이름 변경(P1 1.1, [#1](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/1)), CI 통과, ADR 0004·0005([#2](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/2), [#3](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/3)), PRD 결정 4건([#4](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/4)), 공유 타입 선머지(P1 2.1~2.3, [#7](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/7))
- 못 한 것
  - PRD `approved`, P1 스펙 팀 승인: 검토는 마쳤으나 상태를 바꾸지 않음
  - ADR 0006(DB·테스트), 0007(저장소 구조): 미작성
  - spike 3건 중 GitHub diff 조회, smee webhook: 미수행(픽스처 PR 확인은 2주차 [#16](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/16)에서 함)
  - 지표 기준선, 라벨 `track-1`~`track-3`: 미완
- 이유: 1주차 PR 11건 중 6건이 컨텍스트(`/task`, `/pr-ready`, 리뷰 에이전트) 정비였습니다. 구현을 시작하기 전에 작업 흐름을 먼저 다듬는 데 시간을 썼습니다

### 2주차: M1 CLI MVP — 미완
- 완료: P1 상위 작업 1~6(공유 작업 1~4, T2의 5 diff 해석, 6 glob 필터), T1 11.1~11.4(청크 계획, [#17](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/17)). `tasks.md` 체크박스 기준 37/110
- 못 한 것: 완료 조건("실제 PR 1건을 CLI로 리뷰")에 필요한 7(GitHub 클라이언트), 8(컨텍스트 수집), 10·12·13(리뷰 엔진), 15~18(CLI와 출력)
- 이유
  - 트랙 병렬이 일어나지 않았습니다. T1은 머지 1건(#17)과 진행 중 브랜치 1개(`feat/t1-p1-10-response-validation`)이고, T2와 공유 작업은 한 사람이 순서대로 진행했습니다. T3는 원격 브랜치가 없습니다
  - 기능 PR 하나가 크고 오래 걸렸습니다(#12 18.5시간, #17 17.7시간. 테스트 포함 추가 줄 수는 #7 2,782, #12 1,406, #13 1,208)

## 지표

| | 1주차 | 2주차 | 합계 |
|---|---|---|---|
| 머지된 PR | 11 | 13 (+닫힘 1 [#23](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/23), 머지 금지 픽스처 1 [#15](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/15)) | 24 |
| 평균 리드타임 | 7.1시간 | 3.0시간 | 4.9시간 |
| 리드타임 중앙값 | 2분 | 3분 | 3분 |
| 반려율 | 0% | 0% | 0% |
| AI 리뷰 지적 / 반영 | 137 / 87 (64%) | 90 / 57 (63%) | 227 / 144 (63%) |

- **리드타임**: 중앙값이 3분인 것은 사람 리뷰 없이 작성자가 바로 머지했기 때문입니다. 2시간을 넘은 PR은 기능 PR 6건(#1, #6, #7, #12, #17, #18)뿐이고, 이 6건의 평균은 19.3시간입니다
- **반려율 0%는 사람 리뷰가 없어서 나온 숫자입니다.** 26건 중 사람 리뷰는 #1의 승인 1건뿐이고 `CHANGES_REQUESTED`는 없었습니다. 그 결과 [#26](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/26)에서 "승인 1건 필수"를 걸지 않기로 했습니다
- **AI 리뷰**: 코드 PR(#1, #6, #7, #12, #13, #17, #18, #20, #22)은 83건 중 61건(73%)을 반영했고, 문서·컨텍스트 PR은 144건 중 83건(58%)을 반영했습니다. 문서 PR에서 기각된 지적은 대부분 "선택"(표기 통일, 범위 밖 서술)이었습니다. 집계에는 PR 설명의 값을 썼고, 리뷰어 확인은 거치지 않았습니다. #12는 `/pr-ready` 리뷰(5/2)만 셌습니다(작업별 리뷰 17/8, 후속 리뷰 5/3 별도)
- **규칙 추가·삭제** (`context` 라벨 PR과 규칙 문서를 고친 PR)

| 주차 | PR | 종류 | 내용 |
|---|---|---|---|
| 1 | [#5](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/5) | 수정 | `new-endpoint` 스킬을 평면 패키지 기준으로 |
| 1 | [#6](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/6) | 추가·삭제 | ArchUnit 규칙 8개와 소스 검사 테스트 추가, `testing.md`의 "1.2 전에는 쓰지 않는다" 단서 삭제 |
| 1 | [#8](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/8) | 추가 | "뒤 작업에 넘길 것"을 스펙 문서에 반영(`/pr-ready`, `reviewer`) |
| 1 | [#9](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/9) | 추가 | `context-reviewer` 에이전트, `.claude/rules/context/editing.md` |
| 1 | [#10](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/10) | 추가 | `/task` 계획 승인 단계 |
| 1 | [#11](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/11) | 수정 | `/task`가 브랜치 이름 확인에서 멈춘 뒤 계획으로 |
| 2 | [#14](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/14) | 추가·삭제 | handoff 메모 파일 추가, `/pr-ready`의 "보고가 없으면 사용자에게 묻는다" 삭제 |
| 2 | [#19](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/19) | 수정 | `reviewer`, `context-reviewer` 모델을 opus로 |
| 2 | [#21](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/21) | 추가 | 패키지 경로째 쓰지 않고 import (`backend/CLAUDE.md`, `reviewer`) |
| 2 | [#23](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/23) | 철회 | `/task` 계획을 opus `task-planner`에 맡기기 → 닫음 |
| 2 | [#25](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/25) | 삭제 | PR 템플릿의 "모든 변경 줄을 설명할 수 있음" |
| 2 | [#26](https://github.com/jjangyeesoo/claude-team-pr-lens/pull/26) | 수정 | 승인 1건 필수를 걸지 않는 결정 |

  합계: 추가 7, 삭제 3, 수정 4, 철회 1 (#6, #14는 추가와 삭제에 모두 셈)

## 방법론 관찰 (발표 재료)

### 효과 있었던 것
- **"뒤 작업에 넘길 것"을 스펙에 남기기** (#8, #14): 리뷰가 찾은 "뒤 작업이 스펙만 읽으면 틀리는 것"이 작업 본문의 `주의`·`미정` 줄로 남았습니다. #13은 8개 작업, #17은 3개 작업의 본문을 고쳤습니다. #14에서 메모 파일로 바꾼 뒤로는 세션을 새로 시작하거나 컨텍스트가 압축되어도 이 항목을 잃지 않습니다
- **실수 → 규칙 루프** (#21): 리뷰에서 나온 "패키지 경로째 쓴 클래스" 지적을 같은 날 규칙과 reviewer 체크리스트로 만들고, 기존 위반을 #22에서 정리했습니다
- **규칙을 테스트로 강제하기** (#6): ArchUnit이 패키지 의존 규칙을 막고, 리뷰 과정에서 `System.out::println` 메서드 참조를 놓치던 구멍도 찾아 막았습니다. 문서 규칙보다 확실합니다
- **설정 전용 리뷰어** (#9): 코드용 `reviewer` 대신 `context-reviewer`가 지침 사이의 맞물림과 충돌을 봅니다. #9에서 22건 중 17건, #11에서 6건 중 5건을 반영했습니다
- **작업별 리뷰 + PR 전체 리뷰** (#7, #12, #20): 하위 작업마다 리뷰하고 마지막에 전체를 한 번 더 본 PR의 반영률이 높았습니다(#7 22/20, #20 9/9)
- **정답표가 있는 픽스처 PR** (#15, #16): 실제 API 동작을 확인할 고정 입력과 정답표를 만들어 두었습니다. 정답표는 모델 입력에 들어가지 않게 따로 둡니다

### 효과 없었던 것
- **사람 리뷰와 승인 규칙**: 26건 중 사람 리뷰는 1건입니다. PLAYBOOK의 승인 규칙은 운영되지 않았고, 리드타임 중앙값 3분은 사실상 작성자가 직접 머지했다는 뜻입니다
- **역할 순환과 트랙 병렬**: 작성자 1명이 거의 모든 PR을 냈습니다. 역할과 트랙이 전제하는 "여러 사람의 병렬 작업"이 일어나지 않았습니다
- **계획 작성을 에이전트로 분리** (#23 철회): 중복 읽기와 재위임 비용이 Opus로 고정해 얻는 이득보다 컸습니다. 작업이 이미 잘게 쪼개져 있어 이득이 작았습니다. 마지막 주차 오케스트레이션을 설계할 때 같은 비용(수정 때마다 스펙을 다시 읽음)을 피해야 합니다
- **`paths` 규칙 로드** (#9): `editing.md`가 메인 세션에서는 로드되지 않은 경우가 있었습니다. 원인을 찾지 못해 루트 `CLAUDE.md`가 직접 가리키게 했습니다
- **마지막 커밋 재리뷰 누락** (#8, #9, #11, #12): 리뷰 지적을 반영한 마지막 커밋은 다시 리뷰하지 않은 PR이 많습니다
- **문서 PR의 AI 리뷰**: 반영률이 58%로 코드 PR(73%)보다 낮습니다. #4는 10건 중 1건, #25는 4건 중 0건을 반영했습니다. 표기와 범위 밖 서술에 대한 "선택" 지적이 많았습니다

### 가이드·컨텍스트에 반영된 것
- PLAYBOOK 원칙 3번과 방법론 가이드 원칙 4번: "모든 줄을 설명"에서 "검증 증거와 AI 리뷰 결과를 확인하고 책임진다"로 (#25)
- PLAYBOOK 승인 규칙: 브랜치 보호의 승인 필수는 걸지 않고 사람 규칙으로 운영 (#26)
- `docs/guide/task-workflow.md`: 계획 승인, 브랜치 확인 순서, handoff 메모 흐름 (#10, #11, #14), 리뷰 에이전트의 모델 설명 (#19)
- `.claude/rules/context/editing.md`: 설정을 고칠 때 함께 고칠 파일, 리뷰와 실행 확인 규칙 (#9)
- `backend/CLAUDE.md` Gotchas: import 규칙 (#21)
