# P2 자동화 스펙 색인

PR이 열리면 webhook으로 리뷰를 자동 실행하고, 결과를 저장하고, PR에 게시합니다(PRD FR-7~11, ROADMAP 3주차 M2).

- 상태 (2026-10-04): 검토만 했고 **아직 고치지 않았습니다.** 그대로는 구현에 들어갈 수 없습니다
- 문서: [requirements.md](requirements.md), [design.md](design.md)(약 15만 바이트. 필요한 절만 읽기), [tasks.md](tasks.md)
- 읽는 법은 [../README.md](../README.md)

## 수정 전에 확인할 것

**검토 결과**: `docs/spec-review/`의 [03](../../spec-review/03-p2-requirements-design.md)(요구사항·설계), [04](../../spec-review/04-p2-tasks-and-p1-link.md)(작업 목록, P1 연계), [05](../../spec-review/05-external-facts.md)(외부 사실), [01](../../spec-review/01-claude-usability.md)(저장소와의 충돌). 줄 번호는 검토 시점 기준이라 절 제목과 요구사항 번호로 찾습니다. 외부 사실은 고치기 전에 공식 문서 원문을 확인합니다.

**확정된 결정**
- 패키지 구조: [ADR 0003](../../adr/0003-role-based-flat-packages.md). `common`(`ErrorResponse`, `ApiExceptionHandler`, `TimeConfig`)을 어느 패키지로 옮길지는 이 스펙에서 정합니다
- 에러 형식 `ErrorResponse`, 경로 `/api/v1/`: [ADR 0008](../../adr/0008-keep-api-conventions.md). webhook 경로와 페이지네이션 규칙은 이 스펙을 고칠 때 정합니다

**P1 스펙이 바뀌어서 함께 고쳐야 하는 것** (P1 수정 2026-10-04)
- `IncompleteDetails.rawResponseExcerpt`가 `ChunkIssue.rawResponseExcerpt`로 옮겨졌고 `ReviewResult`에 `excludedFileDetails`가 추가됐습니다(저장 스키마, DTO)
- `RetryListener.onRetry`의 대기 시간이 `Duration`입니다(이 스펙은 ms 합으로 집계)
- `PrLensException`은 sealed가 아니고 재시도 예외는 `support`에 있습니다. P2 예외가 상속할 수 있습니다
- `PrFetcher.fetch(RepoRef, int)`, 생성자 주입(`HttpGitHubClient`, `RetryExecutor`, `RetryingLlmClient`), `CostCalculator.estimate`는 P1에 정의됐습니다(04번의 B-2, B-4, B-12가 해소됨)
- P1의 ArchUnit 규칙 "Spring 애너테이션은 `cli`에만"을 이 스펙의 규칙으로 교체해야 합니다(04번 B-1)
- 04번 (B) 중 P2 쪽에 남은 지적: B-1, B-3, B-5, B-6, B-7, B-8, B-11

**환경**: Docker가 설치돼 있지 않습니다(PostgreSQL, Testcontainers에 필요). Docker 테스트를 Stop hook과 CI에서 어떻게 분리할지는 ADR-0006에서 정합니다.

**팀이 정할 것**: 결정 대기 D-1~D-12와 spike 13건(03번 "결정 대기와 spike 항목"). 임의로 확정하지 않습니다.
