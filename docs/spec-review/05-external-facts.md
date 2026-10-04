# 05. 외부 기술 사실 검증

[← 목차](README.md)

스펙이 GitHub API, Claude API, 라이브러리에 대해 적은 주장을 공식 문서와 대조했습니다(기준일 2026-10-04).

**주의**: 모든 근거는 웹 문서의 요약을 보고 확인한 것입니다. 스펙을 고치기 전에 해당 URL의 원문을 한 번 더 확인하세요.

## 총평

GitHub 쪽 주장(엔드포인트 경로, webhook 10초·25MB·서명, JWT `iat`/`exp`, 파일 목록 3,000개 상한)과 Anthropic 쪽 큰 틀(`claude-opus-5-5`, $4/$20, effort 기본 `medium`, thinking 상시 켜짐, `output_config.format`)은 공식 문서와 일치합니다. 구현을 깨뜨릴 오류는 네 가지입니다.

- 설계의 JSON 스키마에 구조화 출력이 지원하지 않는 `minimum`·`minLength`가 그대로 들어 있습니다.
- GitHub rate limit 대기 규칙이 공식 지침과 다릅니다.
- Opus 5.5 캐시 읽기 단가가 실제의 2배로 적혀 있습니다.
- Spring Boot 4.1이 JUnit 6을 관리하는데, jqwik 최신판은 JUnit Platform 1.x 기반입니다.

spike 항목의 절반 이상은 문서로 이미 답이 나옵니다. 실험이 꼭 필요한 것은 422 본문 형식, 게시 주체 login, jqwik과 JUnit 6 조합, HTTP 호출 취소 정도입니다.

## 틀리거나 부분적으로 맞는 주장 (피해가 큰 순)

**1. 구조화 출력 스키마의 `minimum`, `minLength`** — 부분적으로 맞음 (스키마 자체는 틀림)
- 주장: SDK 헬퍼가 `minimum`·`minLength`를 지우고 설명으로 옮기므로, 스키마에 `"minimum": 1`, `"minLength": 1`을 두고 로컬에서 재검증한다.
- 위치: `P1-des:286`, `P1-des:306`, `P1-des:309`
- 근거: `minimum`·`minLength`는 미지원이고 "unsupported feature → 400 error"입니다. 변환은 "대부분의 SDK 헬퍼" 얘기이며, Java SDK는 변환하지 않고 로컬 검증에서 거부합니다. 설계처럼 원시 스키마를 보내면 400이 예상되므로 두 키워드를 빼야 합니다(로컬 재검증 방침은 타당).
- 출처: https://platform.claude.com/docs/en/build-with-claude/structured-outputs

**2. GitHub rate limit 재시도 규칙** — 부분적으로 맞음
- 주장: GitHub 403(`retry-after` 있음 또는 remaining 0)을 429처럼 재시도하고, `retry-after`가 없으면 1·2·4초 백오프, 60초 초과면 포기.
- 위치: `P1-req:410-416`, `P1-des:824`, `P1-tasks:53`
- 근거: 응답 판별(403/429, `x-ratelimit-remaining: 0`)은 맞습니다. 대기 규칙은 다릅니다. remaining 0이면 `x-ratelimit-reset`까지, 그 외에는 최소 1분 대기입니다. 1·2·4초 재시도는 전부 실패합니다. 또 `retry-after`도 없고 remaining도 0이 아닌 secondary limit 403은 스펙에서 "권한 부족"으로 오진됩니다.
- 출처: https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api

**3. Opus 5.5 캐시 읽기 단가** — 틀림
- 주장: 캐시 읽기 단가 $0.40(입력의 0.1배).
- 위치: `P1-des:514`, `P1-des:517`
- 근거: Opus 5.5의 캐시 읽기는 0.05배, $0.20/MTok입니다. 5분 캐시 쓰기 $5(1.25배)는 맞습니다. 예시 JSON의 `0.1496`(`P1-des:488`)도 이 단가로 계산된 값입니다.
- 출처: https://platform.claude.com/docs/en/about-claude/pricing

