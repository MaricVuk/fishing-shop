import type { Dashboard } from "@/lib/admin-types";

export type DashboardOrders = NonNullable<Dashboard["pendingOrders"]>;

export function todaysBucket(sales: NonNullable<Dashboard["sales"]>) {
  return sales.buckets[sales.buckets.length - 1] ?? null;
}

export function weekRevenue(sales: NonNullable<Dashboard["sales"]>): string {
  return sales.totals.revenue;
}

export function waitingHint(pending: DashboardOrders, formatDate: (iso: string) => string): string | undefined {
  if (pending.count === 0 || !pending.oldestSince) return undefined;
  return `Najstarija od ${formatDate(pending.oldestSince)}`;
}
