---
paths:
  - "backend/src/main/java/com/prlens/github/**"
  - "backend/src/main/java/com/prlens/pullrequest/**"
  - "backend/src/main/java/com/prlens/diff/**"
  - "backend/src/main/java/com/prlens/glob/**"
  - "backend/src/main/java/com/prlens/filter/**"
  - "backend/src/main/java/com/prlens/context/**"
---
# GitHub 연동과 컨텍스트 수집 규칙
리뷰에서 반복된 지적이다. 근거는 `docs/specs/pr-lens-p1-cli/tasks.md` 7~9번과 18.3 본문의 "주의"와 "결정"에 있다.
- 팀 컨텍스트(`CLAUDE.md`, rules, `@import`)는 base SHA에서만 읽는다. head SHA에서 읽는 것은 PR 본문이 링크한 스펙뿐이고 base에서 404일 때만이다(요구사항 4.1, 5.1, D-7 제안값). 새 수집 코드의 `ContextFile`은 `Revision.BASE`로 만든다
- base 파일은 `BaseFileReader.fetch`로 읽는다. `Missing`(트리에 없음, 404), `Directory`, `TooLarge`는 서로 다른 처리이므로 `read`로 뭉개지 않는다
- 트리 색인은 `RepoTreeIndex.load`를 한 번만 부르고 같은 색인을 수집기들에 넘긴다
- `HttpGitHubClient`의 404는 메서드마다 다르다: `getPullRequest`·`listFiles`·`getTree`는 `GitHubApiException`을 던지고, `getFile`은 `FileFetch.NotFound` 값을 돌려준다
- 연결 실패와 제한 시간 초과는 예외로 던지지 않고 `Attempt.Retryable`로 돌려준다. 던지면 `RetryExecutor`가 재시도 없이 통과시킨다
- 리다이렉트는 따르지 않는다(`Authorization` 보호, 요구사항 19.6). 3xx는 `Attempt.Fatal`이다
- 디버그 로그의 헤더는 `SecretMasker.maskHeader`로 가린다. 값을 직접 로그에 쓰지 않는다
- glob 패턴은 `GlobMatcher.validate`로 먼저 걸러 무효 패턴마다 경고 1건을 낸다. 파일마다 `matches`를 try/catch하지 않는다. 패턴에는 `\`를 거부한 뒤에만 `RepoPaths.normalize`를 쓴다(그 함수가 `\`를 `/`로 바꿔 무효 패턴을 통과시킨다)
- `ContextAccumulator.add`는 스스로 `RepoPaths.normalize`한 뒤 문자열 그대로 비교한다(대소문자와 `..`는 맞추지 않는다). 그 밖의 경로 정규화는 `backend/CLAUDE.md` Gotchas를 따른다
