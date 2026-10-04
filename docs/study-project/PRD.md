# PRD: PR Lens (가칭), 팀 컨텍스트 기반 PR 리뷰어

- 상태: **draft** (1주차에 팀 검토 후 approved로 변경) · 작성일: 2026-09-30
- 팀: 시니어 개발자 3명 (A, B, C) · 기간: 5주 · 투입: 1인당 주 __시간 (1주차 킥오프에서 합의하고, 그 값에 맞춰 ROADMAP 범위를 조정)
- 관련 문서: [ROADMAP](ROADMAP.md) · [진행 방식(플레이북)](PLAYBOOK.md) · [팀 방법론 가이드](../claude-code-team-methodology.md)

> 이 문서는 프로젝트 저장소의 `docs/study-project/PRD.md`에 있고, 이후 변경은 PR로 합니다. 가이드(`docs/claude-code-team-methodology.md`)도 같은 저장소에 있어 상대 링크가 그대로 동작합니다.

---

## 1. 배경과 문제

AI PR 리뷰 도구(CodeRabbit, Claude Code GitHub Action 등)는 이미 많습니다. 하지만 대부분 **범용 기준**으로 리뷰해서 다음 문제가 생깁니다.

- 팀 컨벤션을 모릅니다. "우리 팀은 엔티티를 응답으로 직접 반환하지 않는다" 같은 규칙을 지적하지 못합니다.
- 반대로 팀이 의도적으로 허용한 패턴을 문제로 지적해서 노이즈가 생깁니다.
- 지적의 근거가 없어서, 사람이 "이걸 반영해야 하나"를 매번 판단해야 합니다.

