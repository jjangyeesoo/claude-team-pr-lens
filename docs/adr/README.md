# 아키텍처 결정 기록 (ADR)
새 결정은 `/adr <제목>`으로 추가한다. 이 목록은 CLAUDE.md가 import하므로 한 줄 요약으로 유지한다.

- [0001](0001-feature-based-packages.md) (0003으로 대체됨) 기능 단위 패키지 구조(`<feature>.{domain,api}`)를 쓴다
- [0002](0002-standard-error-response.md) 모든 API 에러는 `ErrorResponse { code, message, details }`로 응답한다
- [0003](0003-role-based-flat-packages.md) `com.prlens` 아래 역할 단위 평면 패키지를 쓰고 의존 규칙은 ArchUnit으로 강제한다
