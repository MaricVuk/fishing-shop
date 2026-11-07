import type { EmployeeRole } from "@/lib/admin-types";

export type AdminNavItem = { href: string; label: string };

const SECTIONS: (AdminNavItem & { roles: EmployeeRole[] })[] = [
  { href: "/admin", label: "Pregled", roles: ["OWNER", "SELLER", "WAREHOUSE"] },
  { href: "/admin/orders", label: "Porudžbine", roles: ["OWNER", "SELLER"] },
  { href: "/admin/stock", label: "Magacin", roles: ["OWNER", "WAREHOUSE"] },
  { href: "/admin/reports", label: "Izveštaji", roles: ["OWNER", "WAREHOUSE"] },
  { href: "/admin/employees", label: "Zaposleni", roles: ["OWNER"] },
  { href: "/admin/jobs", label: "Poslovi", roles: ["OWNER"] },
  { href: "/admin/audit", label: "Audit log", roles: ["OWNER"] },
  { href: "/admin/account", label: "Moj nalog", roles: ["OWNER", "SELLER", "WAREHOUSE"] },
];

const ROLE_LABELS: Record<EmployeeRole, string> = {
  OWNER: "Vlasnik",
  SELLER: "Prodavac",
  WAREHOUSE: "Magacioner",
};

export function adminNavigation(role: EmployeeRole): AdminNavItem[] {
  return SECTIONS.filter((section) => section.roles.includes(role)).map(({ href, label }) => ({
    href,
    label,
  }));
}

export function canAccess(role: EmployeeRole, path: string): boolean {
  const section = [...SECTIONS]
    .sort((a, b) => b.href.length - a.href.length)
    .find((candidate) => path === candidate.href || path.startsWith(`${candidate.href}/`));
  return section !== undefined && section.roles.includes(role);
}

export function roleLabel(role: EmployeeRole): string {
  return ROLE_LABELS[role];
}
