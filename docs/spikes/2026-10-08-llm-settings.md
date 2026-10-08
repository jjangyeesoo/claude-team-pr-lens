# Spike: Claude API 실제 호출과 LLM 설정 실측 (2026-10-08)

ADR-0005(모델, effort, 최대 출력 토큰, refusal fallback, 캐시 지점)를 정하기 전에 `claude-opus-5-5`를 실제로 호출해 비용, 응답 시간, 출력 토큰을 쟀습니다. 저장소 밖 임시 Gradle 프로젝트에서 실행했고, 저장소의 `build.gradle.kts`는 고치지 않았습니다.

- 환경: Windows 11, JDK Temurin 17, Gradle 9.7.1, anthropic-java 2.68.0
- 요청 형태: design.md의 배치 그대로입니다. `system`(지시문), user 블록 1(`<prlens:team_context>`, 끝에 ephemeral `cache_control`), user 블록 2(PR 제목·본문·파일별 diff), `output_config.format`(design.md의 JSON 스키마), `output_config.effort` 명시, `max_tokens` 16,000, SDK 재시도 0회, 제한 시간 180초, 비스트리밍
- 팀 컨텍스트: base 커밋의 `CLAUDE.md`, `backend/CLAUDE.md`, `.claude/rules/` 3개 (약 6,700 토큰)
- 호출 15회, 합계 약 $1.56. 한 사람이 한 번씩 실행한 값이라 편차는 보지 못했습니다. `DelimiterCodec` 이스케이프와 재시도 래퍼는 넣지 않았습니다.

## 입력

| 이름 | 내용 | 변경 줄 |
|---|---|---|
| pr1 | 이 저장소 PR #1 (패키지 변경, 메모 샘플 삭제). 삭제 위주 | 추가 46, 삭제 277, 파일 36 |
| add | 스타터 초기 커밋의 `backend/src` (메모 API 추가). 추가 위주 | 추가 311, 파일 14 |
| sec | 직접 만든 보안 수정 diff. 명령 주입·SQL 주입 수정과 공격 문자열이 든 테스트 | 추가 약 45, 파일 2 |

## 측정값

입력 토큰은 `input`(캐시 밖) + 캐시 쓰기 또는 읽기입니다. 비용은 요구사항 18.4의 단가(입력 $4, 출력 $20, 캐시 쓰기 $5, 캐시 읽기 $0.20)로 계산했습니다.

| 입력 | effort | 입력 | 캐시 쓰기 | 캐시 읽기 | 출력 | 응답 시간 | 비용 | 지적 수 |
|---|---|---|---|---|---|---|---|---|
| pr1 | low | 17,969 | 6,712 | 0 | 662 | 15.1초 | $0.1187 | 1 |
| pr1 | medium | 17,969 | 6,712 | 0 | 2,116 | 22.4초 | $0.1478 | 2 |
| pr1 | medium | 17,969 | 6,712 | 0 | 1,866 | 29.4초 | $0.1428 | 2 |
| pr1 | high | 17,969 | 6,710 | 0 | 2,637 | 32.8초 | $0.1582 | 2 |
| pr1 | medium, 캐시 지점 없음 | 24,679 | 0 | 0 | 2,214 | 26.7초 | $0.1430 | 2 |
| pr1 | medium (스키마 순서 고정 뒤 1회차) | 17,969 | 6,712 | 0 | 1,798 | 19.7초 | $0.1414 | 2 |
| pr1 | medium (바로 이어 2회차) | 17,969 | 0 | 6,712 | 2,069 | 20.9초 | $0.1146 | 2 |
| add | low | 9,026 | 0 | 6,712 | 1,038 | 10.1초 | $0.0582 | 3 |
| add | medium | 9,026 | 0 | 6,712 | 1,866 | 18.1초 | $0.0748 | 3 |
| add | medium | 9,026 | 0 | 6,712 | 2,033 | 19.6초 | $0.0781 | 4 |
| add | high | 9,026 | 0 | 6,712 | 2,908 | 27.2초 | $0.0956 | 4 |
| add | high | 9,026 | 0 | 6,712 | 3,374 | 30.4초 | $0.1049 | 3 |
| sec | medium | 1,582 | 0 | 6,712 | 2,577 | 23.6초 | $0.0592 | 6 |
| sec | medium, `fallbacks: "default"` | 1,582 | 0 | 6,712 | 2,803 | 25.9초 | $0.0637 | 5 |

