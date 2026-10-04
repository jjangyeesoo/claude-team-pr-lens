---
paths:
  - "web/src/**/*.{ts,tsx}"
---
# Next.js 코드 규칙
- 컴포넌트는 기본이 Server Component다. `"use client"`는 이벤트 핸들러, 상태, 브라우저 API가 필요한 가장 작은 컴포넌트에만 붙인다
- 데이터 조회는 Server Component에서 `src/lib/api/<도메인>.ts` 함수를 await한다. 클라이언트 컴포넌트에서 backend를 직접 `fetch`하지 않는다
- backend 응답 타입은 `src/lib/api/types.ts`의 타입만 쓴다. 컴포넌트 안에서 응답 모양을 새로 정의하지 않는다
- `any`를 쓰지 않는다. 모르는 값은 `unknown`으로 받고 좁힌다
- 날짜는 backend에서 ISO 문자열로 온다. 화면에 보여 줄 때만 변환하고, 원본은 `<time dateTime>`에 둔다
- 로딩·에러 UI는 라우트 폴더의 `loading.tsx`, `error.tsx`를 우선 쓴다

# 테스트 규칙 (Vitest)
- 테스트 파일은 대상 옆에 `*.test.ts(x)`로 둔다
- `src/lib/`의 순수 함수(파싱, 변환, 계산)는 반드시 단위 테스트한다. `fetch` 호출부는 얇게 유지해서 테스트할 로직이 순수 함수로 빠지게 한다
- 테스트 이름은 동작을 설명하는 camelCase 문장으로 쓴다 (backend와 같은 규칙)
- IMPORTANT: 테스트를 통과시키려고 assertion을 지우거나 `.skip`을 붙이지 않는다