한편 Claude Code를 쓰는 팀은 이미 `CLAUDE.md`, `.claude/rules/`, `docs/adr/`, `docs/specs/`에 **팀의 기준을 글로 적어 두고** 있습니다([가이드 2장](../claude-code-team-methodology.md#2-팀-컨텍스트-공유-핵심-장)). 이 문서들을 리뷰 기준으로 쓰면 위 문제를 줄일 수 있습니다.

## 2. 목표

1. **팀 컨텍스트 기반 리뷰**: 리뷰 대상 저장소의 `CLAUDE.md`, `.claude/rules/`(경로 매칭 포함), PR에 링크된 스펙을 읽어 리뷰 기준으로 삼는다.
2. **근거 있는 지적**: 모든 지적에 근거(어느 규칙 파일, 어느 스펙 항목, 또는 "일반 원칙")를 붙인다.
3. **연구회 방법론 검증**: 이 프로젝트 자체를 가이드의 방법론(스펙 주도, 역할 순환, 컨텍스트 관리)으로 개발하고, 우리 PR을 우리 도구로 리뷰(도그푸딩)해서 결과를 측정한다.

### 성공 기준 (5주차 발표 시점)

| 지표 | 목표 | 측정 방법 |
|---|---|---|
| 도그푸딩 | 3주차 이후 팀 PR 전부에 시도, 80% 이상 리뷰 | PR Lens 코멘트, 또는 PR 설명에 첨부한 CLI 결과 (3주차 초반에는 자동 게시가 아직 없으므로) |
| 지적 채택률 | 사람이 "유효"로 판정한 지적 비율 50% 이상 | 2주차까지: PR 설명에 수기 기록(작성자가 기록, 리뷰어가 확인) / 3주차부터: FR-11로 자동 수집 |
| 근거 연결률 | 규칙·스펙 근거가 붙은 지적 비율 측정 (목표는 1주차에 기준선 보고 결정) | 결과 JSON의 `basis` 필드 집계 |
| PR당 비용 | 측정해서 보고 (상한: 1회 리뷰 $0.50) | API 응답 `usage` 집계 |
| 응답 시간 | 400줄 PR 기준 2분 이내 | 실행 로그 |

## 3. 사용자와 사용 시나리오

- **1차 사용자: 연구회 팀 자신.** 팀 저장소의 PR을 리뷰받는다.
- **2차 사용자: Claude Code를 쓰는 2~5명 규모 팀.** 발표 후 다른 팀이 설치해 볼 수 있는 수준을 목표로 한다.

| # | 시나리오 | 단계 |
|---|---|---|
| S1 | 개발자가 PR을 올리기 전후에 터미널에서 `prlens review <PR URL>`을 실행해 리뷰를 본다 | P1 (CLI) |
| S2 | PR이 열리거나 커밋이 추가되면 자동으로 리뷰가 실행되고, PR에 요약 코멘트와 라인 코멘트가 달린다 | P2 (자동화) |
| S3 | 리뷰어가 웹에서 PR별 리뷰 이력과, 어떤 규칙이 자주 위반되는지 통계를 본다 | P3 (웹) |

## 4. 기능 요구사항

### P1: CLI (2주차)

- **FR-1 PR 가져오기**: GitHub PR URL을 받아 메타데이터(제목, 본문, base/head SHA)와 변경 파일별 diff를 가져온다. 인증은 환경변수 `GITHUB_TOKEN`(PAT).
- **FR-2 diff 필터링**: 바이너리, lock 파일, 생성 코드, 비밀정보 패턴(`.env*`, `secrets/**`)에 해당하는 파일은 LLM에 보내지 않고 "제외됨"으로 표시한다. 제외 패턴은 설정 파일로 바꿀 수 있다.
- **FR-3 팀 컨텍스트 수집**: base 브랜치 기준으로 대상 저장소의 다음 파일을 가져온다.
  - 루트 `CLAUDE.md`와 `.claude/CLAUDE.md`
  - **변경 파일의 상위 폴더에 있는 `CLAUDE.md`** (예: `backend/CLAUDE.md`, `web/CLAUDE.md`). Claude Code가 그 폴더 파일을 다룰 때 로드하는 지시문이라, 빼면 스택별 규칙을 놓친다
  - `.claude/rules/**/*.md` 중 `paths` 프런트매터가 변경 파일과 매칭되는 것 (`paths`가 없으면 항상 포함). glob은 `**`와 중괄호 확장(`*.{ts,tsx}`)을 지원해야 한다
  - `CLAUDE.md` 안의 `@path` import (최대 4단계)
  - PR 본문에 `docs/specs/...` 링크가 있으면 그 스펙
- **FR-4 리뷰 생성**: diff와 컨텍스트로 Claude API를 호출하고, 구조화된 출력(JSON 스키마)으로 결과를 받는다. 결과 스키마는 5장 참고.
- **FR-5 출력**: 기본은 터미널용 Markdown, `--format json`이면 JSON. 종료 코드는 `blocker` 지적이 있으면 1, 없으면 0 (CI에서 쓸 수 있게).
- **FR-6 크기 상한**: 변경 줄 수가 상한(기본 400줄)을 넘으면 파일 단위로 나눠 리뷰하고 결과를 합친다. 상한의 3배를 넘으면 "PR을 나누세요"라는 안내와 함께 요약 리뷰만 한다.

### P2: 자동화와 저장 (3주차)

- **FR-7 webhook 수신**: GitHub App의 `pull_request` 이벤트(`opened`, `synchronize`, `reopened`)를 받는다. 서명(`X-Hub-Signature-256`)을 검증한다.
- **FR-8 비동기 실행과 중복 방지**: 리뷰는 비동기로 실행한다. 같은 `(저장소, PR, head SHA)` 조합은 한 번만 리뷰한다.
- **FR-9 결과 저장**: 리뷰 실행과 지적 사항을 DB에 저장한다. CLI 실행 결과도 `--save` 옵션으로 저장할 수 있다.
- **FR-10 GitHub 게시**: PR에 요약 코멘트 1개와, 라인을 특정할 수 있는 지적은 라인 코멘트로 게시한다. 다시 실행하면 이전 요약 코멘트를 갱신한다(코멘트가 쌓이지 않게).
- **FR-11 채택/기각 기록**: 지적마다 채택/기각을 자동으로 수집한다. 수집 방식은 3주차 스펙에서 정한다(후보: 라인 코멘트의 👍/👎 리액션). 그 전까지는 PR 설명의 수기 기록(작성자가 기록, 리뷰어가 확인)을 쓰고, 이 기능이 범위에서 빠져도 수기 기록을 유지한다.

### P3: 웹 조회 (4주차)

- **FR-12 조회 API**: 저장소별 PR 목록, PR별 리뷰 실행 이력, 실행별 지적 목록을 REST API로 제공한다.
- **FR-13 PR 리뷰 화면**: PR 목록 → PR 상세(리뷰 실행 이력, 지적 목록, 근거 규칙 링크).
- **FR-14 통계 화면**: 규칙별 지적 횟수(상위 N개), 심각도별 분포, 채택률, PR당 비용 추이.

## 5. 리뷰 결과 스키마 (초안)

```json
{
  "summary": "변경 요약과 전체 평가 (3~5문장)",
  "findings": [
    {
      "file": "src/main/java/.../MemoController.java",
      "line": 42,
      "severity": "blocker | major | minor | nit",
      "category": "correctness | security | convention | test | design",
      "message": "무엇이 문제인가",
      "suggestion": "어떻게 고치면 되는가 (선택)",
      "basis": { "type": "rule | spec | general", "ref": ".claude/rules/backend/api-design.md" }
    }
  ],
  "excludedFiles": ["package-lock.json"],
  "usage": { "inputTokens": 0, "outputTokens": 0, "model": "claude-opus-5-5" }
}
```

- `basis.type = rule | spec`이면 `ref`에 실제로 컨텍스트에 포함된 파일 경로만 허용한다(검증 로직으로 확인, 없는 파일을 지어내면 `general`로 강등).
- `line`은 diff의 변경 라인 범위 안이어야 라인 코멘트로 게시한다. 범위 밖이면 요약 코멘트에 포함한다.

## 6. 비기능 요구사항

- **보안**
  - GitHub 토큰, Anthropic API 키, webhook secret은 환경변수로만 받고 로그에 남기지 않는다.
  - 리뷰 대상은 **연구회 저장소와 공개 저장소로 한정**한다. 회사 코드를 외부 LLM으로 보내지 않는다.
  - diff 속의 지시문(프롬프트 인젝션)을 리뷰 지시로 따르지 않도록 시스템 프롬프트에서 diff를 "데이터"로 구분한다.
- **비용**: 기본 모델은 `claude-opus-5-5`(입력 $4 / 출력 $20 per 1M 토큰, 2026-09 기준). 400줄 PR 한 번에 입력 약 2만, 출력 약 3천 토큰이면 약 $0.14입니다. 이 모델은 thinking을 끌 수 없고 thinking 토큰도 출력으로 과금되므로 실제 비용은 이보다 큽니다. 1주차 기술 검증에서 실측합니다. 시스템 프롬프트와 저장소 공통 컨텍스트(base 기준 CLAUDE.md, rules)처럼 반복되는 앞부분은 prompt caching으로 줄인다(모델별 최소 길이보다 짧으면 캐시되지 않는다). 모델과 effort 설정은 1주차 ADR에서 정하고 설정 파일로 바꿀 수 있게 한다. 이 모델의 effort 기본값은 `medium`이라 이전 모델(`high`)과 다르므로 명시적으로 정한다.
- **신뢰성**: API 오류(429, 5xx)는 재시도한다. `stop_reason`이 `refusal`이나 `max_tokens`이면 결과를 "불완전"으로 표시하고 숨기지 않는다.
- **이식성**: 팀원 OS가 섞여 있으므로 Windows/macOS/Linux에서 CLI가 동작해야 한다.

## 7. 기술 스택 (1주차 ADR로 확정)

| 영역 | 선택 | 비고 |
|---|---|---|
| BE / 리뷰 엔진 | Java 17, Spring Boot 4.1, Gradle(Kotlin DSL) | `claude-team-starter`의 `backend/`를 그대로 사용 |
| CLI | BE와 같은 코드베이스 (picocli 또는 Spring Shell) | 리뷰 로직을 CLI와 서버가 공유 |
| LLM | Anthropic Java SDK (`com.anthropic`), 구조화된 출력 | ADR: 모델·effort 선택 |
| GitHub 연동 | REST API. P1은 PAT, P2는 GitHub App | 로컬 webhook 테스트는 smee.io |
| DB | PostgreSQL (로컬은 Docker Compose), 테스트는 Testcontainers 또는 H2 | ADR로 확정 |
| FE | Next.js (App Router), TypeScript | **조회 전용**. 비즈니스 로직은 BE에만 둔다 |
| 저장소 구조 | 모노레포: `backend/`, `web/` | 스택별 rules를 경로로 분리 ([가이드 부록 A](../claude-code-team-methodology.md#7-부록-a-공통-코어와-스택별-오버레이-구분표)) |

## 8. 범위 밖 (Out of scope)

- 코드 자동 수정 커밋(리뷰만 한다)
- GitHub 외 플랫폼(GitLab, Bitbucket)
- 사용자 인증, 멀티 테넌시, 과금 (웹은 로컬 또는 단일 팀 데모 환경)
- 운영 배포와 가용성 보장 (데모 가능 수준까지)
- 대형 PR(상한의 3배 초과)의 상세 리뷰

## 9. 위험과 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| 지적이 많고 노이즈가 큼 | 도그푸딩 중단 | 심각도 하한 설정(기본 `minor` 이상만 게시), 기각 사유를 모아 프롬프트와 규칙 개선 |
| 근거(`basis`)를 지어냄 | 신뢰도 하락 | 5장의 검증 로직, 테스트 픽스처로 회귀 확인 |
| webhook 로컬 개발이 막힘 | 3주차 지연 | 1주차 기술 검증(spike)에 smee.io 연결 포함, 막히면 CLI + GitHub Actions 실행으로 대체 |
| 한 주에 역할이 바뀌며 맥락 유실 | 품질 저하 | [플레이북](PLAYBOOK.md)의 금요일 회고와 인수인계 노트 |
| API 비용 초과 | 실험 중단 | 1주차에 월 예산 합의, 실행마다 `usage` 기록, 상한 초과 시 CLI가 경고 |

## 10. 결정이 필요한 것 (1주차)

스타터에 이미 ADR 0001(기능 단위 패키지), 0002(표준 에러 응답)가 있으므로 새 ADR은 0003부터 번호를 매깁니다. 스타터 ADR을 유지할지도 1주차에 확인합니다.

- [ ] 제품 이름 확정 (가칭 PR Lens)
- [ ] ADR-0003 저장소 구조 (스타터의 모노레포 `backend/` + `web/` 그대로 사용 여부)
- [ ] ADR-0004 CLI 라이브러리 (picocli vs Spring Shell)
- [ ] ADR-0005 LLM 모델과 effort 기본값, 비용 상한, refusal 처리(서버 측 fallback 사용 여부)
- [ ] ADR-0006 DB와 테스트 전략
- [ ] 채택/기각 **수기** 기록 형식 (PR 설명의 칸 이름과 기록 주체). 자동 수집(FR-11) 방식은 3주차 스펙에서 결정
- [ ] API 키 발급 주체와 월 예산
- [ ] 1인당 주당 투입 시간