**4. `stop_reason` 처리** — 부분적으로 맞음
- 주장: `stop_reason`은 `end_turn 등 정상` / `refusal` / `max_tokens` 세 갈래로 처리.
- 위치: `P1-des:331-335`, `PRD.md:109`
- 근거: 값은 7개입니다(`end_turn`, `max_tokens`, `stop_sequence`, `tool_use`, `pause_turn`, `refusal`, `model_context_window_exceeded`). `model_context_window_exceeded`는 "Treat the response as truncated"인데 스펙에서는 정상 경로로 들어가 `schema_violation`이 됩니다.
- 출처: https://platform.claude.com/docs/en/build-with-claude/handling-stop-reasons

**5. webhook 재전송 픽스처** — 틀림 (영향 작음)
- 주장: 재전송 픽스처는 "같은 Dedupe_Key, 다른 delivery ID".
- 위치: `P2-des:1527`
- 근거: "If you request a redelivery, the `X-GitHub-Delivery` header will be the same as in the original delivery." 중복 판정은 Dedupe_Key 기준이라 동작에는 영향이 없고 픽스처만 실제와 다릅니다.
- 출처: https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks

**6. JWT `iss`** — 부분적으로 맞음
- 주장: JWT `iss = App ID`.
- 위치: `P2-des:363`, `P2-req:154`
- 근거: App ID도 허용되지만 "Use of the client ID is recommended"입니다.
- 출처: https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app

**7. jqwik과 JUnit 버전** — 부분적으로 맞음 (호환성 미확정)
- 주장: 속성 기반 테스트는 jqwik(JUnit Platform 엔진).
- 위치: `P1-des:870`, `P1-tasks:7`, `P2-tasks:7`
- 근거: Spring Boot 4.1.1은 `junit-jupiter` 6.0.3을 관리합니다. jqwik 최신 1.10.1은 JUnit Platform 1.14.4 기반이고, 이후 릴리스는 "JUnit Platform 6 and thus Java >= 21"이라고 적혀 있습니다. 1.10.1이 Platform 6에서 도는지는 문서에 없습니다.
- 출처: https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html , https://jqwik.net/release-notes.html

## 맞는 주장

