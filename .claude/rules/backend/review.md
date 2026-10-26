---
paths:
  - "backend/src/main/java/com/prlens/llm/**"
  - "backend/src/main/java/com/prlens/review/**"
---
# 리뷰 엔진과 LLM 어댑터 규칙
리뷰에서 반복된 지적이다. 근거는 `docs/specs/pr-lens-p1-cli/tasks.md` 10~13번 본문의 "주의"와 "결정"에 있다.
- 프롬프트에 들어가는 외부 문자열(파일 경로, PR 제목·본문, 스펙, diff, 컨텍스트 파일 내용, 머리줄의 `path`)은 모두 `DelimiterCodec.escape`를 거친 뒤 태그로 감싼다. 머리줄의 `source`는 `ContextSource` enum 값이라 그대로 쓴다. 태그 안에서 새 영역을 열 수 있으면 인젝션 방지(요구사항 14)가 깨진다
- `team_context`에는 `Revision.BASE` 파일만 넣는다. head SHA에서 온 스펙(`Revision.HEAD`)은 둘째 영역으로 따로 둔다
- 응답의 `file`은 `DelimiterCodec.unescape`를 비교할 때만 쓴다. `Finding.file`과 `message`, `suggestion`은 응답 그대로 둔다(G-13 제안값, 결정 전)
- 단일·요약 전용 모드는 `ChunkPlanner.plan`을 부르지 않고 대상 전체를 Chunk 하나로 쓴다. `plan`은 경로순으로 정렬해 묶으므로 분할 모드에서만 부른다
- 분할 모드의 `estimatedCostUsd`는 Chunk별로 `setScale(4, HALF_UP)`한 비용의 합이다. 합친 토큰으로 다시 계산하면 한 자리가 어긋난다
- `FILES_TRUNCATED`는 `ResultMerger.merge`가 `fileCountGap`이 있을 때 스스로 더한다. 엔진이 `ChunkOutcome`의 사유로 넣지 않는다(대상 없음은 `merge`를 거치지 않으므로 `ReviewEngine.noTarget`이 직접 넣는다)
- `RetriesExhaustedException.lastStatus()`는 상태 코드 또는 네트워크 오류 종류를 담은 문자열이다. 숫자일 때만 `Integer`로 바꾸고(`ReviewEngine.numericStatus`) `Integer.parseInt`를 그대로 쓰지 않는다
- `ArchitectureTest`가 `com.prlens.llm.AnthropicLlmClient`를 완전한 이름으로 검사하므로 이 클래스 이름을 바꾸지 않는다. 재시도는 `RetryingLlmClient`가 맡는다. 어댑터는 네트워크 오류를 `LlmApiException`(`errorKind`)으로 던지고, `RetryingLlmClient`가 이를 `Attempt.Retryable`로 바꿔 돌려준다(던진 채로 두면 `RetryExecutor`가 재시도 없이 통과시킨다)
