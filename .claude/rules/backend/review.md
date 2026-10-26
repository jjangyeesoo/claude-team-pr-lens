---
paths:
  - "backend/src/main/java/com/prlens/llm/**"
  - "backend/src/main/java/com/prlens/review/**"
---
# 리뷰 엔진과 LLM 어댑터 규칙
리뷰에서 반복된 지적이다. 근거는 `docs/specs/pr-lens-p1-cli/tasks.md` 10~13번 본문의 "주의"와 "결정"에 있다.
- 프롬프트에 들어가는 외부 문자열(파일 경로, PR 제목·본문, 스펙, diff, 컨텍스트 파일 내용, 머리줄의 `path`·`source`)은 모두 `DelimiterCodec.escape`를 거친 뒤 태그로 감싼다. 태그 안에서 새 영역을 열 수 있으면 인젝션 방지(요구사항 14)가 깨진다
- `team_context`에는 `Revision.BASE` 파일만 넣는다. head SHA에서 온 스펙(`Revision.HEAD`)은 둘째 영역으로 따로 둔다
- 응답의 `file`은 `DelimiterCodec.unescape`를 비교할 때만 쓴다. `Finding.file`과 `message`, `suggestion`은 응답 그대로 둔다(G-13)
- 단일 모드는 `ChunkPlanner.plan`을 부르지 않고 대상 전체를 Chunk 하나로 쓴다. `plan`은 경로순으로 정렬해 묶으므로 분할 모드에서만 부른다
- 분할 모드의 `estimatedCostUsd`는 Chunk별로 `setScale(4, HALF_UP)`한 비용의 합이다. 합친 토큰으로 다시 계산하면 한 자리가 어긋난다
- `FILES_TRUNCATED`는 `ResultMerger.merge`가 `fileCountGap`이 있을 때 스스로 더한다. 엔진이 `ChunkOutcome`의 사유로 넣지 않는다
- `RetriesExhaustedException.lastStatus()`는 상태 코드 또는 네트워크 오류 종류를 담은 문자열이다. 숫자일 때만 `Integer`로 바꾸고(`ReviewEngine.numericStatus`) `Integer.parseInt`를 그대로 쓰지 않는다
- `review`는 `AnthropicLlmClient`와 SDK 타입을 참조하지 않고 `LlmClient`만 쓴다(`ArchitectureTest`가 클래스 이름으로 검사하므로 클래스 이름을 바꾸지 않는다). 재시도는 `RetryingLlmClient`가 맡고, 재시도할 네트워크 오류는 예외로 던지지 않고 `Attempt.Retryable`로 돌려준다