| # | 주장 | 위치 | 근거 |
|---|---|---|---|
| 8 | PR 파일 목록은 문서상 최대 3,000개, `per_page` 최대 100 | `P1-des:189-190`, `P1-req:67` | "Responses include a maximum of 3000 files", 기본 30개. https://docs.github.com/en/rest/pulls/pulls |
| 9 | 파일 `status` 값 7종, `patch`·`previous_filename`은 없을 수 있음 | `P1-des:191`, `P1-req:72` | enum이 `added/removed/modified/renamed/copied/changed/unchanged`이고 두 필드는 필수 아님. 같은 URL |
| 10 | PR 메타데이터에 `changed_files`가 있고 `body`는 null 가능 | `P1-req:61-62` | `changed_files` 필수 정수, `body` string or null. 같은 URL |
| 11 | webhook은 10초 안에 2XX 응답 | `P2-des:258` | "respond with a 2XX response within 10 seconds". https://docs.github.com/en/webhooks/using-webhooks/best-practices-for-using-webhooks |
| 12 | GitHub는 실패한 전달을 자동 재전송하지 않음 | `P2-des:176` | "GitHub does not automatically redeliver failed webhook deliveries". https://docs.github.com/en/webhooks/using-webhooks/handling-failed-webhook-deliveries |
| 13 | payload 상한 25MB | `P2-req:63`, `P2-des:263` | "Payloads are capped at 25 MB" (넘으면 전달하지 않음). https://docs.github.com/en/webhooks/webhook-events-and-payloads |
| 14 | `X-Hub-Signature-256`은 본문의 HMAC-SHA256 hex, `sha256=` 접두어, 상수 시간 비교, SHA-1은 레거시 | `P2-req:81-84`, `P2-des:281-285` | https://docs.github.com/en/webhooks/using-webhooks/validating-webhook-deliveries |
| 15 | `pull_request` action `opened/synchronize/reopened/closed/ready_for_review`, App payload에 `installation` 포함 | `P2-req:25`, `P2-des:255` | https://docs.github.com/en/webhooks/webhook-events-and-payloads |
| 16 | 리액션 webhook은 없음 | `P2-req:406` | 이벤트 목록에 리액션 이벤트가 없습니다. 같은 URL |
| 17 | JWT는 RS256, `iat` 60초 과거, `exp` 10분 이내 | `P2-des:363`, `P2-des:377` | https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app |
| 18 | `POST /app/installations/{id}/access_tokens`, `GET /app`의 `slug` | `P2-des:379`, `P2-des:855` | 응답 `token`, `expires_at`, 201. https://docs.github.com/en/rest/apps/apps |
| 19 | 리뷰 생성: `POST .../pulls/{n}/reviews`, event `COMMENT`에 body 필요, 코멘트에 `line`/`side` | `P2-des:825`, `P2-des:937`, `P2-req:265-266` | body는 "Required when using REQUEST_CHANGES or COMMENT". event를 비우면 PENDING. https://docs.github.com/en/rest/pulls/reviews |
| 20 | `position`이 아니라 `line` + `side=RIGHT`로 위치 지정 | `P2-des:840` | 단건 코멘트 API에서 `position`은 "closing down. Use line instead". https://docs.github.com/en/rest/pulls/comments |
| 21 | 리뷰 코멘트 목록, 리뷰별 코멘트 목록 경로 | `P2-des:824`, `P2-des:826` | https://docs.github.com/en/rest/pulls/comments , https://docs.github.com/en/rest/pulls/reviews |
| 22 | 이슈 코멘트 목록·생성, 수정은 `PATCH .../issues/comments/{id}` | `P2-des:827-829` | 목록은 ID 오름차순. https://docs.github.com/en/rest/issues/comments |
| 23 | 리액션 목록 경로, `+1`/`-1`, `user`는 null 가능, 404 | `P2-des:830`, `P2-des:838`, `P2-req:316` | https://docs.github.com/en/rest/reactions/reactions |
| 24 | App 권한은 Pull requests 읽기·쓰기 + Contents 읽기 + Metadata 읽기면 충분 | `P2-req:162` | 이슈 코멘트 생성·수정이 "Pull requests" write에도 올라 있고, contents·trees는 Contents read입니다. https://docs.github.com/en/rest/authentication/permissions-required-for-github-apps |
| 25 | fork PR에서는 `GITHUB_TOKEN`이 읽기 전용이고 비밀값이 전달되지 않음 | `P2-des:1168`, `P2-req:395` | https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows |
| 26 | contents API 경로, 폴더면 배열 반환 | `P1-des:220` | https://docs.github.com/en/rest/repos/contents |
| 27 | 모델 ID `claude-opus-5-5`, 입력 $4 / 출력 $20 | `PRD.md:108`, `P1-req:362` | https://platform.claude.com/docs/en/about-claude/models/overview |
| 28 | 이 모델은 thinking을 끌 수 없고 effort 기본값은 `medium` | `PRD.md:108`, `P1-des:24` | "Adaptive thinking is always on and can't be turned off", `disabled`는 400. https://platform.claude.com/docs/en/build-with-claude/effort |
| 29 | thinking 토큰은 출력으로 과금되고 `max_tokens`에 포함 | `PRD.md:108`, `P1-des:25`, `P1-req:391` | "billed as output tokens ... count toward `max_tokens`". https://platform.claude.com/docs/en/build-with-claude/thinking |
| 30 | `output_config.format`(`json_schema`), 구 `output_format` + 베타 헤더는 폐기 예정 | `P1-des:284`, `P1-des:354` | https://platform.claude.com/docs/en/build-with-claude/structured-outputs |
| 31 | 선택 파라미터 24개·union 16개 상한, `["string","null"]` 사용 가능 | `P1-des:287` | type 배열은 union으로 셉니다(스펙은 3개 사용). 같은 URL |
| 32 | enum 대소문자가 보장되지 않음 | `P1-des:288` | "don't guarantee the capitalization of string `enum`". 같은 URL |
| 33 | `refusal`·`max_tokens`면 스키마를 따르지 않을 수 있음 | `P1-des:289` | 같은 URL |
| 34 | 스키마를 바꾸면 프롬프트 캐시가 무효화됨 | `P1-des:272` | "Changing the `output_config.format` parameter will invalidate any prompt cache". 같은 URL |
| 35 | 캐시 순서는 tools → system → messages, 최소 길이보다 짧으면 캐시되지 않음 | `P1-des:271`, `PRD.md:108` | 오류 없이 캐시 없이 처리됩니다. https://platform.claude.com/docs/en/build-with-claude/prompt-caching |
| 36 | usage 네 가지 토큰을 따로 곱해 합산 | `P1-req:392` | `input_tokens`는 캐시 지점 뒤 토큰만이라 이중 계산이 없습니다. 같은 URL |
| 37 | 최대 출력 토큰 허용 범위 1~128,000 | `P1-req:364` | Opus 5.5 Max output 128K. https://platform.claude.com/docs/en/about-claude/models/overview |
| 38 | Claude API 429·5xx 재시도, 529 예시, `retry-after`는 초 단위 | `P1-req:408-411`, `P1-des:472` | 529 `overloaded_error`. https://platform.claude.com/docs/en/api/errors , https://platform.claude.com/docs/en/api/rate-limits |
| 39 | SDK 패키지 `com.anthropic`, `maxRetries`로 재시도 끄기, 타임아웃 설정 | `P1-des:346-350` | 기본 재시도 2회, 기본 타임아웃 10분. https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/java |
| 40 | Spring Boot 4.1 + Java 17, Jackson 3(`tools.jackson`) 기본 | `PRD.md:116`, `P1-des:494` | https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide , https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html |
| 41 | `flyway-core` + `flyway-database-postgresql`, 버전은 Boot 관리 | `P2-des:661` | 둘 다 12.4.0으로 관리됩니다. https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html |
| 42 | ArchUnit은 Spring Boot BOM에 없어 직접 고정 | `P2-des:1063` | 관리 목록에 없습니다. 최신 1.5.1에 `archunit-junit6`이 있습니다. https://www.archunit.org/userguide/html/000_Index.html |
| 43 | jqwik `tries` 기본 1,000, 실패 시 seed와 축소 반례 보고 | `P1-des:877`, `P1-des:889` | https://jqwik.net/docs/current/user-guide.html |

