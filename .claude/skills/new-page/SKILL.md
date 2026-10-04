---
name: new-page
description: 팀 컨벤션에 맞춰 backend 데이터를 보여 주는 Next.js 페이지(라우트, API 호출 함수, 타입, 테스트)를 만든다. 새 화면을 추가할 때 사용.
argument-hint: "[/경로 화면 설명]"
---
다음 화면을 추가한다: $ARGUMENTS

작업 위치: `web/`. 기준 예시: `src/app/memos/page.tsx`, `src/lib/api/memos.ts`, `src/lib/api/types.ts`, `src/lib/api/http.test.ts`.

1. 먼저 `node_modules/next/dist/docs/`에서 사용할 API(라우팅, 데이터 조회, `loading`/`error` 파일)의 문서를 확인한다. 이 버전의 Next.js는 학습 데이터와 다를 수 있다.
2. 필요한 backend API가 있는지 확인한다. 없으면 멈추고 `/new-endpoint`로 먼저 만들지 사용자에게 묻는다.
3. 응답 타입을 `src/lib/api/types.ts`에 backend DTO와 1:1로 추가한다.
4. 호출 함수를 `src/lib/api/<도메인>.ts`에 추가한다 (`import "server-only"`, `apiUrl`과 `parseResponse` 사용).
5. 변환·계산 로직이 있으면 순수 함수로 빼고, 실패하는 Vitest 테스트를 먼저 작성한다.
6. 페이지는 Server Component로 만들고 `await connection()` 뒤에 데이터를 조회한다. 상호작용이 필요한 부분만 `"use client"` 컴포넌트로 분리한다.
7. `web/`에서 `npm run verify`와 `npm run build`로 확인한다. backend가 떠 있다면 `npm run dev`로 화면도 확인한다.
