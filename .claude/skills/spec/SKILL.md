---
name: spec
description: 새 기능의 스펙 문서를 인터뷰 방식으로 작성한다. 구현 전에 사용.
disable-model-invocation: true
argument-hint: "[기능 한 줄 설명]"
---
다음 기능의 스펙을 작성한다: $ARGUMENTS

1. 먼저 관련 코드를 가볍게 탐색해 현재 구조(패키지, 기존 API, 에러 처리)를 파악한다.
2. AskUserQuestion으로 나를 인터뷰한다. 뻔한 질문은 생략하고 요구사항의 모호한 부분, 엣지 케이스, API 형태, 데이터 모델, 트레이드오프 가운데 내가 놓쳤을 만한 것 위주로 묻는다. 한 번에 최대 4개씩 묻고, 충분해질 때까지 반복한다.
3. `docs/specs/_template.md` 형식으로 `docs/specs/<yyyy-mm-dd>-<kebab-slug>.md`를 작성한다.
   - 변경할 패키지·클래스와 API 시그니처를 구체적으로 적는다
   - "범위 밖" 항목을 반드시 적는다
   - "검증 방법"에는 실행할 테스트 클래스와 수동 확인 절차(curl 예시)를 적는다
4. 마지막에 "새 세션에서 `@docs/specs/<파일>` 구현해"라고 안내한다. 이 세션에서는 구현하지 않는다.
