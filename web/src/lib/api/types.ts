// backend의 요청/응답 DTO와 1:1로 맞춘다. backend DTO를 바꾸면 이 파일도 같은 PR에서 바꾼다.

/** backend: memo/api/MemoResponse */
export type Memo = {
  id: number;
  title: string;
  content: string | null;
  /** ISO-8601 (java.time.Instant) */
  createdAt: string;
};

/** backend: common/error/ErrorResponse. 모든 API 에러는 이 형식이다 (ADR-0002). */
export type ErrorResponse = {
  code: string;
  message: string;
  details: string[];
};
