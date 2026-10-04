# 0003. PR Lens는 역할 단위 평면 패키지를 쓴다 (0001 대체)
- 상태: accepted · 날짜: 2026-10-04 · 대체: [0001](0001-feature-based-packages.md)

## 배경
PRD 10장과 ROADMAP 1주차가 "ADR-0003 저장소 구조(모노레포 `backend/` + `web/` 사용 여부)"로 예약했던 번호를 이 결정이 먼저 썼다. 저장소 구조 결정은 0007로 쓴다. 스펙이 가리키는 0004(CLI 라이브러리), 0005(LLM 설정), 0006(DB·테스트)은 그대로다.

ADR 0001의 `<feature>.{domain,api}` 구조는 메모 샘플처럼 REST 기능 하나가 Controller와 서비스로 이루어진 경우를 전제로 한다. PR Lens는 모양이 다르다.

- P1은 HTTP API가 없는 CLI 파이프라인이다(PR 가져오기 → 컨텍스트 수집 → 리뷰 → 출력). `diff.api`, `glob.api`에는 넣을 것이 없다.
- P1·P2 스펙은 `com.prlens` 아래 역할별 패키지를 전제로 트랙 배정, 의존 규칙, "P2는 `review`·`llm`을 고치지 않는다"는 규칙을 적었다. 패키지 이름을 바꾸면 이 셋을 모두 다시 써야 한다.
- ADR 0001이 피하려던 것은 계층 단위 패키지(`controller/`, `service/`, `repository/`)다. 스펙의 패키지는 계층이 아니라 역할 단위라서 그 취지와 어긋나지 않는다.

## 결정
기본 패키지는 `com.prlens`이고, 그 바로 아래에 역할 단위 패키지를 평면으로 둔다. `domain`/`api` 하위 구분은 두지 않는다.

- 패키지 목록과 담당 트랙의 원본은 각 스펙 설계 문서의 "패키지 배치" 표다.
  - P1: `model`, `support`, `github`, `pullrequest`, `diff`, `glob`, `filter`, `context`, `llm`, `review`, `codec`, `output`, `config`, `cli`
  - P2 추가: `webhook`, `store`(`store.db`, `store.memory`), `query`, `publish`, `server`, `execution`
- 의존 방향은 문서가 아니라 ArchUnit 테스트로 강제한다. 규칙의 원본은 각 설계 문서의 "의존 규칙"이다.
- 공유 패키지는 두 개만 둔다.
  - `model`: 트랙 사이를 오가는 불변 데이터 타입. JDK 외에는 의존하지 않는다.
  - `support`: 재시도, 비밀정보 마스킹, 경고, 예외 기반 타입처럼 둘 이상의 패키지가 쓰는 기반 코드.
  - 한 패키지만 쓰는 코드는 그 패키지에 둔다. "공통 코드는 최소한으로"라는 ADR 0001의 원칙은 유지한다.
- 새 패키지를 추가하려면 설계 문서의 패키지 표와 의존 규칙을 같은 PR에서 고친다.

## 고려한 대안
- **ADR 0001 유지 (`<feature>.{domain,api}`)**: 저장소의 rules, 스킬, reviewer를 그대로 쓴다. 하지만 P1·P2 설계의 패키지 표, 의존 다이어그램, 의존 규칙, 작업 목록의 패키지 참조를 모두 다시 써야 하고, CLI 파이프라인에는 `api` 하위 패키지가 대부분 비게 된다.
- **계층 단위 패키지**: ADR 0001에서 이미 기각했다.

## 결과
- 최상위 패키지가 약 20개가 된다. 새 코드의 위치는 패키지 표를 보고 정해야 하고, 규칙 위반은 ArchUnit이 잡는다.
- ArchUnit은 새 의존성이다. 추가할 때 사람의 승인이 필요하다(P1 작업 1).
- 후속 작업
  - `backend/CLAUDE.md`의 Architecture 절, `.claude/agents/reviewer.md`의 레이어 규칙, `.claude/skills/new-endpoint`를 이 결정에 맞게 고친다.
  - 패키지 이름을 `com.example.starter`에서 `com.prlens`로 바꾼다(ROADMAP 1주차).
  - 메모 샘플(`memo`)은 P1 작업 1에서 지운다(ROADMAP: 2주차 첫 기능 PR).
- 아직 정하지 않은 것
  - `common/error`(`ErrorResponse`, `ApiExceptionHandler`)와 `common/config`를 어느 패키지로 옮길지. P2 설계가 제안을 적었다: `ErrorResponse`는 `support`로, `ApiExceptionHandler`와 `TimeConfig`는 `server`로, `@SpringBootApplication` 클래스는 루트 패키지 `com.prlens`에 그대로(P2 작업 1.1). P2 스펙 승인 때 확정한다. 그때까지 ArchUnit의 Spring 애너테이션 규칙에서 `common`을 예외로 둔다.
  - P2가 `execution` 패키지를 추가한다(서버와 CLI가 함께 쓰는 취소, 제한 시간, usage 집계 도구). P2 설계 "패키지 배치"가 원본이다.
