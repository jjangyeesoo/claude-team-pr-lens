import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "starter web",
  description: "Claude Code 팀 스타터 샘플 FE",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
