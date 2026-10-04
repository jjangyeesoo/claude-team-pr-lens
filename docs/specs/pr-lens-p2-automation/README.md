# P2 자동화 스펙 색인

PR이 열리면 webhook으로 리뷰를 자동 실행하고, 결과를 저장하고, PR에 게시합니다(PRD FR-7~11과 FR-12 일부, ROADMAP 3주차 M2).

- 상태 (2026-10-04): 스펙 검토의 지적, P1 변경분, 독립 검증 네 건의 지적 중 선택지가 없는 것을 반영함. 팀 승인 전. P1 코드가 머지된 뒤에 구현
- 규모: 요구사항 20개(인수 기준 207개), 속성 27개, 말단 작업 107개(선택 `*` 25개). 3명이 한 주에 전부 끝내기는 어려우므로 tasks.md Notes의 "완료 조건 최소 경로와 밀리면 미룰 것"을 먼저 봅니다
- 읽는 법은 [../README.md](../README.md). 절은 제목으로 찾습니다. design.md는 약 19만 바이트라 필요한 절만 읽습니다

## 문서와 절

| 문서 | 절 |
|---|---|
| [requirements.md](requirements.md) | Glossary(Run_Error_Kind, Publish_Error_Kind, Ack_Reason 포함) · Requirement 1~6 (T1: webhook 수신, 서명, 허용 저장소, 비동기 실행, 중복 방지, App 인증) · 7~10 (T2: 저장 스키마, usage, `--save`, 조회 API) · 11~15 (T3: 요약·라인 코멘트, 심각도 하한, head 변경, 채택/기각) · 16~20 (공통: 패키지 경계, 응답 시간, 비밀정보, 설정, Actions 대체 경로) · 결정 대기 항목 |
| [design.md](design.md) | Overview(작업 환경 확인 결과, 결정 대기 권고) · Architecture(패키지 배치, 의존 규칙, 시퀀스 4개) · Components and Interfaces: T1 webhook(요청 처리 순서와 응답, BoundedBodyReader, SignatureVerifier, Job_Runner(제한 시간과 취소 포함), GitHub App 인증) / T2 store·query(ReviewStore 인터페이스, 저장 레코드, DDL 초안, 시도 번호와 동시성, 트랜잭션 경계, 마이그레이션, DB 오류 치환, 메모리 구현, CLI `--save`, Query_API, OpenAPI 문서와 CI 계약 검사, 바인딩 주소와 시작 경고, 비용과 usage 저장 규칙) / T3 publish(GitHub 호출 인터페이스, 게시 주체 식별, CommentPublisher, Finding_Fingerprint, PublishPlanner와 Publish_Outcome, Line_Comment 단계, Summary_Comment 본문, FeedbackCollector) / 공통(패키지 경계 검사, 테스트 픽스처, 응답 시간 측정, 비밀정보, 설정 확장, 서버 실행, GitHub Actions 대체 경로 `--publish`, 설치 문서에 적을 항목). 트랙마다 "요구사항 공백" 절이 있음 · Data Models · Correctness Properties(1~27) · Error Handling · Testing Strategy(응답 시간 측정 절차, 3주차 spike 체크리스트 포함) · 요구사항 공백 요약 |
| [tasks.md](tasks.md) | Overview(선머지 범위, Docker 테스트, 트랙 사이 의존) · Tasks · Notes · Task Dependency Graph |

## 트랙, 패키지, 작업

| 트랙 (담당) | 패키지 | 작업 |
|---|---|---|
| 환경 준비와 사전 확인 (킥오프 전) | GitHub 원격, DB, GitHub App, smee, `docs/spikes/` | 0 |
| 공유 (리드 A. 3은 B, 1.3과 4는 C) | `store`·`github`·`publish` 인터페이스, `execution`, `config`, `support`, `server` | 1~5. 월요일 선머지는 1.1, 2.1~2.4, 2.6, 2.7 |
| T1 webhook (A) | `webhook`, `github`(App 인증), `execution`, `server` | 6~13 |
| T2 store·query (B) | `store`, `store.db`, `store.memory`, `query`, `cli`(`--save`) | 14~19 |
| T3 publish (C) | `publish`, `github`(게시 호출), `cli`(`--publish`, `feedback sync`) | 20~25 |
| 컨텍스트 (C) | `.claude/rules/`, `backend/CLAUDE.md`, `reviewer.md`, `new-endpoint` 스킬 | 28 |
| 통합 (전원) | 픽스처, CI, 설치 문서, 실제 webhook 리허설 | 26~27 |

## 확정된 결정
- 패키지 구조: [ADR 0003](../../adr/0003-role-based-flat-packages.md)
- 에러 형식 `ErrorResponse`, 조회 API 경로 `/api/v1/`: [ADR 0008](../../adr/0008-keep-api-conventions.md)

## 결정 대기 (팀이 정함)

원본은 requirements.md "결정 대기 항목"(D)과 design.md "요구사항 공백 요약"(G)입니다. 결정 전에 무엇으로 구현하는지는 항목에 따라 다릅니다.

- **D-1~D-12, G-1~G-11**: 제안이 현재 스펙의 동작입니다. 제안값으로 구현합니다
- **D-13~D-18, G-12**: 제안은 스펙에 반영하지 않은 대안입니다. 결정 전에는 요구사항 문구대로 구현합니다
- **D-2, D-8의 서버 담당**: 제안값으로 대신할 수 없습니다. 작업 1.2와 0.1이 기다리므로 킥오프 전에 확정합니다