모든 호출의 `stop_reason`은 `end_turn`이었습니다.

## 결과

| 질문 | 결과 |
|---|---|
| 1회 리뷰 비용이 상한 $0.50 안인가 | 안입니다. 300줄대 PR에서 $0.06~$0.16입니다. 캐시를 쓰는 큰 diff(pr1)에서는 입력이 대부분입니다(pr1 medium에서 입력 약 $0.105, 출력 약 $0.04). 캐시를 읽거나 diff가 작으면 출력이 절반 이상입니다(sec medium에서 입력 약 $0.008, 출력 약 $0.05). effort를 `low`에서 `high`로 올려도 차이는 $0.04 정도입니다 |
| effort별 응답 시간 | `low` 10~15초, `medium` 18~29초, `high` 27~33초입니다. P1의 120초(요구사항 9.7), P2의 Claude 호출 예산 90초보다 훨씬 짧습니다 |
| 출력 속도 | 출력 토큰을 응답 시간으로 나누면 초당 44~111 토큰입니다(pr1 44~99, add·sec 103~111). 입력 처리 시간이 포함된 값입니다. 이 속도로 180초에 받는 양은 약 8,000~20,000 토큰입니다 |
| thinking 토큰은 얼마나 되는가 | `usage.outputTokensDetails.thinkingTokens`로 읽습니다. add 입력에서 `low` 0, `medium` 507(출력 2,033 중), `high` 1,979(출력 3,374 중)였습니다. `high`에서 늘어난 출력은 거의 thinking입니다 |
| 최대 출력 토큰 16,000이 충분한가 | 충분합니다. 가장 큰 출력이 3,374 토큰이었고 `max_tokens`에 닿은 적이 없습니다. 지적이 수십 건인 PR은 재지 못했습니다 |
| effort에 따라 지적이 달라지는가 | 이 표본에서는 `low`가 지적 수가 적고(pr1 1건 대 2건) `medium`과 `high`는 비슷했습니다. 표본이 작아 품질 차이는 판단하지 않습니다 |
| 캐시가 읽히는가 | 읽힙니다. 다만 **JSON 스키마의 키 순서가 요청마다 같아야** 합니다. `Map.of`로 스키마를 만들었을 때는 JVM 실행마다 순회 순서가 달라 같은 요청을 이어 보내도 매번 캐시를 새로 썼습니다(위 표의 처음 네 행). 삽입 순서를 지키는 맵으로 바꾸자 바로 읽혔습니다 |
| effort를 바꾸면 캐시가 무효화되는가 | 이 실험에서는 아닙니다. `medium`으로 쓴 캐시를 `low`와 `high` 요청이 읽었습니다 |
| PR이 달라도 캐시를 읽는가 | 읽습니다. pr1로 쓴 팀 컨텍스트 캐시를 add와 sec 요청이 읽었습니다. 같은 저장소의 PR을 5분 안에 이어 리뷰하면 단일 모드도 캐시 이득이 있습니다 |
| 단일 모드에서 캐시 쓰기의 추가 비용 | 6,712 토큰 기준 약 $0.007입니다(캐시 지점 없음 $0.1430, 있음 $0.1414~$0.1478로 편차 안). 읽으면 약 $0.026을 아낍니다 |
| 보안 관련 diff에서 refusal이 나오는가 | 이 한 건에서는 나오지 않았습니다. 공격 문자열(셸 주입, SQL 주입)이 든 테스트를 정상적으로 리뷰했고, 남은 `sort` 주입 경로를 blocker로 지적했습니다. refusal 자체는 재현하지 못했습니다 |
| `fallbacks: "default"`를 Java SDK로 보낼 수 있는가 | 요청은 받아들여집니다. 전용 빌더 메서드를 찾지 않고 `putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")`와 `putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))`로 보냈습니다. refusal이 없었으므로 대체 모델로 넘어가는 동작은 확인하지 못했습니다 |
| 출력이 `max_tokens`에 닿으면 어떻게 되는가 | `max_tokens`를 1,200으로 낮춰 재현했습니다(sec, medium, 표에는 없는 15번째 호출, $0.0639. 캐시가 만료돼 6,712 토큰을 다시 썼습니다). HTTP 200에 `stop_reason`이 `max_tokens`, `usage.outputTokens`가 한도와 같은 1,200이었고 본문 JSON은 첫 지적의 `message` 중간에서 끊겼습니다. 1,200 중 797이 thinking 토큰이었습니다. thinking도 한도에 포함됩니다 |
| 규칙 근거(`basis`)가 붙는가 | 붙습니다. `basis.ref`가 team_context 머리줄의 `path`와 같은 글자로 나왔습니다(`backend/CLAUDE.md`, `.claude/rules/backend/testing.md` 등). M1 완료 조건인 "규칙 근거 지적 1건 이상"은 세 입력 모두에서 나왔습니다 |

