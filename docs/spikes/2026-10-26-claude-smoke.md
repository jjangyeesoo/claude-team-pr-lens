# 실제 Claude API 스모크 (P1 작업 13.4)

2026-10-26, Windows, Gradle 9.7.1(Gradle 출력에서 확인). 실행한 JDK 버전은 확인하지 않았다(빌드 설정의 대상은 Java 17). `AnthropicSmokeTest`(`com.prlens.review`)를 사용자가 직접 한 번 실행했다. 원본은 `backend/build/test-results/test/TEST-com.prlens.review.AnthropicSmokeTest.xml`의 표준 출력이다. 이 파일은 커밋되지 않고 다음 `test` 실행 때 덮어써지므로 아래에 그대로 인용한다. 키는 사용자의 별도 PowerShell 프로세스에서만 썼고 기록에 남기지 않았다.

## 입력
- 모델과 effort: `Configuration.defaults()` (`claude-opus-5-5`, `medium`, 최대 출력 토큰 16,000)
- 파일 하나(`UserController.java`, 추가 8줄, 안에 `System.out.println`), 컨텍스트 파일 둘(`.claude/rules/backend/logging.md`에 "운영 코드에서 System.out.println을 쓰지 않는다", `CLAUDE.md`)
- `PromptBuilder.build(SINGLE, …)`로 만든 요청을 `RetryingLlmClient(AnthropicLlmClient)`로 보내고 `ResponseParser`, `FindingValidator`를 통과시켰다

## 원본 출력 (XML의 `system-out` 그대로)
```
[smoke] 응답 시간 5908 ms
[smoke] stop_reason=end_turn
[smoke] usage=LlmUsage[inputTokens=196, outputTokens=297, cacheWriteTokens=1731, cacheReadTokens=0]
[smoke] 추정 비용(USD)=0.0154
[smoke] summary=UserController를 추가해 사용자 조회 메서드(find)를 도입하는 PR입니다. 운영 코드에서 System.out.println을 사용해 팀 로깅 규칙을 위반합니다.
[smoke] finding MINOR src/main/java/com/example/UserController.java:5 basis=RULE/.claude/rules/backend/logging.md verdict=INLINE_ELIGIBLE
```
같은 XML의 `testsuite`: `tests="1" skipped="0" failures="0" errors="0"`, `time="6.321"`(테스트 케이스는 6.312초).

## 확인한 것
| 항목 | 결과 |
|---|---|
| 테스트 결과 | 1건 통과, skip 0, 실패 0 (XML의 `time` 6.312초) |
| 응답 시간 | 5,908 ms (요청 1건) |
| `stop_reason` | `end_turn` |
| 파서 | `incompleteReason` 없음. 응답이 JSON 스키마를 따랐고 `ResponseParser`를 통과함 |
| usage | 입력 196, 출력 297, 캐시 쓰기 1,731, 캐시 읽기 0 |
| 추정 비용 | 0.0154 USD (기본 단가로 검산: 196×4 + 297×20 + 1,731×5 = 15,379 → 1,000,000으로 나눠 반올림) |
| 지적 | 1건. `MINOR`, `UserController.java` 5줄, `basis` = `RULE` / `.claude/rules/backend/logging.md`, `INLINE_ELIGIBLE` |
| `basis.ref` | 컨텍스트 파일 경로와 같은 문자열로 와서 강등되지 않았다 |
| 캐시 쓰기 | 1,731 토큰이 기록됨. 캐시 지점이 실제로 작동했다 |

## 확인하지 못한 것
- 응답 content 블록의 구성(thinking 블록이 앞에 오는지): 블록은 출력하지 않았다. 첫 text 블록을 고르는 구현이 파서를 통과했다는 것까지만 안다
- 캐시 읽기: 요청을 한 번만 보내서 0이다. 같은 앞부분으로 두 번 보내 읽기가 나오는지는 보지 않았다(분할 모드는 작업 20.4에서 본다)
- 실제 API의 오류 응답(429, 5xx, 4xx)과 `retry-after` 값의 모양, refusal 응답의 모양
- 400줄 단일 모드 120초 측정(요구사항 9.7)
- 같은 입력의 반복 실행 편차
