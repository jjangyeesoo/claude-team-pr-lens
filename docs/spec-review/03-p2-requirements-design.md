# 03. P2 요구사항 ↔ 설계

[← 목차](README.md)

대상: `P2-req`, `P2-des`, `docs/study-project/PRD.md`, `ROADMAP.md`. 이 문서에서 `R`은 `P2-req`, `D`는 `P2-des`입니다.

## 총평

**판정: 수정 후 구현 가능.** 지금 그대로는 구현에 들어가기 어렵습니다. 치명 등급은 없지만 중요 12건이 있습니다.

요구사항 20개(수용 기준 205개)는 설계에 거의 빠짐없이 대응되고, 중복 방지 트랜잭션·서명 검증·토큰 캐시 설계는 견고합니다. 막는 문제는 세 묶음입니다.

- **스타터와의 충돌**: 설계는 스타터가 없다고 전제하지만 실제로는 `claude-team-prlen/`에 있고, 에러 형식·URL 규칙이 설계와 다릅니다.
- **게시 경로의 멱등성과 복구**: POST 재시도로 코멘트가 중복될 수 있고, 게시 실패 후 복구 경로가 없습니다.
- **미반영 수정과 미결 항목**: 설계가 제안한 요구사항 수정 10건이 `requirements.md`에 반영되지 않았고, D-1~D-12 전부와 spike 13건이 열려 있습니다.

T2(store)는 거의 바로 착수할 수 있고, T1·T3는 아래 1~12번을 정리한 뒤 착수하는 것이 안전합니다.

## 발견 사항

### 중요

**1. [모순] 표준 에러 응답 형식**
- 위치: `D:20-22`, `D:775`, `D:1108`
- 문제: 설계는 "스타터가 없습니다 … `ProblemDetail`을 가정"이라고 적었습니다. 실제 `repo/docs/adr/0002-standard-error-response.md`는 `ErrorResponse { code, message, details }`를 채택했고 `ProblemDetail`은 "고려한 대안"으로 기각했습니다.
- 영향: 요구사항 1.12, 4.13, 10.7, 10.8, 10.14, 18.6과 OpenAPI 스키마 전부.
- 수정안: `ErrorResponse`와 UPPER_SNAKE_CASE `code` 목록으로 다시 쓰고, `invalidPathVariables`는 `details`로 옮깁니다.

**2. [모순] 팀 API 규칙과 패키지**
- 위치: `R:222-225`, `D:59`, `D:22`
- 문제
  - 경로가 `/api/repositories…`인데 스타터의 `.claude/rules/backend/api-design.md`는 "`/api/v1/<복수형-kebab-case>`"와 목록 페이지네이션을 요구합니다.
  - 기본 패키지를 `com.prlens`로 가정했지만 스타터는 `com.example.starter`입니다(ROADMAP 1주차에 개명 예정).
  - `query`·`server` 패키지는 요구사항 16.1에 없습니다.
- 수정안: 경로를 `/api/v1/`로 바꾸거나 규칙 예외를 ADR로 남기고, 16.1에 두 패키지를 추가합니다. 도그푸딩 때 자기 규칙 위반으로 지적될 부분입니다.

**3. [설계 결함] 비멱등 POST 재시도로 코멘트 중복**
- 위치: `D:847`, `D:937`, `D:1008`
- 문제
  - "모든 메서드는 P1 `RetryExecutor`를 그대로 거칩니다"인데, P1 요구사항 21.1은 5xx·연결 실패·제한 시간 초과를 재시도합니다.
  - GitHub가 처리한 뒤 응답만 유실되면 `createIssueComment`·`createReview`가 두 번 실행됩니다.
  - 요구사항 11.2는 중복을 지우지 않으므로 Summary가 영구히 2개가 되고, 12.14도 깨집니다.
  - `createReview` 성공 후 `listReviewCommentsOfReview`가 실패하는 경우의 동작도 없습니다(`D:938`).
- 수정안: POST는 자동 재시도하지 않고 "마커 재조회 후 없을 때만 재전송"으로 바꿉니다. 코멘트 ID 조회 실패 시 처리도 명시합니다.

