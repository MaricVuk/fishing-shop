import { formatPrice } from "@/lib/format";
import type { ReportGranularity } from "@/lib/admin-types";

type RawParams = Record<string, string | string[] | undefined>;

export type ReportQuery = { from: string | null; to: string | null; granularity: ReportGranularity };
export type ReportPreset = { label: string; query: ReportQuery };

export const GRANULARITIES: ReportGranularity[] = ["DAY", "WEEK", "MONTH"];

const GRANULARITY_LABELS: Record<ReportGranularity, string> = {
  DAY: "Po danima",
  WEEK: "Po nedeljama",
  MONTH: "Po mesecima",
};

export const BEST_SELLERS_LIMIT = 10;

const MONTHS = ["jan", "feb", "mar", "apr", "maj", "jun", "jul", "avg", "sep", "okt", "nov", "dec"];
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const SHOP_DAY = new Intl.DateTimeFormat("en-CA", {
  timeZone: "Europe/Belgrade",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function parts(iso: string): [number, number, number] {
  const [year, month, day] = iso.split("-").map(Number);
  return [year, month, day];
}

function isIsoDate(value: string | undefined): value is string {
  if (!value || !ISO_DATE.test(value)) return false;
  const [year, month, day] = parts(value);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

function isGranularity(value: unknown): value is ReportGranularity {
  return typeof value === "string" && (GRANULARITIES as string[]).includes(value);
}

export function parseReportQuery(raw: RawParams): ReportQuery {
  const from = first(raw.from);
  const to = first(raw.to);
  const granularity = first(raw.granularity);
  return {
    from: isIsoDate(from) ? from : null,
    to: isIsoDate(to) ? to : null,
    granularity: isGranularity(granularity) ? granularity : "DAY",
  };
}

export function salesApiPath(query: ReportQuery): string {
  const params = new URLSearchParams({ granularity: query.granularity });
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  return `/api/admin/reports/sales?${params}`;
}

export function bestSellersApiPath(query: ReportQuery): string {
  const params = new URLSearchParams({ limit: String(BEST_SELLERS_LIMIT) });
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  return `/api/admin/reports/best-sellers?${params}`;
}

export function stockLevelLabel(quantity: number): string {
  return quantity === 0 ? "Nema na stanju" : `${formatCount(quantity)} kom.`;
}

export function reportsHref(query: ReportQuery): string {
  const params = new URLSearchParams();
  if (query.from) params.set("from", query.from);
  if (query.to) params.set("to", query.to);
  if (query.granularity !== "DAY") params.set("granularity", query.granularity);
  const search = params.toString();
  return search ? `/admin/reports?${search}` : "/admin/reports";
}

export function shopToday(now: Date): string {
  return SHOP_DAY.format(now);
}

function shift(iso: string, { days = 0, months = 0 }: { days?: number; months?: number }): string {
  const [year, month, day] = parts(iso);
  if (months !== 0) {
    const target = new Date(Date.UTC(year, month - 1 + months, 1));
    const lastDay = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
    target.setUTCDate(Math.min(day, lastDay));
    return target.toISOString().slice(0, 10);
  }
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

function mondayOf(iso: string): string {
  const [year, month, day] = parts(iso);
  const weekday = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return shift(iso, { days: -((weekday + 6) % 7) });
}

export function reportPresets(today: string): ReportPreset[] {
  return [
    { label: "30 dana", query: { from: shift(today, { days: -29 }), to: today, granularity: "DAY" } },
    { label: "26 nedelja", query: { from: mondayOf(shift(today, { days: -175 })), to: today, granularity: "WEEK" } },
    { label: "12 meseci", query: { from: shift(today, { months: -11 }).slice(0, 8) + "01", to: today, granularity: "MONTH" } },
    { label: "5 godina", query: { from: shift(today, { months: -59 }).slice(0, 8) + "01", to: today, granularity: "MONTH" } },
  ];
}

export function granularityLabel(granularity: ReportGranularity): string {
  return GRANULARITY_LABELS[granularity];
}

export function formatShopDate(iso: string): string {
  const [year, month, day] = parts(iso);
  return `${day}. ${month}. ${year}.`;
}

export function bucketLabel(start: string, granularity: ReportGranularity): string {
  const [year, month, day] = parts(start);
  if (granularity === "MONTH") return `${MONTHS[month - 1]} ${year}.`;
  if (granularity === "WEEK") return `od ${day}. ${month}.`;
  return `${day}. ${month}.`;
}

export function bucketTitle(start: string, granularity: ReportGranularity): string {
  if (granularity === "MONTH") return bucketLabel(start, granularity);
  if (granularity === "WEEK") return `Nedelja od ${formatShopDate(start)}`;
  return formatShopDate(start);
}

export function formatAmount(amount: string): string {
  return formatPrice(Number(amount));
}

const COUNT = new Intl.NumberFormat("sr-Latn-RS");

export function formatCount(count: number): string {
  return COUNT.format(count);
}

export function chartValue(amount: string): number {
  return Number(amount);
}
