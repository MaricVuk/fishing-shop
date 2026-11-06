import { roleLabel } from "@/lib/admin-nav";
import { describeChange } from "@/lib/admin-orders";
import type { AuditEntry, EmployeeRole } from "@/lib/admin-types";

type RawParams = Record<string, string | string[] | undefined>;

export type AuditEntityType = "VARIANT" | "ORDER" | "EMPLOYEE";
export type AuditQuery = { type: AuditEntityType | null; page: number };

export const ROLES: EmployeeRole[] = ["OWNER", "SELLER", "WAREHOUSE"];
export const AUDIT_TYPES: AuditEntityType[] = ["ORDER", "VARIANT", "EMPLOYEE"];

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const ENTITY_LABELS: Record<AuditEntityType, string> = {
  ORDER: "Porudžbina",
  VARIANT: "Varijanta",
  EMPLOYEE: "Zaposleni",
};
const TYPE_FILTER_LABELS: Record<AuditEntityType, string> = {
  ORDER: "Porudžbine",
  VARIANT: "Magacin",
  EMPLOYEE: "Zaposleni",
};

export type NewEmployeeValues = { email: string; fullName: string; role: string; password: string };
export type NewEmployeeErrors = Partial<Record<keyof NewEmployeeValues, string>>;
export type NewEmployeeResult =
  | { ok: true; value: { email: string; fullName: string; role: EmployeeRole; password: string } }
  | { ok: false; errors: NewEmployeeErrors };

function isRole(value: string): value is EmployeeRole {
  return (ROLES as string[]).includes(value);
}

function isAuditType(value: unknown): value is AuditEntityType {
  return typeof value === "string" && (AUDIT_TYPES as string[]).includes(value);
}

export function validatePassword(password: string): string | null {
  if (password.length < 12) return "Lozinka mora imati najmanje 12 znakova.";
  if (password.length > 64) return "Lozinka može imati najviše 64 znaka.";
  if (new TextEncoder().encode(password).length > 72) {
    return "Lozinka je predugačka. Koristite manje slova sa kvačicama.";
  }
  return null;
}

export function validateNewEmployee(values: NewEmployeeValues): NewEmployeeResult {
  const errors: NewEmployeeErrors = {};
  const email = values.email.trim().toLowerCase();
  const fullName = values.fullName.trim();
  if (!EMAIL.test(email) || email.length > 100) errors.email = "Unesite ispravnu adresu e-pošte.";
  if (fullName === "") errors.fullName = "Unesite ime i prezime.";
  else if (fullName.length > 150) errors.fullName = "Ime može imati najviše 150 znakova.";
  if (!isRole(values.role)) errors.role = "Izaberite ulogu.";
  const password = validatePassword(values.password);
  if (password) errors.password = password;
  if (Object.keys(errors).length > 0 || !isRole(values.role)) return { ok: false, errors };
  return { ok: true, value: { email, fullName, role: values.role, password: values.password } };
}

export function auditEntityLabel(type: string, id: string): string {
  return `${isAuditType(type) ? ENTITY_LABELS[type] : type} #${id}`;
}

export function auditTypeFilterLabel(type: AuditEntityType): string {
  return TYPE_FILTER_LABELS[type];
}

function text(value: unknown): string | null {
  return typeof value === "string" && value !== "" ? value : null;
}

function role(value: unknown): string {
  return typeof value === "string" && isRole(value) ? roleLabel(value) : String(value);
}

export function describeAudit(entry: AuditEntry): string {
  const before = entry.oldValue ?? {};
  const after = entry.newValue ?? {};
  const reason = text(after.reason);
  const withReason = (sentence: string) => (reason ? `${sentence} ${reason}` : sentence);

  switch (entry.action) {
    case "STOCK_RECEIVED":
      return withReason(`Prijem +${after.received} (${before.quantity} → ${after.quantity}).`);
    case "STOCK_ADJUSTED":
      return withReason(`Popis ${before.quantity} → ${after.quantity}.`);
    case "ORDER_STATUS_CHANGED":
      return describeChange(entry);
    case "EMPLOYEE_CREATED":
      return `Dodat nalog ${after.email} (${role(after.role)}).`;
    case "EMPLOYEE_ROLE_CHANGED":
      return `Uloga: ${role(before.role)} → ${role(after.role)}.`;
    case "EMPLOYEE_DEACTIVATED":
      return "Nalog je isključen.";
    case "EMPLOYEE_ACTIVATED":
      return "Nalog je ponovo uključen.";
    case "EMPLOYEE_PASSWORD_RESET":
      return "Vlasnik je postavio novu lozinku.";
    case "EMPLOYEE_PASSWORD_CHANGED":
      return "Zaposleni je promenio lozinku.";
    default:
      return entry.action;
  }
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export function parseAuditQuery(raw: RawParams): AuditQuery {
  const type = first(raw.type);
  const page = Number.parseInt(first(raw.page) ?? "1", 10);
  return {
    type: isAuditType(type) ? type : null,
    page: Number.isInteger(page) && page > 1 ? Math.min(page, 10_000) : 1,
  };
}

export function auditHref(query: AuditQuery, patch: Partial<AuditQuery> = {}): string {
  const next = { ...query, page: 1, ...patch };
  const params = new URLSearchParams();
  if (next.type) params.set("type", next.type);
  if (next.page > 1) params.set("page", String(next.page));
  const search = params.toString();
  return search ? `/admin/audit?${search}` : "/admin/audit";
}
