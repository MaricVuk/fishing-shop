import { formatDateTime } from "@/lib/admin-stock";
import type { AuditEntry, InvoiceSummary, OrderStatus } from "@/lib/admin-types";

type RawParams = Record<string, string | string[] | undefined>;

export type OrderQuery = { status: OrderStatus | null; q: string; page: number };

export const ORDER_STATUSES: OrderStatus[] = ["NEW", "CONFIRMED", "SHIPPED", "DELIVERED", "CANCELLED"];

const STATUS_LABELS: Record<OrderStatus, string> = {
  NEW: "Nova",
  CONFIRMED: "Potvrđena",
  SHIPPED: "Poslata",
  DELIVERED: "Isporučena",
  CANCELLED: "Otkazana",
};

const ACTION_LABELS: Record<OrderStatus, string> = {
  NEW: "Vrati u nove",
  CONFIRMED: "Potvrdi",
  SHIPPED: "Označi kao poslatu",
  DELIVERED: "Označi kao isporučenu",
  CANCELLED: "Otkaži porudžbinu",
};

const MAX_SEARCH = 100;
const MAX_PAGE = 10_000;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function isStatus(value: unknown): value is OrderStatus {
  return typeof value === "string" && (ORDER_STATUSES as string[]).includes(value);
}

export function parseOrderQuery(raw: RawParams): OrderQuery {
  const status = first(raw.status);
  const page = Number.parseInt(first(raw.page) ?? "1", 10);
  return {
    status: isStatus(status) ? status : null,
    q: (first(raw.q) ?? "").trim().slice(0, MAX_SEARCH),
    page: Number.isInteger(page) && page > 1 ? Math.min(page, MAX_PAGE) : 1,
  };
}

export function ordersHref(query: OrderQuery, patch: Partial<OrderQuery> = {}): string {
  const next = { ...query, page: 1, ...patch };
  const params = new URLSearchParams();
  if (next.status) params.set("status", next.status);
  if (next.q) params.set("q", next.q);
  if (next.page > 1) params.set("page", String(next.page));
  const search = params.toString();
  return search ? `/admin/orders?${search}` : "/admin/orders";
}

export function statusLabel(status: OrderStatus): string {
  return STATUS_LABELS[status];
}

export function statusActionLabel(status: OrderStatus): string {
  return ACTION_LABELS[status];
}

export function describeChange(entry: AuditEntry): string {
  const from = entry.oldValue?.status;
  const to = entry.newValue?.status;
  const parts = [
    isStatus(from) && isStatus(to) ? `${statusLabel(from)} → ${statusLabel(to)}.` : "Promena statusa.",
  ];
  const reason = entry.newValue?.reason;
  if (typeof reason === "string" && reason !== "") parts.push(`Razlog: ${reason}.`);
  const returned = entry.newValue?.returned;
  if (Array.isArray(returned) && returned.length > 0) {
    const units = returned.reduce(
      (sum: number, line: unknown) =>
        sum + (typeof line === "object" && line !== null && typeof (line as { quantity?: unknown }).quantity === "number"
          ? (line as { quantity: number }).quantity
          : 0),
      0,
    );
    parts.push(`Vraćeno na stanje: ${units} kom.`);
  }
  return parts.join(" ");
}

export type InvoiceView = { title: string; text: string; href: string | null };

export function describeInvoice(orderId: number, invoice: InvoiceSummary): InvoiceView | null {
  switch (invoice.status) {
    case "ISSUED":
      return {
        title: invoice.number ?? "Račun",
        text: invoice.issuedAt ? `Izdat ${formatDateTime(invoice.issuedAt)}` : "Izdat",
        href: `/api/admin/orders/${orderId}/invoice`,
      };
    case "PENDING":
      return { title: "Račun se pravi", text: "Pojaviće se ovde za nekoliko sekundi. Osveži stranicu.", href: null };
    case "FAILED":
      return {
        title: "Račun nije napravljen",
        text: "Svi pokušaji su propali. Javi administratoru.",
        href: null,
      };
    case "NONE":
      return null;
  }
}

export function describeCreditNote(orderId: number, creditNote: InvoiceSummary): InvoiceView | null {
  switch (creditNote.status) {
    case "ISSUED":
      return {
        title: creditNote.number ?? "Storno",
        text: creditNote.issuedAt ? `Storno izdat ${formatDateTime(creditNote.issuedAt)}` : "Storno izdat",
        href: `/api/admin/orders/${orderId}/credit-note`,
      };
    case "PENDING":
      return { title: "Storno se pravi", text: "Porudžbina je otkazana posle računa. Osveži stranicu.", href: null };
    case "FAILED":
      return {
        title: "Storno nije napravljen",
        text: "Svi pokušaji su propali. Javi administratoru.",
        href: null,
      };
    case "NONE":
      return null;
  }
}

