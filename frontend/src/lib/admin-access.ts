import { redirect } from "next/navigation";
import { canAccess } from "@/lib/admin-nav";
import { getCurrentEmployee } from "@/lib/admin-session";
import type { Employee } from "@/lib/admin-types";

export async function requireEmployee(): Promise<Employee> {
  const employee = await getCurrentEmployee();
  if (!employee) redirect("/admin/login");
  return employee;
}

export async function accessTo(path: string): Promise<{ employee: Employee; allowed: boolean }> {
  const employee = await requireEmployee();
  return { employee, allowed: canAccess(employee.role, path) };
}