| ID | 내용 | 제안 |
|---|---|---|
| D-1 | 채택/기각 수집 방식 | 라인 코멘트의 👍/👎 리액션 |
| D-2 | DB와 테스트 전략 (ADR-0006) | PostgreSQL + Flyway + Testcontainers. Docker 테스트는 `@Tag("docker")`로 분리 |
| D-3 | draft PR 리뷰 여부 | draft도 리뷰 |
| D-4 | 비동기 실행 방식 | 서버 안 작업자 풀 + DB의 `queued` 상태 |
| D-5 | 저장·게시 실패 시 CLI 종료 코드 | 4 |
| D-6 | CLI 단독 실행에 허용 목록 적용 | 미적용 |
| D-7 | 지문 구성 | 줄 번호 제외. 같은 메시지의 다른 줄 지적이 보이지 않는 한계를 함께 정함 |
| D-8 | 조회 API 노출 범위, 도그푸딩 서버를 누가 띄울지, 쌓인 데이터의 공유 방법 | `127.0.0.1` 바인딩, 서버는 한 대, `pg_dump`로 공유 |
| D-9 | `superseded`의 Active_Run 포함 | 포함 |
| D-10 | 채택률 분모 | P3에서 확정 |
| D-11 | 허용 판정 기준 | 저장소 이름 |
| D-12 | Publish_Error 개수 | 하나 |
| D-13 | 게시 실패 뒤의 복구 | (미반영) 재전송 때 게시만 다시, 실패 코멘트 게시도 완료 시각 기록 |
| D-14 | 유실 이벤트 재시작, `interrupted`의 횟수 포함 | (미반영. 현재는 횟수에 포함) 재전송 방법을 문서화하고 `interrupted`는 횟수에서 뺌 |
| D-15 | Actions 대체 경로의 저장, 전환 기준, 완료 확인 방법 | (미반영. 현재는 저장되지 않고 워크플로는 문서 예시뿐) JSON 아티팩트를 로컬에서 가져오기. 수요일 스모크가 실패하면 전환 |
| D-16 | webhook 경로와 조회 규칙, ADR 0002의 예외(`query` 전용 처리기, webhook의 `ErrorResponse` 직접 생성) | (현재 동작과 같음) webhook은 `/api/v1/` 밖, 페이지네이션은 P3, 두 예외 인정 |
| D-17 | P3가 요구하는 변경 16항목 (목록 봉투, `RunDto` 세 필드, 단건 조회 둘, 오류 메시지 노출 등) | (미반영) 작업 17.1·17.3 전(수요일)에 반영 여부 결정 |
| D-18 | `feedback sync`에서 제외할 리액션 주체 (G-12) | (미반영. 현재는 게시 주체) 코멘트 작성자 |
| G-1~G-4, G-6~G-9, G-11 | 설계가 메운 요구사항 공백 | 요구사항에 `(G-n 제안)`으로 옮김 (G-5는 설계로 해결) |
| G-10 | 진행 중 HTTP 호출을 끊을 수 없는 경우 | spike 4 결과에 따름 |
| G-13 | 재사용한 라인 코멘트가 head 변경 뒤 옛 커밋 줄에 outdated로 남음 | 설치 문서에 한계로 기록 |

D-13과 D-14는 도그푸딩 중에 바로 드러나는 문제라 3주차 킥오프에서 먼저 정하는 것이 좋습니다. D-17은 조회 API 작업(17.1, 17.3) 전에, D-15의 전환 기준은 수요일 스모크 전에 정합니다.

## 구현 전에 확인할 것
- **환경 (작업 0.1, 킥오프 전)**: GitHub 원격과 CI 첫 실행, DB(Docker 또는 PostgreSQL 직접 설치), GitHub App 등록, smee. 2026-10-04 기준 개발 PC에 Docker가 없고 원격 저장소도 없습니다. DB가 없으면 서버가 시작하지 않고 `JdbcReviewStore`를 검증할 수 없습니다
- **킥오프 전에 확정할 결정**: D-2(DB와 테스트 전략. ADR-0006이 아직 없고 작성은 작업 0.1)와 D-8의 서버 담당
- **spike**: design.md "3주차 spike 체크리스트"의 "실험 필요" 항목(작업 0.2~0.4). spike 10(게시 주체)은 T3 구현 전에, spike 15(smee의 바이트 보존)는 킥오프 전에 끝냅니다
- **도그푸딩 서버는 한 대**: 누구의 PC에서 띄울지 킥오프에서 정합니다(D-8)
- **P1에 의존하는 것**: `PrFetcher.fetch(RepoRef, int)`, 생성자 주입, `Attempt`, `CostCalculator.estimate`, sealed가 아닌 `PrLensException`(P1 설계 "공유 경계"). P2는 P1의 `HttpGitHubClient` 생성자, `CliPipeline.preflight`, `MaskingPrintStream`, `ConfigLoader` 반환형, `PullRequestUrl`을 고칩니다(작업 2.3, 2.6, 3.1, 4.1)

## 관련 문서
- 이 스펙을 고친 근거: `docs/spec-review/`의 01, 05와 커밋 `f67a6b1`. 함께 근거로 쓴 03과 04는 반영 뒤 지웠습니다(원문은 `f67a6b1`까지의 이력)
- P3가 이 스펙에 요구하는 것: [P3 색인](../pr-lens-p3-web/README.md)
