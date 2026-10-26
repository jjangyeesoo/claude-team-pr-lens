# P1 CLI 스펙 색인

`prlens review <PR URL>`로 팀 컨텍스트 기준 리뷰를 터미널에 출력합니다(PRD FR-1~6, ROADMAP 2주차 M1).

- 상태 (2026-10-04): 스펙 검토와 독립 검증의 지적을 반영함. 팀 승인 전. 코드는 아직 없음
- 규모: 요구사항 22개(인수 기준 225개), 속성 44개, 말단 작업 93개(필수 58, 선택 `*` 35)
- 읽는 법은 [../README.md](../README.md). 절은 제목으로 찾습니다

## 문서와 절

| 문서 | 절 |
|---|---|
| [requirements.md](requirements.md) | Glossary · Requirement 1~8 (T2: PR 가져오기, diff, 필터, 컨텍스트 수집) · 9, 11~14, 16, 20 (T1: 리뷰 생성, 검증, 분할, 인젝션 방지, 불완전 결과, 비용) · 10, 15, 18, 22 (T3: 직렬화, 출력, 설정, 이식성) · 17, 19, 21 (리드: 공유 인터페이스, 비밀정보, 재시도) · 결정 대기 항목 |
| [design.md](design.md) | Overview · Architecture(계층과 의존 방향, 패키지 배치, 실행 순서) · Components and Interfaces(공유 경계, T2, T1, T3) · Data Models(레코드, ReviewResult JSON 형식, 설정 파일) · Correctness Properties(1~44) · Error Handling(예외 계층과 종료 코드, 재시도 정책, 비밀정보 마스킹, 이식성, 요구사항 공백) · Testing Strategy(도구, 속성 테스트 규칙, 예시·통합 테스트, 1주차 spike 확인 목록) |
| [tasks.md](tasks.md) | Overview(선머지 범위, 트랙 사이 의존, PR 단위) · Tasks · Notes(spike 의존, 결정 대기가 막는 작업, 데모 최소 경로) · Task Dependency Graph |

## 트랙, 패키지, 작업

| 트랙 (담당) | 패키지 | 작업 |
|---|---|---|
| 공유 (리드 B) | `model`, `support` | 1~4. 월요일 선머지는 1.1, 2.1~2.3 |
| T2 GitHub 연동 (A) | `github`, `pullrequest`, `diff`, `glob`, `filter`, `context` | 5~9 |
| T1 리뷰 엔진 (B) | `llm`, `review` | 10~14 |
| T3 CLI 출력 (C) | `codec`, `output`, `config`, `cli` | 15~19 |
| 컨텍스트 (A) | `.claude/`, `CLAUDE.md` | 23 |
| 통합 (T3 주도) | 픽스처, CI | 20~22 |

Glossary 용어는 밑줄 표기(`Diff_Parser`), 클래스는 붙여 쓴 이름(`DiffParser`)입니다. 다른 것은 둘입니다: `Basis_Validator` → `FindingValidator`, `Output_Formatter` → `MarkdownFormatter`와 `ResultCodec`.

## 결정 대기 (팀이 정함. 그 전에는 제안값으로 구현)

원본은 requirements.md "결정 대기 항목"(D), design.md "요구사항 공백"(G), tasks.md Notes(막는 작업)입니다.

