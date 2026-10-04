---
name: pr-ready
description: 현재 브랜치를 PR 올릴 수 있는 상태로 점검하고 PR 설명 초안을 만든다.
disable-model-invocation: true
---
현재 브랜치를 PR 전에 점검한다.

1. `git status`, `git diff main...HEAD --stat`으로 변경 범위를 확인한다.
   - 커밋하지 않은 변경이 있으면 먼저 알리고 멈춘다. reviewer는 `git diff main...HEAD`로 커밋된 변경만 본다.
   - PR 단위는 `tasks.md`의 상위 작업 하나다. 그 작업에 "PR 경계"가 적혀 있으면 이 브랜치가 경계 하나에 해당하는지 확인한다.
   - 크기는 `src/main` 변경만 센다(테스트, 픽스처, 문서 제외). 400줄을 넘으면 `tasks.md`의 PR 경계로 나눌 수 있는지 먼저 보고, 나눌 수 없으면 PR 설명에 넣을 이유를 한 줄 제안한다.
2. 바뀐 스택만 검증하고 결과(통과 수, 실패 여부)를 증거로 기록한다.
   - `backend/`가 바뀌었으면 `backend/`에서 `./gradlew spotlessApply test`
   - `web/`이 바뀌었으면 `web/`에서 `npm run verify`와 `npm run build`
   - backend 응답 DTO가 바뀌었는데 `web/src/lib/api/types.ts`가 그대로면 짚어 준다
3. reviewer 서브에이전트로 diff를 리뷰한다. 관련 스펙이 있으면 스펙 폴더 경로(`docs/specs/<이름>/`)와 작업 번호를 함께 넘긴다.
4. 리뷰 결과를 "반드시 수정"과 "선택"으로 나눠 보여 주고, "반드시 수정"만 고친다. 고친 뒤 테스트를 다시 실행한다.
5. `.github/pull_request_template.md` 형식으로 PR 설명 초안을 출력한다. 검증 증거(실행한 명령과 결과 요약)를 포함한다.
6. push나 PR 생성은 하지 않는다. 사용자가 직접 확인하고 올린다.
