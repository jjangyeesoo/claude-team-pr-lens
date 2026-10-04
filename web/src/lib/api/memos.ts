// 서버에서만 실행된다. backend 주소가 클라이언트 번들에 들어가지 않도록 server-only로 막는다.
import "server-only";
import { apiUrl, parseResponse } from "./http";
import type { Memo } from "./types";

const API_BASE_URL = process.env.API_BASE_URL ?? "http://localhost:8080";

export async function listMemos(): Promise<Memo[]> {
  const res = await fetch(apiUrl(API_BASE_URL, "/api/v1/memos"));
  return parseResponse<Memo[]>(res);
}
