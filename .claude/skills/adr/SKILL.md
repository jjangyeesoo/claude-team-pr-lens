---
name: adr
description: 아키텍처 결정 기록(ADR)을 작성하고 목록에 추가한다.
disable-model-invocation: true
argument-hint: "[결정 제목]"
---
다음 결정에 대한 ADR을 작성한다: $ARGUMENTS

1. `docs/adr/`에서 가장 큰 번호를 찾아 다음 번호(4자리)로 `docs/adr/NNNN-<kebab-slug>.md`를 만든다.
2. 형식: 제목 / 상태(proposed) / 날짜 / 배경 / 결정 / 고려한 대안(각각의 장단점) / 결과(트레이드오프, 후속 작업).
3. 정보가 부족하면 AskUserQuestion으로 배경과 대안을 묻는다. 추측으로 채우지 않는다.
4. `docs/adr/README.md` 목록에 한 줄 요약을 추가한다 (CLAUDE.md가 이 목록을 import한다).
