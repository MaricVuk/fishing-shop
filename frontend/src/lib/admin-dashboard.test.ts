import { describe, expect, it } from "vitest";
import { todaysBucket, waitingHint, weekRevenue } from "@/lib/admin-dashboard";
import type { SalesReport } from "@/lib/admin-types";

const sales: SalesReport = {
  from: "2026-11-01",
  to: "2026-11-07",
  granularity: "DAY",
  totals: { revenue: "700.00", orders: 7, newRevenue: "0", newOrders: 0 },
  buckets: [
    { start: "2026-11-06", revenue: "100.00", orders: 1, newRevenue: "0", newOrders: 0 },
    { start: "2026-11-07", revenue: "250.50", orders: 2, newRevenue: "0", newOrders: 0 },
  ],
};

describe("todaysBucket", () => {
  it("is the last day of the report", () => {
    expect(todaysBucket(sales)?.start).toBe("2026-11-07");
  });

  it("is null for a report without days", () => {
    expect(todaysBucket({ ...sales, buckets: [] })).toBeNull();
  });
});

describe("weekRevenue", () => {
  it("passes the amount on as the backend wrote it", () => {
    expect(weekRevenue(sales)).toBe("700.00");
  });
});

describe("waitingHint", () => {
  const format = (iso: string) => `<${iso}>`;

  it("names the oldest waiting order", () => {
    expect(waitingHint({ count: 3, oldestSince: "2026-11-07T08:00:00Z" }, format)).toBe(
      "Najstarija od <2026-11-07T08:00:00Z>",
    );
  });

  it("says nothing when no order is waiting", () => {
    expect(waitingHint({ count: 0 }, format)).toBeUndefined();
    expect(waitingHint({ count: 2 }, format)).toBeUndefined();
  });
});
