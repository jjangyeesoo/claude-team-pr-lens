# <기능명>
- 이슈: #<번호> · 담당: @<이름> · 상태: draft | approved | done · 작성일: <yyyy-mm-dd>

## 배경 / 목표
<왜 필요한가, 누구를 위한 것인가>

## 요구사항 (수락 기준)
- [ ] <사용자는 ... 할 수 있다>

## 범위 밖 (Out of scope)
- <이번에 하지 않는 것>

## 설계
- backend 변경 (패키지·클래스):
- web 변경 (라우트·컴포넌트, 없으면 "없음"):
- API: `METHOD /api/v1/...`, 요청/응답 예시
- 데이터 모델:

## 엣지 케이스
- <입력 경계값, 동시성, 권한, 없는 리소스 등>

## 검증 방법
- 자동: `backend`에서 `./gradlew test --tests "<테스트 클래스>"` 통과 / `web`에서 `npm run verify` 통과
- 수동(E2E):
  1. `backend`에서 `./gradlew bootRun` (화면이 있으면 `web`에서 `npm run dev`도)
  2. `curl -i -X POST localhost:8080/api/v1/... -H 'Content-Type: application/json' -d '{...}'` → 기대 결과