**4. [누락] 게시 실패 후 복구 경로 없음**
- 위치: `R:125`, `R:252`, `D:435`, `D:893`
- 문제
  - 요구사항 4.14는 Publish_Error가 없는 Review_Run만 재게시하고, 같은 SHA 재전송은 `duplicate`입니다. 따라서 `succeeded`인데 게시되지 않은 상태에서 빠져나갈 길이 없습니다.
  - `failed`의 실패 코멘트도 복구 대상이 아닙니다.
  - `recordPublished` 가드가 `succeeded·incomplete`뿐이라 실패 코멘트 게시 성공 시 4.11의 게시 완료 시각이 기록되지 않습니다.
- 수정안: 재게시 명령(`prlens publish --run <id>`)을 두거나 재전송 시 미게시 Review_Run을 게시만 다시 하게 하고, 가드에 `failed`를 포함할지 정합니다.

**5. [누락] Actions 대체 경로에서 저장이 안 됨**
- 위치: `R:11`, `D:1142`, `D:1177`
- 문제
  - 요구사항 도입부는 "요구사항 1~2는 연기하고 나머지 요구사항은 유지"라고 합니다.
  - 그러나 워크플로 예시는 `--publish` 단독이고 저장소는 "`InMemoryReviewStore`(프로세스 종료 시 소멸)"입니다.
  - DB는 로컬 Docker Compose + `127.0.0.1`(D-2, D-8)이라 호스팅 러너에서 닿지 않습니다.
- 영향: 대체 경로에서는 FR-9, FR-11(15.10이 저장된 PR을 요구), 조회 API 데이터가 모두 비고, 4주차 완료 조건인 "3주차부터 쌓인 실제 리뷰 데이터"도 깨집니다.
- 수정안: 러너에서 JSON 아티팩트를 남기고 로컬에서 가져오는 명령을 두거나, 대체 경로에서 연기되는 요구사항을 명시합니다.

**6. [모순] 공백 요약의 요구사항 수정이 미반영**
- 위치: `D:1578-1592`
- 문제
  - "요구사항 수정" 열 10건(4.5, 4.3, 7.6, 7.17, 9.9, 20장, 8.5, 10.6, 12.11, 15.2)이 `requirements.md`에 없습니다.
  - 설계는 이미 달리 동작합니다: `installation_id` 컬럼(G-3), `head_moved`(G-1), 스냅샷 전 실패 미저장(G-6).
  - G-12는 "결정 전까지 요구사항 문구대로 구현"이라, 개인 PAT로 `feedback sync`를 돌리면 실행자의 👍/👎가 빠져 채택률이 왜곡됩니다.
- 수정안: 승인 전에 10건을 `requirements.md`에 반영하고 G-12를 확정합니다.

**7. [설계 결함] 5초 응답 보장 불가**
- 위치: `R:70`, `D:258`, `D:625`
- 문제
  - "DB 호출 두세 번 … 쿼리 제한 시간 3초"는 문장 단위 제한입니다. `registerAutomatedRun`은 5~6문장에 `FOR UPDATE` 대기까지 있어 최악 합계가 5초와 GitHub의 10초를 넘습니다.
  - 커넥션 획득 제한, 본문 읽기 시간 제한이 없습니다.
  - 서명 검증 전에 25MB를 메모리에 올립니다(`D:263-264`).
- 수정안: 요청 전체 데드라인(예: 3초)과 `lock_timeout`, 풀 `connectionTimeout`을 명시합니다.

**8. [설계 결함] 제한 시간 취소와 완료의 경합**
- 위치: `D:332`, `D:342-344`
- 문제
  - 토큰 확인 후 `completeRun`으로 넘어가는 사이 타이머가 발화하면 작업자 스레드가 인터럽트된 채 저장·게시를 진행합니다.
  - `completeRun` 저장 실패 시 "`running`인 채로 끝냅니다"(7.15)인데 타이머가 살아 있어 나중에 `failRun(timeout)`과 실패 코멘트가 나갑니다. 7.15의 "다음 시작 시 `interrupted`"와 어긋납니다.
