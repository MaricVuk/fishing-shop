import type { Metadata } from "next";
import Link from "next/link";
import { notFound } from "next/navigation";
import { EmployeeAccessForm, PasswordResetForm, RoleForm } from "@/components/admin/employee-forms";
import { Forbidden } from "@/components/admin/forbidden";
import { Badge } from "@/components/ui/badge";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import { roleLabel } from "@/lib/admin-nav";
import { formatDateTime } from "@/lib/admin-stock";
import { describeAudit } from "@/lib/admin-team";
import type { AdminEmployee, AdminPage, AuditEntry } from "@/lib/admin-types";

export const metadata: Metadata = { title: "Zaposleni" };

export default async function EmployeePage({ params }: PageProps<"/admin/employees/[employeeId]">) {
  const { allowed, employee: me } = await accessTo("/admin/employees");
  if (!allowed) return <Forbidden />;

  const { employeeId } = await params;
  if (!/^[1-9]\d{0,17}$/.test(employeeId)) notFound();
  const [employees, history] = await Promise.all([
    adminGet<AdminEmployee[]>("/api/admin/employees"),
    adminGet<AdminPage<AuditEntry>>(`/api/admin/audit?entityType=EMPLOYEE&entityId=${employeeId}&size=50`),
  ]);
  const employee = employees?.find((candidate) => String(candidate.id) === employeeId);
  if (!employee) notFound();
  const isMe = employee.id === me.id;

  return (
    <>
      <Link href="/admin/employees" className="text-sm text-primary underline-offset-4 hover:underline">
        ← Zaposleni
      </Link>
      <div className="mt-3 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold">{employee.fullName}</h1>
        {employee.active ? <Badge variant="secondary">Aktivan</Badge> : <Badge variant="outline">Isključen</Badge>}
      </div>
      <p className="mt-1 text-muted-foreground">
        {employee.email} · {roleLabel(employee.role)} · nalog od {formatDateTime(employee.createdAt)}
      </p>

      {isMe ? (
        <p className="mt-8 rounded-xl bg-muted px-6 py-6 text-muted-foreground">
          Ovo je vaš nalog. Svoju ulogu i pristup ne možete da menjate, a lozinku menjate na stranici{" "}
          <Link href="/admin/account" className="text-primary underline underline-offset-4">
            Moj nalog
          </Link>
          .
        </p>
      ) : (
        <div className="mt-8 grid gap-6 md:grid-cols-3">
          <RoleForm employeeId={employee.id} role={employee.role} />
          <EmployeeAccessForm employeeId={employee.id} active={employee.active} />
          <PasswordResetForm employeeId={employee.id} />
        </div>
      )}

      <h2 className="mt-10 text-xl font-semibold">Istorija naloga</h2>
      {(history?.content ?? []).length === 0 ? (
        <p className="mt-3 text-muted-foreground">Nalog još nije menjan.</p>
      ) : (
        <ol className="mt-3 flex flex-col gap-3">
          {history!.content.map((entry) => (
            <li key={entry.id} className="rounded-xl border px-4 py-3 text-sm">
              <p>{describeAudit(entry)}</p>
              <p className="mt-1 text-muted-foreground">
                {formatDateTime(entry.createdAt)} · {entry.actorEmail}
              </p>
            </li>
          ))}
        </ol>
      )}
    </>
  );
}
