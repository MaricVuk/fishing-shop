import type { Metadata } from "next";
import Link from "next/link";
import { NewEmployeeForm } from "@/components/admin/employee-forms";
import { Forbidden } from "@/components/admin/forbidden";
import { Badge } from "@/components/ui/badge";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import { roleLabel } from "@/lib/admin-nav";
import type { AdminEmployee } from "@/lib/admin-types";

export const metadata: Metadata = { title: "Zaposleni" };

export default async function EmployeesPage() {
  const { allowed, employee: me } = await accessTo("/admin/employees");
  if (!allowed) return <Forbidden />;

  const employees = (await adminGet<AdminEmployee[]>("/api/admin/employees")) ?? [];

  return (
    <>
      <h1 className="text-3xl font-bold">Zaposleni</h1>
      <p className="mt-1 text-sm text-muted-foreground">{employees.filter((e) => e.active).length} aktivnih naloga</p>

      <div className="mt-6 grid gap-8 lg:grid-cols-[1fr_22rem]">
        <div className="overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Ime</th>
                <th scope="col" className="px-4 py-3 font-medium">Uloga</th>
                <th scope="col" className="px-4 py-3 font-medium">Nalog</th>
              </tr>
            </thead>
            <tbody>
              {employees.map((employee) => (
                <tr key={employee.id} className="border-t">
                  <td className="px-4 py-3">
                    <Link
                      href={`/admin/employees/${employee.id}`}
                      className="font-medium text-primary underline-offset-4 hover:underline"
                    >
                      {employee.fullName}
                    </Link>
                    {employee.id === me.id && <span className="text-muted-foreground"> (vi)</span>}
                    <span className="block text-muted-foreground break-all">{employee.email}</span>
                  </td>
                  <td className="px-4 py-3">{roleLabel(employee.role)}</td>
                  <td className="px-4 py-3">
                    {employee.active ? (
                      <Badge variant="secondary">Aktivan</Badge>
                    ) : (
                      <Badge variant="outline">Isključen</Badge>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <NewEmployeeForm />
      </div>
    </>
  );
}