- 수정안: 완료와 취소를 하나의 CAS 상태(`RUNNING → FINISHING | CANCELLED`)로 결정하고, 저장 실패 시 타이머 처리를 명시합니다.

**9. [설계 결함] 유실된 이벤트의 재시작 수단 없음**
- 위치: `R:72`, `R:124`, `R:119`, `R:126`, `D:176`
- 문제
  - 설계가 "GitHub는 실패한 전달을 자동으로 다시 보내지 않으므로"라고 인정합니다. 그런데 503(대기열 가득, store 오류), 서버 중단 중 이벤트, `interrupted`는 모두 수동 재전송에만 의존합니다.
  - `interrupted`가 시도 횟수에 포함되어, 재시작이 잦은 로컬 개발에서 3회 만에 `max_attempts_reached`가 됩니다.
- 수정안: 수동 트리거(CLI 또는 재전송 스크립트)를 범위에 넣고, `interrupted`를 횟수에서 뺄지 결정합니다.

**10. [설계 결함] `actions` Review_Run 고아 상태**
- 위치: `D:809`, `D:1150`
- 문제
  - G-7은 수동 SQL로 정리하라는 제안뿐입니다.
  - `--save`에서 `completeRun`이 실패하면 "임시 `InMemoryReviewStore`에 옮겨 게시"하므로 DB에는 `running`이 영구히 남고 그 SHA는 계속 `duplicate`입니다.
- 수정안: 작업 제한 시간을 넘긴 `actions`의 `queued`/`running`을 중복 판정에서 실패로 취급합니다.

**11. [모호] 게시 주체 식별이 spike에 의존**
- 위치: `D:855-860`
- 문제: Summary 하나 유지(11.1), Line_Comment 재사용(12.4), 리액션 제외(15.2)가 모두 `{slug}[bot]` login 가정에 달려 있습니다. 틀리면 실행마다 Summary가 새로 생깁니다.
- 수정안: 구현 전에 spike 10을 끝내고, 숫자 user ID 비교를 기본으로 합니다.

**12. [설계 결함] 같은 지문의 다른 줄 지적이 PR에서 사라짐**
- 위치: `R:269`, `D:926-929`
- 문제
  - 지문에 `line`이 없어, 같은 파일에서 같은 메시지로 여러 줄을 지적하면 최소 순번만 게시됩니다.
  - 나머지는 `duplicate_in_run`으로 라인 밖 목록에도 들어가지 않습니다.
- 수정안: 실행 내 중복 판정에는 `line`을 포함하거나, 대표 코멘트 본문에 다른 줄 번호를 나열합니다(D-7 결정에 포함).

### 경미

**13. [모순] 요구사항 15.6의 일시 위반**
- 위치: `R:320`, `D:436-437`, `D:257`
- 문제: `recordFindingOutcomes`가 재사용한 Line_Comment의 기존 Feedback_State를 새 행에 복사하지 않습니다. 갱신이 등록 전에 트리거되어 마지막 push 이후 리액션은 `closed` 때만, close 이후 리액션은 수동 sync로만 수집됩니다.
- 수정안: 재사용 시 상태를 복사하고 수집 시점 한계를 문서화합니다.

**14. [누락] 기각 사유 미수집**
- 위치: `PRD.md:136`
- 문제: PRD 9장은 "기각 사유를 모아 프롬프트와 규칙 개선"인데 요구사항 15는 상태만 모읍니다.
- 수정안: 범위 밖이라고 명시하거나 수기 기록으로 지정합니다.

**15. [모호] 값 목록이 설계 제안뿐**
- 위치: `D:253`, `D:336`, `D:894`
- 문제: 오류 종류, 게시 오류 종류, 202 사유(`event_ignored` 등)가 요구사항에 없습니다. OpenAPI와 P3가 의존합니다.
- 수정안: Glossary에 확정합니다.

**16. [모호] "5번의 인증 오류"**
- 위치: `R:159`
- 문제: 수용 기준 5번을 뜻하지만 "다섯 번"으로 읽힙니다.
- 수정안: "6.5의 인증 오류"로 고칩니다.