| ID | 내용 | 결정할 곳 | 제안값 |
|---|---|---|---|
| D-1 | CLI 라이브러리 | 확정: [ADR 0004](../../adr/0004-cli-library-picocli.md) | picocli |
| D-2 | 모델, effort, 최대 출력 토큰, refusal fallback | 확정 ([ADR 0005](../../adr/0005-llm-settings.md)) | `claude-opus-5-5`, `medium`, 16,000(상한 20,000), fallback 미사용 |
| D-3 | 1회 비용 상한 | 1주차 합의 | $0.50 |
| D-4 | 불완전 결과와 요약 전용 모드의 종료 코드 | T3 스펙 검토 | 불완전 3. 요약 전용은 지금 0 |
| D-5 | 설정 파일 형식과 위치 | T3 | YAML, `./.prlens.yml` → `~/.prlens.yml` |
| D-6 | 생성 코드 기본 제외 패턴 | T2 | 4개 패턴 |
| D-7 | 스펙 파일의 head SHA fallback | T2 스펙 검토 | base 404일 때만 |
| D-8 | 리뷰 대상 저장소 한정 (PRD 보안) | 1주차 합의 | P1은 한정하지 않음 |
| G-1~G-7 | patch 해석 오류, 줄 수 0, `*` 포함 비밀값, 무효 glob, 출력 토큰 기본값, GitHub 상태 매핑, enum 대소문자 | 스펙 승인 때 | 설계의 처리대로 작업에 반영됨 |
| G-8 | 리뷰되지 않은 파일이 있어도 `complete` | 팀 | 사유 표시까지만 |
| G-9 | 트리가 잘리면 rules를 열거 못 함 | 팀 | 경고 후 rule 생략 |
| G-10 | 토큰 단가 일부 지정 | 스펙 승인 때 | 모델 단위 병합 |
| G-11 | 컨텍스트 크기 상한 없음 | 팀 | 상한 없음 |
| G-12 | 1MB 초과 컨텍스트 파일 | 팀 | 경고 후 건너뜀 |
| G-13 | 응답 `file`이 이스케이프된 경로일 때의 비교 | 팀 | 비교 전 `file`만 `unescape` |
| G-14 | LLM이 만든 문자열(`summary`, `file`)의 Markdown 구조 문자 처리 | 팀 | `message`·`suggestion`은 들여써 목록 안에 두고 `summary`·경로는 원문 그대로 |
| G-15 | PR 템플릿의 스펙 폴더 경로 (폴더 링크는 건너뜀) | 팀 | 폴더를 그 폴더의 `requirements.md`, `design.md`, `tasks.md`로 펼치거나 템플릿을 파일 경로로 바꿈 |
| G-16 | 스펙 경로 끝에 붙은 조사·구두점 | 팀 | 끝의 `.,;:`를 떼고 한글은 경로 문자에서 뺌 |
| G-17 | URL ref에 `/`가 든 링크의 경계 | 팀 | 첫 `/docs/specs/`를 경계로 보는 현재 동작을 요구사항에 명시 |
| G-18 | `basis.ref` 비교 대상에 `Revision.HEAD` 스펙 경로 포함 | 팀 | 포함(요구사항 11.2~11.3 문구대로 전체 경로) |
| G-19 | 읽은 텍스트의 BOM과 비ASCII 공백 | 팀 | 텍스트 파일도 맨 앞 BOM 하나를 벗겨 냄, `@import`·스펙 링크의 공백은 ASCII만 |
| G-20 | 디버그 모드를 켜는 방법 | 팀 | 환경변수 `PRLENS_DEBUG`가 비어 있지 않으면 켜짐. 예상하지 못한 예외의 스택과 HTTP 디버그 로그에 같은 조건을 씀 |
| G-21 | Claude API의 429 외 4xx 응답 사유(오류 종류, 본문 message)를 사용자에게 보여 줄지 | 팀 | 메시지에 오류 종류를 더하고 본문 message는 디버그 모드(G-20)에서만 stderr에 냄 |

## 관련 문서
- 패키지 구조: [ADR 0003](../../adr/0003-role-based-flat-packages.md)
- 확인한 기술 사실: [spikes/2026-10-04-build-stack.md](../../spikes/2026-10-04-build-stack.md), design.md "1주차 spike 확인 목록"
- 이 스펙을 고친 근거: `docs/spec-review/`의 01, 05와 커밋 `282d1ab`. 함께 근거로 쓴 02와 04(B)는 반영 뒤 지웠습니다(원문은 각각 `282d1ab` 이전, `f67a6b1`까지의 이력)
