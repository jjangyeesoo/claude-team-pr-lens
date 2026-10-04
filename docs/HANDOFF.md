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
- **스펙**: P1은 고쳤고(아래 "P1 스펙 수정 내역"), P2와 P3는 검토만 했고 **아직 고치지 않았습니다.**
  - P2: 그대로는 구현에 들어갈 수 없고 수정하면 가능
  - P3: 요구사항만 있음. 작은 수정 후 설계 가능
- **환경**: JDK 17, Node 24, git 설치됨. `web/node_modules` 설치됨. **Docker 없음**(P2의 PostgreSQL, Testcontainers에 필요).
- **git 저장소로 만들었습니다**(`main`, 원격 없음). Stop hook(`VerifyOnStop`)이 동작할 조건은 됐지만 실제로 도는지는 아직 확인하지 않았습니다. Claude Code 보호 hook과 권한 규칙도 보강했습니다(커밋 `4589626`).
- **CI 워크플로와 PR 템플릿은 작성했지만 GitHub 원격이 없어 CI를 실행해 보지 못했습니다.**

## 앞으로 할 것

### 1. 스펙 수정 (다음 작업)

`docs/spec-review/README.md`부터 읽습니다. P1 → P2 → P3 순서를 권합니다(P2·P3가 P1의 타입과 이름을 가져다 씁니다).

- **결정 없이 고칠 수 있는 것**: 틀린 참조 번호, 순서가 뒤바뀐 작업, 사실 오류(캐시 단가, 스키마의 `minimum`·`minLength`, GitHub rate limit 대기 규칙, `stop_reason` 값), "스타터가 없다"는 서술, 없는 Gradle 경로 `:backend:`, 모호한 문구.
- **결정이 필요했던 것** (2026-10-04 확정)

  | 결정 | 확정 내용 | 범위 |
  |---|---|---|
  | 에러 형식 | `ErrorResponse` 유지 (ADR 0002 그대로) | P2, P3 |
  | API 경로 | `/api/v1/` 적용 (저장소 규칙 그대로) | P2, P3 |
  | 패키지 구조 | 스펙의 평면 패키지를 채택하고 ADR 0001을 대체 ([ADR 0003](adr/0003-role-based-flat-packages.md)) | P1, P2 |
  | P3의 404와 로딩 표시 | 404가 필요한 화면은 로딩 표시를 두지 않음 | P3 |
  | P3 통계의 중복 집계 | PR별 최신 실행만 집계 | P3 |

- **팀이 정할 것**: 스펙의 결정 대기 항목(P1 14건, P2 12건, P3 12건)과 ADR 0004~0006. 임의로 확정하지 말고 스펙에 "대기"로 둡니다.
- P3는 요구사항을 고친 뒤 설계와 작업 목록을 새로 만들어야 합니다.

#### P1 스펙 수정 내역 (2026-10-04, 브랜치 `docs/p1-spec-fix`)

번호는 `docs/spec-review/`의 지적 번호입니다.

- **02번(P1 내부 정합성) 25건**
  - 반영: 1, 2, 3, 5, 6, 7, 8, 9, 12~22, 24, 25
  - 잠정 처리만 하고 결정 대기로 남김: 10(제외 사유는 표시, `incomplete` 여부는 G-8), 17(경고 후 rule 생략, G-9)
  - 결정 대기로만 기록: 4(D-8 저장소 한정), 11(D-4에 요약 전용 모드 종료 코드 추가), 23(G-11 컨텍스트 크기 상한)
- **05번(외부 사실)**: 스키마의 `minimum`·`minLength`, GitHub rate limit 대기 규칙(요구사항 21.9~21.11), 캐시 읽기 단가 $0.20, `stop_reason`, JUnit 6과 jqwik spike를 반영. Claude spend limit 429, 180초 제한과 스트리밍, refusal fallback은 ADR-0005에서 정할 항목으로 설계 문서 끝에 기록
- **04번 (B) P1 ↔ P2 연계 중 P1 쪽**: B-2(생성자 시그니처), B-4(`PrFetcher.fetch(RepoRef, int)`), B-9(예외를 sealed가 아니게, `support`에 정의), B-10, B-12(`CostCalculator.estimate`), B-13(대기 시간 `Duration`)
- **직접 확인한 것**: sealed 클래스의 하위 타입을 다른 패키지에 두면 JDK 17에서 컴파일 오류(`javac` 실행), 스타터의 JUnit이 6.0.3(Gradle 캐시), GitHub rate limit 규칙(공식 문서), Opus 5.5 단가·effort 값·구조화 출력 제약(Anthropic 문서). 나머지 외부 사실은 검토 문서의 요약을 그대로 옮겼습니다.

**독립 검증 후 보완 (2026-10-04)**

