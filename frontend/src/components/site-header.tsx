import Link from "next/link";
import { SHOP_NAME } from "@/lib/config";

export function SiteHeader() {
  return (
    <header className="border-b">
      <div className="mx-auto flex h-16 w-full max-w-7xl items-center px-4 sm:px-6 lg:px-8">
        <Link
          href="/products"
          className="font-display text-2xl font-bold tracking-tight text-primary"
        >
          {SHOP_NAME}
        </Link>
      </div>
    </header>
  );
}