**17. [참조 오류] "요구사항 1.11"**
- 위치: `D:381`
- 문제: P1 요구사항 1.11을 뜻하는데 P2의 1.11은 delivery ID 기록입니다.
- 수정안: "P1 요구사항"으로 표기합니다.

**18. [참조 오류] Property 1**
- 위치: `D:1254`, `D:233`
- 문제: `WebhookProcessor`로 1.3(413)을 검증한다고 하지만 Processor는 이미 읽은 `byte[]`를 받습니다.
- 수정안: 413은 `BoundedBodyReader` 테스트로 분리합니다.

**19. [모순] Property 20과 요구사항 11.14**
- 위치: `D:1382`, `R:257`
- 문제: `failed` 혼합을 허용하는데 `github_auth` 실패나 head 변경만으로 이뤄진 순서열이면 코멘트 수가 0입니다.
- 수정안: "실제 게시가 한 번 이상"을 전제로 추가합니다.

**20. [모순] Property 10**
- 위치: `D:1312`, `D:796`
- 문제: 모든 종료 방식에 "받은 응답의 합"을 요구하지만 성공 시에는 `ReviewResult.usage`를 저장합니다.
- 수정안: 두 값이 같다는 근거(P1 20.1~20.2)를 명시하거나 속성을 나눕니다.

**21. [설계 결함] 대기열 카운터**
- 위치: `D:312-313`
- 문제: 중복 판정 전에 카운터를 올려 중복 요청이 용량을 잠시 차지하고, 정상 요청이 503을 받을 수 있습니다.
- 수정안: 판정 후에 올립니다.

**22. [모호] 서버 실행 조건**
- 위치: `D:789`, `D:1134`
- 문제: 포트, 실행 명령, 종료 시 진행 중 작업 처리, DB 풀 설정이 없습니다.
- 수정안: 서버 실행 절을 추가합니다.

**23. [모호] 경로별 설정 원본이 다름**
- 위치: `D:1134`, `D:1184`
- 문제: 서버는 실행 디렉터리의 `.prlens.yml` 하나를 모든 저장소에 쓰고, Actions는 PR 쪽 파일을 읽습니다. 제외 패턴과 허용 목록이 경로마다 달라집니다.
- 수정안: 설정 원본을 하나로 정하거나 차이를 문서화합니다.

**24. [모순] 2분 측정 기준**
- 위치: `ROADMAP.md:75`, `R:352`
- 문제: ROADMAP은 "2분 안에 요약 코멘트"인데 요구사항 17.1은 재시도 대기와 smee 지연을 뺍니다.
- 수정안: 차이를 ROADMAP에 적습니다.

**25. [모호] 시작 복구에서 `interrupted`의 실패 코멘트**
- 위치: `R:119`, `D:168`
- 문제: 4.5는 `failed`에 실패 게시를 요구하지만 복구 경로는 게시하지 않아 Summary가 이전 SHA 기준으로 남습니다.
- 수정안: 의도인지 명시합니다.

## 결정 대기와 spike 항목

### 결정 대기

| 항목 | 의존하는 설계 요소 |
|---|---|
| D-1 수집 방식 | `FeedbackCollector`, `feedback sync`, Line_Comment 본문 안내 |
| D-2 DB·마이그레이션 | `store.db`, 부분 유일 인덱스, `DbMigrator`, ArchUnit의 DB 라이브러리 규칙(`D:66`) |
| D-3 draft PR | `PullRequestPayloadParser`, Target_Action |
| D-4 비동기 방식 | `JobRunner`, `StartupRecovery`, `PrPublishLocks` |
| D-5 종료 코드 4 | `P2ExitCodes`, CLI 종료 코드 표 |
| D-6 CLI 허용 목록 | `--save` preflight |
| D-7 지문 구성 | `FindingFingerprint`, `PublishPlanner`, DB `fingerprint` (발견 12) |
| D-8 노출 범위 | `server.address`, smee 구성 |
| D-9 `superseded`의 Active 포함 | `ux_review_run_active`, `RunStatus.isActive` |
| D-10 채택률 분모 | P3 통계 (P2는 저장만) |
| D-11 허용 판정 기준 | `AllowedRepositories` |
| D-12 Publish_Error 개수 | `review_run.publish_error_*`, `recordPublishError` |

