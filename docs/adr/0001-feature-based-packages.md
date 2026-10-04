# 0001. 기능 단위 패키지 구조
- 상태: accepted · 날짜: 2026-09-27

## 배경
계층 단위 패키지(controller/, service/, repository/)는 기능이 늘수록 한 기능의 코드가 흩어져, 사람과 AI 모두 변경 범위를 파악하기 어렵다.

## 결정
`com.example.starter.<feature>.{domain,api}` 구조를 쓴다. `domain`은 `api`에 의존하지 않는다.

## 고려한 대안
- 계층 단위 패키지: 익숙하지만 기능 단위 변경이 여러 패키지에 퍼진다.
- 헥사고날(ports/adapters): 소규모 팀의 초기 단계에는 과하다.

## 결과
- 새 기능은 새 패키지 하나로 시작한다. `/new-endpoint` 스킬이 이 구조를 따른다.
- 기능 간 공통 코드는 `common/`에 두되, 최소한으로 유지한다.
