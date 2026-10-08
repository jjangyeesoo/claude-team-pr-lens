# 아키텍처 결정 기록 (ADR)
새 결정은 `/adr <제목>`으로 추가한다. 이 목록은 CLAUDE.md가 import하므로 한 줄 요약으로 유지한다.

- [0001](0001-feature-based-packages.md) (0003으로 대체됨) 기능 단위 패키지 구조(`<feature>.{domain,api}`)를 쓴다
- [0002](0002-standard-error-response.md) 모든 API 에러는 `ErrorResponse { code, message, details }`로 응답한다
- [0003](0003-role-based-flat-packages.md) `com.prlens` 아래 역할 단위 평면 패키지를 쓰고 의존 규칙은 ArchUnit으로 강제한다
- [0004](0004-cli-library-picocli.md) CLI 명령 해석은 picocli로 하고 Spring 컨텍스트 없이 실행한다. picocli 타입은 `cli` 패키지에서만 쓴다
- [0005](0005-llm-settings.md) LLM 기본값은 `claude-opus-5-5`, effort `medium`(요청에 항상 명시), 최대 출력 토큰 16,000(상한 20,000)이다. 캐시 지점은 항상 두고 refusal fallback은 P1에서 쓰지 않는다
- [0008](0008-keep-api-conventions.md) P2·P3의 HTTP API는 `/api/v1/` 경로와 `ErrorResponse`를 따른다 (0006~0007은 PRD 10장이 예약, 미작성)