## 스펙이 spike로 남겨 둔 항목

### 문서로 이미 답이 나오는 것

- **파일 목록 상한과 `changed_files`** (`P1-des:190`): 문서 상한은 3,000개입니다. https://docs.github.com/en/rest/pulls/pulls
- **contents API 크기 상한** (`P1-des:220`): 1MB 이하만 base64 본문이 옵니다. 1~100MB는 raw 미디어 타입이 필요하고, 100MB 초과는 미지원입니다. https://docs.github.com/en/rest/repos/contents
- **트리 `truncated` 기준** (`P1-des:220`): 재귀 조회 시 100,000 항목 / 7MB입니다. https://docs.github.com/en/rest/git/trees
- **rate limit 403 헤더** (`P1-des:904`): `x-ratelimit-remaining: 0`, `x-ratelimit-reset`, `retry-after`입니다(위 2번).
- **SDK의 구조화 출력·effort 빌더** (`P1-des:354`): 타입 있는 빌더가 있습니다. `OutputConfig.builder().format(JsonOutputFormat.builder().schema(...).build())`와 `.effort(OutputConfig.Effort.MEDIUM)`이며, 추가 body 파라미터는 필요 없습니다. https://platform.claude.com/docs/en/build-with-claude/structured-outputs , https://platform.claude.com/docs/en/build-with-claude/effort
- **`claude-opus-5-5`의 structured outputs 지원** (`P1-des:355`): 지원 모델 목록에 있습니다. thinking 표시 기본값은 `omitted`라 빈 thinking 블록이 text 블록 앞에 옵니다. 문서의 Java 예제도 첫 text 블록을 고릅니다. https://platform.claude.com/docs/en/build-with-claude/thinking
- **usage 캐시 필드 이름, thinking의 `output_tokens` 포함** (`P1-des:356`): `cache_creation_input_tokens`, `cache_read_input_tokens`이고 thinking은 출력으로 과금됩니다.
- **캐시 최소 길이와 단가** (`P1-des:357`): Opus 5.5는 최소 512 토큰, 쓰기 $5(5분)/$8(1시간), 읽기 $0.20입니다. https://platform.claude.com/docs/en/build-with-claude/prompt-caching
- **SDK 재시도 끄기, 타임아웃, `retry-after` 접근** (`P1-des:358`): `maxRetries(0)`, 클라이언트 `.timeout(Duration)` 또는 `RequestOptions`, 예외의 `statusCode()`와 `headers()`입니다. https://platform.claude.com/docs/en/cli-sdks-libraries/sdks/java
- **Jackson 3 패키지** (`P1-des:494`): `tools.jackson`이고 `jackson-annotations`만 `com.fasterxml.jackson.core`에 남습니다.
- **Flyway 자동 설정 끄기** (`P2-des:1562`): Boot 4에서는 `spring-boot-flyway`/`spring-boot-starter-flyway`를 넣어야만 자동 설정이 켜집니다. `flyway-core`만 넣고 직접 호출하면 끌 것이 없습니다.
- **개인 키 PEM 종류** (`P2-des:378`): "`PKCS#1 RSAPrivateKey` format"입니다. 라이브러리 선택만 남습니다. https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/managing-private-keys-for-github-apps
- **Installation_Token 필드와 유효 기간** (`P2-des:379`): `token`, `expires_at`, 1시간입니다.
- **`pull-requests: write`만으로 이슈 코멘트 가능한지** (`P2-des:1185`): App 권한 표에서는 가능합니다. Actions `GITHUB_TOKEN`도 같은 표를 따를 것으로 보이지만 한 번 실행해 확인하면 됩니다.
- **ArchUnit 버전** (`P2-des:1063`): 1.5.1, JUnit 6용 `archunit-junit6`입니다.
- **Jackson 2와 3의 공존** (`P2-des:785`): 패키지가 달라 공존합니다. Boot 4.1.1은 Jackson 2 databind 2.21.5도 관리하고, Anthropic SDK는 2.13.4 이상이면 됩니다. OpenAPI 검증 도구의 3.1 지원은 확인하지 않았습니다.
- **fat jar 실행 방식** (`P1-des:905`): "fully executable jar" 내장 launch script는 제거됐고 `java -jar`는 그대로입니다. P2 워크플로의 `installDist` 방식은 영향이 없습니다.

