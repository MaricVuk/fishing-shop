import Link from "next/link";
import { AdminNav } from "@/components/admin/admin-nav";
import { LogoutButton } from "@/components/admin/logout-button";
import { adminNavigation, roleLabel } from "@/lib/admin-nav";
import type { Employee } from "@/lib/admin-types";
import { SHOP_NAME } from "@/lib/config";

export function AdminHeader({ employee }: { employee: Employee }) {
  return (
    <header className="border-b">
      <div className="mx-auto flex w-full max-w-7xl flex-col gap-3 px-4 py-3 sm:px-6 lg:px-8">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <Link href="/admin" className="font-display text-xl font-bold tracking-tight text-primary">
            {SHOP_NAME} <span className="text-muted-foreground">admin</span>
          </Link>
          <div className="flex items-center gap-4">
            <p className="text-sm">
              <span className="font-medium">{employee.fullName}</span>{" "}
              <span className="text-muted-foreground">· {roleLabel(employee.role)}</span>
            </p>
            <LogoutButton />
          </div>
        </div>
        <AdminNav items={adminNavigation(employee.role)} />
      </div>
    </header>
  );
}
