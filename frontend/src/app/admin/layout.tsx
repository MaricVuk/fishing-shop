import type { Metadata } from "next";
import { SkipLink } from "@/components/skip-link";
import { SHOP_NAME } from "@/lib/config";

export const metadata: Metadata = {
  title: { default: `Admin | ${SHOP_NAME}`, template: `%s | Admin | ${SHOP_NAME}` },
  robots: { index: false, follow: false },
};

export default function AdminLayout({ children }: LayoutProps<"/admin">) {
  return (
    <>
      <SkipLink />
      {children}
    </>
  );
}