### 실험이 실제로 필요한 것

- **리뷰 422 본문 형식과 리뷰당 코멘트 수 상한** (`P2-des:942`): 공식 문서는 422를 "Validation failed, or the endpoint has been spammed"로만 적습니다. 비공식 이슈 보고들은 코멘트 하나가 diff 밖이면 리뷰 전체가 거부되고 본문이 위치를 담지 않는다고 합니다(예: https://github.com/cli/cli/issues/13358). 사실이면 `RejectionParser`는 대부분 "식별 불가"가 됩니다.
- **게시 주체 login** (`P2-des:860`): `{slug}[bot]` 형식과 Installation_Token의 `GET /user` 403 여부는 문서에 없습니다.
- **webhook content type** (`P2-des:265`): 문서는 JSON과 form 인코딩 둘 다 가능하다고만 합니다. App에서 고정인지는 설정 화면에서 확인해야 합니다.
- **진행 중 HTTP 호출 중단** (`P2-des:345`): JDK 17 문서는 `cancel(true)`가 교환 취소를 "시도"한다고만 하고 시점은 보장하지 않습니다. Anthropic SDK의 취소 동작은 문서에 없습니다. https://docs.oracle.com/en/java/javase/17/docs/api/java.net.http/java/net/http/HttpClient.html
- **그 밖에**: 65,536자 단위, Tomcat `max-swallow-size`, `SmartInitializingSingleton` 순서, Flyway 실패 버전 API와 advisory lock, effort별 응답 시간.

## 구현에 영향이 큰 누락

스펙이 아예 언급하지 않은 외부 제약입니다.

1. **jqwik과 JUnit 6 조합**: Boot 4.1 BOM이 JUnit Platform 6.0.3을 끌어옵니다. jqwik 1.10.1이 안 돌면 속성 테스트 40여 개 계획 전체가 막히므로 1주차 첫 spike로 올려야 합니다. 대안은 JUnit 버전을 1.14.x로 내리거나 PBT 도구를 바꾸는 것입니다.
2. **1MB 초과 컨텍스트 파일**: contents API는 1~100MB 파일에 빈 `content`와 `encoding: "none"`을 돌려줍니다. base64만 가정하면 큰 `CLAUDE.md`나 스펙이 조용히 빈 문자열이 됩니다.
3. **GitHub 콘텐츠 생성 한도**: 분당 80건·시간당 500건이고 동시 요청은 100개까지입니다. 리뷰·이슈 코멘트 문서에 "Creating content too quickly ... may result in secondary rate limiting"이 있습니다.
4. **Actions `GITHUB_TOKEN` 한도**: 저장소당 시간당 1,000건입니다(PAT·App은 5,000건). `--publish`와 `feedback sync`의 페이지 순회가 여기에 걸립니다.
5. **Claude spend cap 429**: `retry-after`가 없고 다음 달까지 계속 실패합니다. `error.details.error_code = enforced_spend_limit_reached`로 구분해 재시도 없이 끝내야 합니다. https://platform.claude.com/docs/en/api/rate-limits
6. **Claude 180초 제한과 타임아웃 재시도**: 응답 못 받은 호출을 재시도할 때 서버 쪽에서 과금이 이어지는지는 확인하지 못했습니다. 또 `maxOutputTokens`를 128,000까지 허용하면서 비스트리밍 180초는 맞지 않습니다. 문서는 큰 `max_tokens`에 스트리밍을 권합니다.
7. **첫 스키마 사용 시 문법 컴파일 지연**: 컴파일 결과는 마지막 사용 후 24시간 캐시됩니다. 120초 측정(`P1-req:208`, `P2-req:352`)에서 첫 호출은 따로 봐야 합니다.
8. **refusal의 `stop_details`와 모델 fallback**: 문서는 Opus 5.5의 refusal을 다른 모델로 재시도하면 대개 처리된다고 안내합니다. ADR-0005의 fallback 결정에 반영할 내용입니다.
9. **리뷰의 `commit_id`와 outdated 코멘트**: 최신 커밋이 아니면 코멘트가 outdated가 될 수 있습니다. head 조회와 게시 사이 경합(`P2-req:306`)과 맞물립니다.
10. **effort 변경 시 캐시 무효화**: 최상위 effort를 요청 사이에 바꾸면 프롬프트 캐시가 깨집니다. 분할 모드의 Chunk들이 같은 effort를 써야 한다는 조건이 `P1-req:397`에 빠져 있습니다.

## 확인 불가 항목

- **파일 목록이 "실제로는 300개에서 끊긴다"** (`P1-des:190`): 커뮤니티 토론 #118311을 열어 보지 않았습니다. 공식 문서는 3,000개만 말합니다.
- **코멘트 본문 65,536자 제한** (`P2-req:251`, `P2-des:1001`): REST 문서에 길이 제한이 없습니다.
- **Installation_Token·Actions 토큰의 `GET /user` 403, `{slug}[bot]` login** (`P2-des:855-856`): users 문서는 PAT/OAuth 스코프만 적고 설치 토큰을 다루지 않습니다.
- **리뷰 생성 응답에 코멘트 ID가 없음** (`P2-des:938`): 가져온 문서 요약에서 응답 스키마를 명확히 확인하지 못했습니다.
- **구조화 출력과 thinking의 호환성**: 문서에 명시가 없습니다. Opus 5.5가 지원 목록에 있다는 간접 근거뿐입니다.
- **Java SDK의 `cache_control` 빌더와 usage 접근자 이름**: 가져온 SDK 페이지에 없습니다. API 필드 이름만 확인했습니다.
- **Testcontainers의 Boot 4.1 관리 버전과 아티팩트 이름**: 버전 표에서 찾지 못했습니다. 2.x에서 `testcontainers-` 접두어로 바뀌었다는 것은 비공식 글에서만 봤습니다.
- **Windows·macOS 호스팅 러너에 Docker가 없음** (`P2-tasks:338`): 러너 문서를 확인하지 않았습니다.
- **확인하지 않은 주장**: "Spring Boot가 YAML 파서를 포함"(`P1-des:27`), `ProblemDetail` RFC 9457(`P2-des:21`), Spring 컨텍스트 없는 `JdbcClient`(`P2-des:650`), `MessageDigest.isEqual` 상수 시간(`P2-des:285`), JDK `KeyFactory`의 PKCS#8 한정(`P2-des:378`), picocli `setParameterExceptionHandler`(`P1-des:362`), smee.io 권장(`PRD.md:119`).
