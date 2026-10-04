# 작업 인수인계 (2026-10-04)

이 저장소를 PR Lens 개발에 들어갈 수 있는 상태로 만드는 작업의 진행 기록입니다. 다음 세션은 이 문서부터 읽으면 됩니다. 스펙 수정이 끝나면 `docs/spec-review/`와 함께 지워도 됩니다.

## 지금까지 한 것

1. **폴더 통합**: 따로 있던 세 폴더(스타터 코드, Kiro 스펙, 기획 문서)를 이 저장소 하나로 합쳤습니다.
   - 스펙 → `docs/specs/pr-lens-p1-cli/`, `pr-lens-p2-automation/`, `pr-lens-p3-web/`
   - 기획 문서 → `docs/study-project/`(PRD, ROADMAP, PLAYBOOK), `docs/claude-code-team-methodology.md`
   - Kiro 전용 `.config.kiro` 세 개와 `.kiro/` 폴더는 지웠습니다.
   - 이동으로 깨진 경로 문구와 링크를 고쳤습니다(P2·P3 요구사항 7행, ROADMAP, 방법론 가이드, PRD 머리말, README).
2. **원본 저장소 흔적 정리**: `.github/`(CI 워크플로, PR 템플릿)를 지웠습니다. `.git`은 처음부터 없었습니다.
3. **스타터 검증**: backend `gradlew spotlessCheck test` 통과, web `npm ci` 후 `npm run verify` 통과(테스트 5개).
4. **스펙 검토**: 서브에이전트 8개로 P1·P2(요구사항·설계·작업 목록)와 P3(요구사항)를 검토하고 결과를 `docs/spec-review/`에 저장했습니다. 지적은 약 180건이고, 지적마다 위치와 수정안이 있습니다.

## 현재 상태

- **코드**: 스타터의 메모 샘플 그대로입니다(패키지 `com.example.starter`). PR Lens 구현은 없습니다.
- **스펙**: 세 건 모두 검토만 했고 **아직 고치지 않았습니다.**
  - P1, P2: 그대로는 구현에 들어갈 수 없고 수정하면 가능
  - P3: 요구사항만 있음. 작은 수정 후 설계 가능
- **환경**: JDK 17, Node 24, git 설치됨. `web/node_modules` 설치됨. **Docker 없음**(P2의 PostgreSQL, Testcontainers에 필요).
- **git 저장소가 아닙니다.** 그래서 Stop hook 자동 검증(`VerifyOnStop`)이 동작하지 않습니다.
- **CI와 PR 템플릿이 없습니다.** ROADMAP, PLAYBOOK의 지표 기록, `/pr-ready` 스킬이 이것에 의존합니다.

## 앞으로 할 것

### 1. 스펙 수정 (다음 작업)

`docs/spec-review/README.md`부터 읽습니다. P1 → P2 → P3 순서를 권합니다(P2·P3가 P1의 타입과 이름을 가져다 씁니다).

- **결정 없이 고칠 수 있는 것**: 틀린 참조 번호, 순서가 뒤바뀐 작업, 사실 오류(캐시 단가, 스키마의 `minimum`·`minLength`, GitHub rate limit 대기 규칙, `stop_reason` 값), "스타터가 없다"는 서술, 없는 Gradle 경로 `:backend:`, 모호한 문구.
- **결정이 필요한 것** (아직 확정되지 않음, 아래는 권장안)

  | 결정 | 권장 | 범위 |
  |---|---|---|
  | 에러 형식 | `ErrorResponse` 유지 (ADR 0002 그대로) | P2, P3 |
  | API 경로 | `/api/v1/` 적용 (저장소 규칙 그대로) | P2, P3 |
  | 패키지 구조 | 스펙의 평면 패키지를 채택하고 ADR 0001을 새 ADR로 대체 | P1, P2 |
  | P3의 404와 로딩 표시 | 404가 필요한 화면은 로딩 표시를 두지 않음 | P3 |
  | P3 통계의 중복 집계 | PR별 최신 실행만 집계 | P3 |

- **팀이 정할 것**: 스펙의 결정 대기 항목(P1 14건, P2 12건, P3 12건)과 ADR 0003~0006. 임의로 확정하지 말고 스펙에 "대기"로 둡니다.
- P3는 요구사항을 고친 뒤 설계와 작업 목록을 새로 만들어야 합니다.

### 2. 저장소 준비

상세 순서는 `docs/spec-review/01-claude-usability.md`의 "착수 전 체크리스트"에 있습니다.

1. `git init`과 첫 커밋, GitHub 저장소 생성
2. `.github/workflows/ci.yml`과 PR 템플릿 다시 만들기
3. ADR 작성 (0001 대체, 0003~0006)
4. 패키지를 `com.prlens`로 변경
5. `CLAUDE.md`, `backend/CLAUDE.md`, `README.md`, rules, reviewer 에이전트를 PR Lens 기준으로 다시 쓰기 (지금은 메모 샘플 기준)
6. 스펙 폴더별 색인과 `/task` 스킬 추가
7. 가장 먼저 할 spike: jqwik이 JUnit 6에서 도는지 (안 돌면 속성 테스트 계획이 막힘)

### 3. 구현

1주차 완료 조건(ROADMAP)을 채운 뒤 P1 작업 1번부터 시작합니다.

## 다음 세션을 시작할 때

- **이 폴더(`claude-team-prlen`)에서 `claude`를 실행합니다.** 그래야 `.claude/settings.json`의 권한 규칙과 hook이 적용됩니다. 처음에는 폴더 신뢰를 묻습니다.
- 파일을 고칠 때 `ProtectFiles` hook이 `.env`, lock 파일, gradle wrapper 수정을 막고, `build.gradle.kts`와 `package.json` 수정은 승인을 묻습니다.
- 스펙 파일은 크니 통째로 `@` 참조하지 말고 필요한 절만 읽습니다(P2 `design.md`는 약 15만 바이트).
- `docs/spec-review/`의 줄 번호는 검토 시점 기준입니다. 스펙을 고치기 시작하면 어긋나므로 절 제목과 요구사항 번호로 찾습니다.
