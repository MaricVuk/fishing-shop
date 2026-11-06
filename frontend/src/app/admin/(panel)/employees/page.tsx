import type { Metadata } from "next";
import { ComingSoon } from "@/components/admin/coming-soon";
import { Forbidden } from "@/components/admin/forbidden";
import { accessTo } from "@/lib/admin-access";

export const metadata: Metadata = { title: "Zaposleni" };

export default async function Page() {
  const { allowed } = await accessTo("/admin/employees");
  return allowed ? <ComingSoon title="Zaposleni" /> : <Forbidden />;
}
