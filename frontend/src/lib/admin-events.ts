import { formatAmount } from "@/lib/admin-reports";
import { describeAttributes } from "@/lib/format";

export const ADMIN_EVENT_NAMES = ["order-placed", "variant-sold-out"] as const;
export type AdminEventName = (typeof ADMIN_EVENT_NAMES)[number];

export type AdminNotice = { key: string; title: string; detail: string; href: string; linkLabel: string };

export const MAX_NOTICES = 4;

type Json = Record<string, unknown>;

function parse(data: string): Json | null {
  try {
    const value: unknown = JSON.parse(data);
    return value !== null && typeof value === "object" && !Array.isArray(value) ? (value as Json) : null;
  } catch {
    return null;
  }
}

function isId(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

function itemsLabel(count: number): string {
  const lastDigit = count % 10;
  const lastTwo = count % 100;
  if (lastDigit === 1 && lastTwo !== 11) return `${count} stavka`;
  if (lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14)) return `${count} stavke`;
  return `${count} stavki`;
}

export function describeAdminEvent(name: string, data: string): AdminNotice | null {
  const event = parse(data);
  if (!event) return null;

  if (name === "order-placed") {
    const { orderId, total, items } = event;
    if (!isId(orderId) || typeof total !== "string" || typeof items !== "number") return null;
    return {
      key: `order-${orderId}`,
      title: `Nova porudžbina #${orderId}`,
      detail: `${itemsLabel(items)}, ukupno ${formatAmount(total)}`,
      href: `/admin/orders/${orderId}`,
      linkLabel: "Otvori porudžbinu",
    };
  }

  if (name === "variant-sold-out") {
    const { movementId, variantId, productName, sku, attributes } = event;
    if (!isId(movementId) || !isId(variantId) || typeof productName !== "string" || typeof sku !== "string") {
      return null;
    }
    const variant =
      attributes && typeof attributes === "object" && !Array.isArray(attributes)
        ? describeAttributes(attributes as Record<string, string>)
        : "";
    return {
      key: `sold-out-${movementId}`,
      title: "Prodat poslednji komad",
      detail: [productName, variant, sku].filter(Boolean).join(" · "),
      href: `/admin/stock/${variantId}`,
      linkLabel: "Otvori u magacinu",
    };
  }

  return null;
}

export function addNotice(notices: AdminNotice[], notice: AdminNotice): AdminNotice[] {
  if (notices.some((existing) => existing.key === notice.key)) return notices;
  return [notice, ...notices].slice(0, MAX_NOTICES);
}

export const REFRESH_DELAY_MS = 600;

export function refreshesOn(pathname: string): boolean {
  return pathname === "/admin";
}

const STREAM_PATH = "/api/admin/events";
const FIRST_RETRY_MS = 2_000;
const LONGEST_RETRY_MS = 60_000;

export function streamUrl(lastEventId: string | null): string {
  return lastEventId ? `${STREAM_PATH}?lastEventId=${encodeURIComponent(lastEventId)}` : STREAM_PATH;
}

export function reconnectDelay(attempt: number): number {
  return Math.min(FIRST_RETRY_MS * 2 ** Math.max(0, attempt), LONGEST_RETRY_MS);
}