## SDK에서 확인한 것 (작업 13.1에 반영)

- 구조화된 출력과 effort: `OutputConfig.builder().effort(OutputConfig.Effort.of("medium")).format(JsonOutputFormat.builder().schema(...).build())`. 스키마는 `JsonOutputFormat.Schema.builder().putAdditionalProperty(키, JsonValue.from(값))`로 넣습니다. `minimum`, `minLength` 없이 design.md의 스키마가 그대로 받아들여졌습니다.
- 캐시 지점: `TextBlockParam.builder().text(...).cacheControl(CacheControlEphemeral.builder().build())`, 메시지는 `addUserMessageOfBlockParams(List.of(ContentBlockParam.ofText(...), ...))`.
- usage: `inputTokens()`, `outputTokens()`는 `long`, `cacheCreationInputTokens()`, `cacheReadInputTokens()`는 `Optional<Long>`입니다.
- 응답 content 블록: `low`에서는 `[text]` 하나, `medium`과 `high`에서는 `[thinking(빈 문자열), text]`입니다. 첫 블록이 아니라 **첫 text 블록**을 골라야 합니다.
- 클라이언트: `AnthropicOkHttpClient.builder().fromEnv().maxRetries(0).timeout(Duration.ofSeconds(180))`.
- 오류: `AnthropicServiceException`의 `statusCode()`, `errorType()`, `headers().values("retry-after")`가 컴파일됩니다. 오류 응답을 실제로 받아 보지는 않았습니다.
- 스키마를 만드는 맵은 순서가 고정돼야 합니다(위 캐시 항목). 구현에서는 스키마를 상수 문자열이나 순서가 정해진 구조로 두고, 직렬화 결과가 실행마다 같은지 테스트로 확인하는 편이 안전합니다.

## 남은 확인

- refusal 응답의 실제 모양(`stop_details`)과 fallback으로 넘어갔을 때의 응답(모델 이름, 과금).
- spend limit에 걸린 429를 일반 429와 구분하는 방법. 문서상 결제 문제는 402 `billing_error`로 따로 있지만 spend limit 429의 본문은 확인하지 못했습니다.
- 지적이 많은 PR(분할 모드, 요약 전용 모드)의 출력 토큰. 작업 20.4의 리허설에서 봅니다.
- 같은 입력의 반복 실행 편차와 effort별 리뷰 품질. 도그푸딩의 채택/기각 기록으로 봅니다.
