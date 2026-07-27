import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "EquipOps · 智能设备运维平台",
  description: "设备台账、维修工单与 RepairMind AI 辅助诊断的一体化运维工作台。",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN">
      <body>{children}</body>
    </html>
  );
}