별도 컨텍스트의 검증 에이전트 둘이 저장소 구성과 P1 스펙을 다시 봤고, 그 지적을 스펙에 반영했습니다.

- 선머지를 작업 1.1, 2.1~2.3으로 줄이고 의존성 추가(1.2)를 분리. T2 순수 함수 4개의 시그니처를 2.3 스텁으로 선머지
- 작업 추가: 7.7·13.4(수요일 실제 API 스모크), 20.4(목요일 데모 리허설), 23.1·23.2(컨텍스트 담당 A의 rules 정리)
- 재시도 실행기 계약 `Attempt<T>`, `LlmApiException`, `FileFetch.TooLarge`를 선머지 타입에 추가. 엔진 쪽 경고는 CLI가 `ReviewResult`에서 유도
- 골든 픽스처 테스트는 스냅샷·컨텍스트를 주입해 엔진과 출력만 실행하도록 정리(제안. 스펙 리뷰에서 확인 필요)
- system 프롬프트에 `basis.ref` 작성 규칙 명시, CLI용 `logback.xml`, 실행 jar의 `mainClass` 지정
- tasks.md Notes에 "데모 최소 경로와 밀리면 미룰 것" 추가
- spike 기록: [spikes/2026-10-04-build-stack.md](spikes/2026-10-04-build-stack.md) (jqwik 1.10.1과 JUnit 6.0.3 동작 확인 등)
- ADR 번호: PRD·ROADMAP이 예약한 "0003 저장소 구조"는 0007로 옮김

**P2를 고칠 때 P1 변경 때문에 함께 봐야 하는 것**

- `IncompleteDetails.rawResponseExcerpt`가 `ChunkIssue.rawResponseExcerpt`로 옮겨졌고, `ReviewResult`에 `excludedFileDetails`가 추가됐습니다(저장 스키마, DTO).
- `RetryListener.onRetry`의 대기 시간이 `Duration`입니다(P2의 ms 합 집계).
- `PrLensException`은 sealed가 아니므로 P2 예외가 상속할 수 있습니다.
- 04번 (B)의 P2 쪽 지적은 그대로 남아 있습니다: B-1, B-3, B-5, B-6, B-7, B-8, B-11.
- 속성이 44개가 됐습니다(Property 44 추가). 작업 1~4의 "선머지" 범위가 작업 1.1, 2.1~2.3으로 줄었습니다.

### 2. 저장소 준비

상세 순서는 `docs/spec-review/01-claude-usability.md`의 "착수 전 체크리스트"에 있습니다.

1. GitHub 저장소 생성과 push (`git init`과 첫 커밋은 완료)
2. `.github/workflows/ci.yml`과 PR 템플릿은 작성함. 원격에 올린 뒤 CI가 실제로 통과하는지 확인하고 브랜치 보호의 필수 체크(`backend`, `web`)를 설정
3. ADR 작성 (0004~0007. 0001을 대체하는 0003은 작성함. 0004와 0005는 P1 작업 1.2의 의존성 추가 전에 필요)
4. 패키지를 `com.prlens`로 변경
5. `.claude/rules/backend/api-design.md`, `new-endpoint`·`new-page` 스킬을 PR Lens 기준으로 다시 쓰기 (P2, P3 전에. `README.md`, 루트 `CLAUDE.md`, `backend/CLAUDE.md`, `testing.md`, reviewer 에이전트, `/pr-ready`는 2026-10-04에 고침. PR 단위는 "상위 작업 하나 = PR 하나"로 정함)
6. 스펙 폴더별 색인과 `/task` 스킬 추가
7. 남은 spike: Anthropic Java SDK 실제 호출(P1 작업 13.4). jqwik과 JUnit 6 호환은 확인함(`docs/spikes/`)

### 3. 구현

1주차 완료 조건(ROADMAP)을 채운 뒤 P1 작업 1번부터 시작합니다.

## 다음 세션을 시작할 때

- **이 폴더(`claude-team-prlen`)에서 `claude`를 실행합니다.** 그래야 `.claude/settings.json`의 권한 규칙과 hook이 적용됩니다. 처음에는 폴더 신뢰를 묻습니다.
- 파일을 고칠 때 `ProtectFiles` hook이 `.env`, lock 파일, gradle wrapper 수정을 막고, `build.gradle.kts`와 `package.json` 수정은 승인을 묻습니다.
- 스펙 파일은 크니 통째로 `@` 참조하지 말고 필요한 절만 읽습니다(P2 `design.md`는 약 15만 바이트).
- `docs/spec-review/`의 줄 번호는 검토 시점 기준입니다. 스펙을 고치기 시작하면 어긋나므로 절 제목과 요구사항 번호로 찾습니다.
