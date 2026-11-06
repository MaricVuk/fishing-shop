import type { Metadata } from "next";
import { ChangePasswordForm } from "@/components/admin/change-password-form";
import { requireEmployee } from "@/lib/admin-access";
import { roleLabel } from "@/lib/admin-nav";

export const metadata: Metadata = { title: "Moj nalog" };

export default async function AccountPage() {
  const employee = await requireEmployee();

  return (
    <>
      <h1 className="text-3xl font-bold">Moj nalog</h1>
      <p className="mt-1 text-muted-foreground">
        {employee.fullName} · {employee.email} · {roleLabel(employee.role)}
      </p>
      <div className="mt-8 max-w-md">
        <ChangePasswordForm />
      </div>
    </>
  );
}
