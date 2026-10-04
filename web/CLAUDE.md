@AGENTS.md

# web (Next.js FE)

Next.js 16 (App Router) / React 19 / TypeScript. backend REST API를 **조회해서 보여 주는** 역할만 한다. 이 파일은 Claude가 `web/` 아래 파일을 다룰 때만 로드된다.

## Commands (`web/`에서 실행)

- 최초 1회: `npm ci`
- 개발 서버: `npm run dev` (포트 3000). backend는 `../backend`에서 `./gradlew bootRun`
- 검증 한 번에: `npm run verify` (format → lint → typecheck → test). Stop hook도 이 명령을 실행한다
- 단일 테스트: `npx vitest run src/lib/api/http.test.ts`
- 빌드 확인: `npm run build`

## Architecture

- `src/app/`: 라우트. 기본은 Server Component이고, 상호작용이 필요한 부분만 `"use client"` 컴포넌트로 분리한다
- `src/lib/api/`: backend 호출은 **여기서만** 한다
  - `types.ts`: backend DTO와 1:1로 맞춘 타입. backend DTO를 바꾸면 같은 PR에서 함께 바꾼다
  - `http.ts`: URL 조합, 응답/에러 파싱 (Next.js에 의존하지 않는 순수 함수, 단위 테스트 대상)
  - `<도메인>.ts`: `import "server-only"`를 붙인 호출 함수. backend 주소(`API_BASE_URL`)가 클라이언트 번들에 들어가지 않게 한다
- backend 데이터를 쓰는 페이지는 `await connection()`으로 요청 시점에 렌더링한다 (빌드할 때 backend가 필요 없게)

## Gotchas

- IMPORTANT: 비즈니스 로직과 데이터 저장은 backend의 책임이다. Route Handler(`route.ts`)나 Server Action에 도메인 로직을 넣지 않는다
- 에러 응답은 backend 표준 `ErrorResponse { code, message, details }`다. `ApiError`로 받아서 처리한다
- 의존성 추가(`package.json` 수정, `npm install <pkg>`)는 사람의 승인이 필요하다. 먼저 이유를 설명하고 묻는다
- `next/font/google`은 쓰지 않는다. 빌드할 때 네트워크가 필요해져 오프라인·CI 빌드가 깨진다
