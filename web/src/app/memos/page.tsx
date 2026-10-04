import { connection } from "next/server";
import { listMemos } from "@/lib/api/memos";
import type { Memo } from "@/lib/api/types";

export const metadata = { title: "메모 목록" };

export default async function MemosPage() {
  // 빌드 시점이 아니라 요청마다 backend를 조회한다 (빌드할 때 backend가 떠 있지 않아도 된다).
  await connection();

  let memos: Memo[];
  try {
    memos = await listMemos();
  } catch (error) {
    console.error("listMemos failed", error);
    return (
      <main>
        <h1>메모</h1>
        <p role="alert">
          backend에 연결할 수 없습니다. <code>backend</code>에서{" "}
          <code>./gradlew bootRun</code>을 실행했는지 확인하세요.
        </p>
      </main>
    );
  }

  return (
    <main>
      <h1>메모</h1>
      {memos.length === 0 ? (
        <p>메모가 없습니다.</p>
      ) : (
        <ul>
          {memos.map((memo) => (
            <li key={memo.id}>
              <strong>{memo.title}</strong>
              {memo.content && <p>{memo.content}</p>}
              <time dateTime={memo.createdAt}>
                {new Date(memo.createdAt).toLocaleString("ko-KR")}
              </time>
            </li>
          ))}
        </ul>
      )}
    </main>
  );
}
