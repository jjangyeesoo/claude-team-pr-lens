// backend 호출의 공통 로직. Next.js에 의존하지 않는 순수 함수로 두어 단위 테스트한다.
import type { ErrorResponse } from "./types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly body: ErrorResponse | null,
  ) {
    super(body ? `${body.code}: ${body.message}` : `HTTP ${status}`);
    this.name = "ApiError";
  }
}

/** base URL 끝의 '/'와 path 앞의 '/'가 겹치거나 빠져도 올바른 URL을 만든다. */
export function apiUrl(baseUrl: string, path: string): string {
  return `${baseUrl.replace(/\/+$/, "")}/${path.replace(/^\/+/, "")}`;
}

/** 2xx면 JSON 본문을, 아니면 ErrorResponse를 담은 ApiError를 던진다. */
export async function parseResponse<T>(res: Response): Promise<T> {
  if (res.ok) {
    return (await res.json()) as T;
  }
  throw new ApiError(res.status, await readErrorBody(res));
}

async function readErrorBody(res: Response): Promise<ErrorResponse | null> {
  try {
    const body: unknown = await res.json();
    return isErrorResponse(body) ? body : null;
  } catch {
    return null; // 프록시 HTML 에러 페이지 등 JSON이 아닌 응답
  }
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.code === "string" && typeof v.message === "string";
}
