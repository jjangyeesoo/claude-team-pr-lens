---
name: spec
description: 스펙 폴더에서 아직 없는 다음 문서(requirements.md → design.md → tasks.md → 색인)를 /task가 읽는 형식으로 작성한다. 구현 전에 사용.
disable-model-invocation: true
argument-hint: "<스펙 이름> [requirements|design|tasks]"
---
대상: $ARGUMENTS (첫 값은 `docs/specs/` 아래 스펙 폴더 이름. 둘째 값은 쓸 문서. 없으면 아직 없는 문서 가운데 순서가 가장 앞선 것. 예: `pr-lens-p3-web design`)

스펙 이름이 없으면 `docs/specs/README.md`의 스펙 목록을 보여 주고 멈춘다. 한 번에 문서 하나만 쓰고, 사람이 검토한 뒤 다음 문서로 넘어간다. 이 세션에서는 구현하지 않는다.

## 1. 준비 (항상 먼저)
1. 스펙 폴더의 `README.md`(색인)를 읽는다. "수정 전에 확인할 것", 확정된 결정, 결정 대기 항목을 확인한다. 색인이 가리키는 검토 문서(`docs/spec-review/`)가 있으면 지적 목록을 읽는다.
2. 앞 단계 문서가 없거나 색인이 "아직 고치지 않았다"고 적은 상태면 그 문서부터 다룬다(요구사항의 검토 지적을 반영하지 않고 설계를 쓰지 않는다).
3. `docs/adr/README.md`, 루트와 스택별 `CLAUDE.md`, 관련 `docs/spikes/` 기록을 본다. 앞 단계 스펙(P1, P2)은 색인만 읽고, 이 스펙이 쓰는 타입과 인터페이스가 나오는 절만 제목으로 찾아 읽는다. 통째로 읽지 않는다.
4. 실제 코드를 확인한다. 스펙이 전제하는 클래스, API, 설치된 도구가 저장소에 있는지 보고, 없으면 문서에 "아직 없음"으로 적는다. 추측으로 채우지 않는다.
5. 팀이 정할 것은 AskUserQuestion으로 묻는다(한 번에 최대 4개). 답을 받지 못한 항목은 결정 대기(D-n, G-n)로 남기고 제안값을 적는다. 임의로 확정하지 않는다.

## 2. 문서별 형식
형식의 기준은 `docs/specs/pr-lens-p1-cli/`의 문서다. 절 제목을 그대로 따른다(`/task`와 색인이 제목으로 찾는다).

### requirements.md
- 절: Introduction · Glossary · Requirements · 결정 대기 항목
- `### Requirement N: <이름> (FR-n)` 아래에 **User Story** 한 문장과 `#### Acceptance Criteria` 번호 목록. 인수 기준은 `WHEN … THE <Glossary 용어> SHALL …`, `IF … THEN …`, `FOR ALL …` 문형으로 쓰고 하나가 검증 하나에 대응하게 한다
- 결정 대기 항목은 표(ID `D-n`, 항목, 결정 주체, 이 스펙의 처리)
- 폴더가 없는 새 스펙이면 PRD와 ROADMAP의 해당 단계를 읽고 인터뷰한 뒤 이 문서부터 쓴다

### design.md
- 절: Overview · Architecture · Components and Interfaces · Data Models · Correctness Properties · Error Handling · Testing Strategy
- 패키지 배치와 의존 방향은 ADR을 따른다. 클래스마다 시그니처와 맡은 요구사항 번호를 적는다
- `### Property N: <이름>` 아래에 `*For any* …` 한 문단과 `**Validates: Requirements x.y, …**`
- 요구사항이 정하지 않은 경우는 "요구사항 공백" 표(G-n, 상황, 설계의 처리, 관련 요구사항)에 모은다
- 새 의존성이 필요하면 이유와 버전을 적고 "사람의 승인 필요"로 표시한다. `build.gradle.kts`, `package.json`은 고치지 않는다

### tasks.md
`/task`가 이 형식에 의존한다.
- 절: Overview(선머지 범위, 트랙 사이 의존, PR 단위) · Tasks · Notes · Task Dependency Graph
- Tasks는 `### <트랙> (담당)` 아래에 상위 작업 `- [ ] N. <이름>`, 하위 작업 `  - [ ] N.M <이름>`. 속성·보조 테스트는 `- [ ]* N.M`으로 표시한다
- 상위 작업 하나가 PR 하나다. `src/main` 400줄을 넘을 상위 작업에는 둘째 줄에 `- PR 경계: 1.1 / 1.2~1.3`을 적는다
- 하위 작업 본문 마지막 줄은 `- _Requirements: x.y, …_`. 모든 인수 기준이 작업 하나 이상에, 모든 Property가 `*` 작업 하나에 대응해야 한다
- 트랙이 끝나는 곳과 전체 끝에 Checkpoint 작업을 둔다. 사람이 하는 작업은 제목에 "사람이 수행"이라고 적는다
- Notes에 결정 대기가 막는 작업 표(항목, 막는 작업)와 "완료 조건 최소 경로와 밀리면 미룰 것"을 적는다
- Task Dependency Graph는 `{"waves": [{"id": 0, "tasks": ["1.1"]}, …]}` JSON. 모든 말단 작업이 정확히 한 wave에 들어간다
- backend와 web이 같은 API를 바꾸는 작업은 같은 PR 경계 안에 둔다(루트 `CLAUDE.md` Cross-stack rules)

## 3. 마무리
1. 스펙 폴더의 `README.md`를 고친다: 상태와 날짜, 규모(요구사항·인수 기준·속성·말단 작업 수), "문서와 절" 표, "트랙, 패키지, 작업" 표(`tasks.md`를 썼을 때), 결정 대기 표와 제안값.
2. `docs/specs/README.md`의 그 스펙 행(문서, 상태)을 고친다.
3. 스스로 검사한다: 작업 번호와 wave의 일치, `_Requirements_`가 가리키는 번호의 존재, 색인의 개수. 어긋난 곳은 고친 뒤 보고한다.
4. 쓴 문서의 요약, 새로 생긴 결정 대기 항목, 앞 단계 스펙에 요구하는 변경, 다음에 쓸 문서를 보고한다.

커밋은 하지 않는다. 스펙 문서 변경은 `docs/<slug>` 브랜치에서 PR로 낸다.

## 이미 완성된 스펙에 기능을 더할 때
새 파일을 만들지 않는다. 그 단계의 스펙 폴더에서 `requirements.md`에 요구사항(또는 인수 기준)을, `design.md`의 해당 절에 설계를, `tasks.md`에 작업과 wave를 더하고 색인의 개수를 고친다. 기존 번호는 바꾸지 않고 뒤에 이어 붙인다(브랜치 이름과 PR이 번호를 가리킨다).