### spike

| 항목 | 의존하는 설계 요소 |
|---|---|
| spike 1 Flyway 자동 설정 끄기 | 서버 시작 순서 |
| spike 2 싱글턴 초기화 vs 포트 열기 | 재시작 복구 순서 |
| spike 3 Tomcat 본문 상한, content type | `BoundedBodyReader`, JSON 해석 |
| spike 4 HTTP 호출 중단 | `CancellableLlmClient`, 요구사항 4.7 (G-10) |
| spike 5 PEM 종류, JWT 라이브러리 | `AppJwtSigner`, 시작 검증 |
| spike 6 토큰 응답 필드 | `InstallationTokenProvider` |
| spike 7 Flyway 실패 버전, 동시 migrate | 요구사항 7.11, 7.12 오류 출력 |
| spike 8 OpenAPI 검증 도구 | `QueryApiContractIT` (요구사항 10.9) |
| spike 9 422 본문 형식 | `RejectionParser` (요구사항 12.8) |
| spike 10 게시 주체 login | `GitHubPrincipal` (발견 11) |
| spike 11 길이 단위 | `SummaryRenderer` |
| spike 12 Actions 권한 | 워크플로 예시, 요구사항 6.9 |
| spike 13 ArchUnit 버전 | `ArchitectureTest` |
| 체크리스트에 없는 spike: effort별 Claude 응답 시간(`D:1087`) | 요구사항 17의 120초 예산 |
| 체크리스트에 없는 spike: 설치 산출물 경로(`D:1185`) | 워크플로 예시 |
| 미확인 전제: P1 클래스 생성자 형태(`D:20`) | `JobScope`, `HttpGitHubClient` 401 처리 |
| 미확인 전제: Standard_Error_Response(`D:21`) | 모든 4xx/5xx 본문 (발견 1) |

spike 1, 5, 6, 12, 13은 [05번 문서](05-external-facts.md#스펙이-spike로-남겨-둔-항목)에서 문서로 답이 나온다고 확인했습니다.

## 커버리지 요약

- **요구사항**: 20개, 수용 기준 205개를 전부 읽고 설계와 대조했습니다. 설계 요소가 아예 없는 기준은 0개입니다.
- **부분 미충족 또는 충돌**: 10개. 1.10, 4.11(`failed`), 7.15, 15.6, 그리고 형식 전제가 틀린 1.12, 4.13, 10.7, 10.8, 10.14, 18.6.
- **속성**: FOR ALL 기준 20개가 모두 Property 27개에 대응합니다. 인용한 요구사항 번호는 모두 실재합니다. 문제 있는 속성은 3개(1, 10, 20)입니다.
- **PRD**: FR-7~FR-11과 FR-12 일부, 비기능 요구사항(보안·비용·신뢰성·응답 시간)에 대응하는 요구사항이 있습니다. 공백은 2건(기각 사유, 대체 경로의 저장)입니다.
- **공백 요약**: G-1~G-13 중 요구사항에 반영된 것은 0건입니다. 표에 없는 공백은 발견 3, 4, 5, 9, 15입니다.

## 확인하지 못한 것

- `P2-tasks`와 P1 스펙 전반은 범위 밖이라 읽지 않았습니다. P1은 요구사항 21과 일부 검색만 확인했습니다. P1에서 가져온 열거값(`review_mode`, `summary_only_reason`), Result_Codec 필드 이름과의 일치(10.10)는 검증하지 못했습니다.
- GitHub API 동작(422 본문, `GET /user` 403, 리뷰 body 필수 여부, 권한 범위)과 Spring Boot 4·Flyway·JDK `HttpClient` 동작은 문서만으로 판단했고 실행하지 않았습니다.
- 스타터는 ADR 0002, `ErrorResponse.java`, `build.gradle.kts`, `api-design.md`만 확인했습니다. PLAYBOOK은 읽지 않았습니다.
