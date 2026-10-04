# 아키텍처 결정 기록 (ADR)
새 결정은 `/adr <제목>`으로 추가한다. 이 목록은 CLAUDE.md가 import하므로 한 줄 요약으로 유지한다.

- [0001](0001-feature-based-packages.md) 기능 단위 패키지 구조(`<feature>.{domain,api}`)를 쓴다
- [0002](0002-standard-error-response.md) 모든 API 에러는 `ErrorResponse { code, message, details }`로 응답한다
