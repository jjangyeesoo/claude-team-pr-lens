---
name: pr-ready
description: 현재 브랜치를 PR 올릴 수 있는 상태로 점검하고 PR 설명 초안을 만든다.
disable-model-invocation: true
---
현재 브랜치를 PR 전에 점검한다.

1. `git status`, `git diff main...HEAD --stat`으로 변경 범위를 확인한다. 400줄을 넘으면 분리 방안을 제안한다.
2. 바뀐 스택만 검증하고 결과(통과 수, 실패 여부)를 증거로 기록한다.
   - `backend/`가 바뀌었으면 `backend/`에서 `./gradlew spotlessApply test`
   - `web/`이 바뀌었으면 `web/`에서 `npm run verify`와 `npm run build`
   - backend 응답 DTO가 바뀌었는데 `web/src/lib/api/types.ts`가 그대로면 짚어 준다
3. reviewer 서브에이전트로 diff를 리뷰한다. 관련 스펙이 있으면(`docs/specs/`) 경로를 함께 넘긴다.
4. 리뷰 결과를 "반드시 수정"과 "선택"으로 나눠 보여 주고, "반드시 수정"만 고친다. 고친 뒤 테스트를 다시 실행한다.
5. `.github/pull_request_template.md` 형식으로 PR 설명 초안을 출력한다. 검증 증거(실행한 명령과 결과 요약)를 포함한다.
6. push나 PR 생성은 하지 않는다. 사용자가 직접 확인하고 올린다.
