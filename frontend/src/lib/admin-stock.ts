import type { StockMovementType } from "@/lib/admin-types";

type RawParams = Record<string, string | string[] | undefined>;

export type StockQuery = { q: string; page: number };

export type StockFormValues = { quantity: string; reason: string };
export type StockFormErrors = Partial<Record<keyof StockFormValues, string>>;
export type StockFormResult =
  | { ok: true; quantity: number; reason: string | null }
  | { ok: false; errors: StockFormErrors };

const MAX_SEARCH = 100;
const MAX_PAGE = 10_000;
const MAX_REASON = 200;
const MAX_RECEIPT = 100_000;
const MAX_COUNT = 1_000_000;
const WHOLE_NUMBER = /^\d+$/;

const MOVEMENT_LABELS: Record<StockMovementType, string> = {
  IN: "Prijem",
  OUT: "Porudžbina",
  ADJUSTMENT: "Popis",
};

const dateTime = new Intl.DateTimeFormat("sr-Latn-RS", {
  timeZone: "Europe/Belgrade",
  day: "numeric",
  month: "numeric",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

const number = new Intl.NumberFormat("sr-Latn-RS");

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export function parseStockQuery(raw: RawParams): StockQuery {
  const page = Number.parseInt(first(raw.page) ?? "1", 10);
  return {
    q: (first(raw.q) ?? "").trim().slice(0, MAX_SEARCH),
    page: Number.isInteger(page) && page > 1 ? Math.min(page, MAX_PAGE) : 1,
  };
}

export function stockHref(query: StockQuery, patch: Partial<StockQuery> = {}): string {
  const next = { ...query, page: 1, ...patch };
  const params = new URLSearchParams();
  if (next.q) params.set("q", next.q);
  if (next.page > 1) params.set("page", String(next.page));
  const search = params.toString();
  return search ? `/admin/stock?${search}` : "/admin/stock";
}

export function movementLabel(type: StockMovementType): string {
  return MOVEMENT_LABELS[type];
}

export function formatDateTime(iso: string): string {
  return dateTime.format(new Date(iso));
}

function wholeNumber(value: string, min: number, max: number): number | null {
  const trimmed = value.trim();
  if (!WHOLE_NUMBER.test(trimmed)) return null;
  const parsed = Number(trimmed);
  return parsed >= min && parsed <= max ? parsed : null;
}

function rangeMessage(min: number, max: number): string {
  return `Unesite ceo broj od ${number.format(min)} do ${number.format(max)}.`;
}

export function validateReceipt(values: StockFormValues): StockFormResult {
  const errors: StockFormErrors = {};
  const quantity = wholeNumber(values.quantity, 1, MAX_RECEIPT);
  const reason = values.reason.trim();
  if (quantity === null) errors.quantity = rangeMessage(1, MAX_RECEIPT);
  if (reason.length > MAX_REASON) errors.reason = `Napomena može imati najviše ${MAX_REASON} znakova.`;
  if (quantity === null || errors.reason) return { ok: false, errors };
  return { ok: true, quantity, reason: reason === "" ? null : reason };
}

export function validateCount(values: StockFormValues): StockFormResult {
  const errors: StockFormErrors = {};
  const quantity = wholeNumber(values.quantity, 0, MAX_COUNT);
  const reason = values.reason.trim();
  if (quantity === null) errors.quantity = rangeMessage(0, MAX_COUNT);
  if (reason === "") errors.reason = "Upišite razlog popisa.";
  else if (reason.length > MAX_REASON) errors.reason = `Razlog može imati najviše ${MAX_REASON} znakova.`;
  if (quantity === null || errors.reason) return { ok: false, errors };
  return { ok: true, quantity, reason };
}
