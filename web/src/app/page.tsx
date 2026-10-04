import Link from "next/link";

export default function Home() {
  return (
    <main>
      <h1>starter web</h1>
      <p>
        backend(<code>/api/v1/memos</code>)를 조회하는 샘플입니다.
      </p>
      <Link href="/memos">메모 목록</Link>
    </main>
  );
}
