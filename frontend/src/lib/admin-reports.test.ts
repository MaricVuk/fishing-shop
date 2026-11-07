import { describe, expect, it } from "vitest";
import {
  bestSellersApiPath,
  bucketLabel,
  bucketTitle,
  chartValue,
  formatAmount,
  formatCount,
  parseReportQuery,
  reportPresets,
  reportsHref,
  salesApiPath,
  shopToday,
  stockLevelLabel,
} from "@/lib/admin-reports";

describe("parseReportQuery", () => {
  it("keeps valid dates and granularity", () => {
    expect(parseReportQuery({ from: "2026-10-01", to: "2026-11-07", granularity: "WEEK" })).toEqual({
      from: "2026-10-01",
      to: "2026-11-07",
      granularity: "WEEK",
    });
  });

  it("drops dates that do not exist or are not ISO dates", () => {
    expect(parseReportQuery({ from: "2026-02-30", to: "7.11.2026" })).toEqual({
      from: null,
      to: null,
      granularity: "DAY",
    });
  });

  it("falls back to days for an unknown granularity, including prototype keys", () => {
    expect(parseReportQuery({ granularity: "YEAR" }).granularity).toBe("DAY");
    expect(parseReportQuery({ granularity: "toString" }).granularity).toBe("DAY");
  });

  it("uses the first value of a repeated parameter", () => {
    expect(parseReportQuery({ from: ["2026-01-01", "2026-02-01"] }).from).toBe("2026-01-01");
  });
});

describe("links", () => {
  it("asks the backend for the chosen period", () => {
    expect(salesApiPath({ from: "2026-10-01", to: "2026-11-07", granularity: "MONTH" })).toBe(
      "/api/admin/reports/sales?granularity=MONTH&from=2026-10-01&to=2026-11-07",
    );
    expect(salesApiPath({ from: null, to: null, granularity: "DAY" })).toBe(
      "/api/admin/reports/sales?granularity=DAY",
    );
  });

  it("keeps the page URL short for the default view", () => {
    expect(reportsHref({ from: null, to: null, granularity: "DAY" })).toBe("/admin/reports");
    expect(reportsHref({ from: "2026-01-01", to: "2026-01-31", granularity: "WEEK" })).toBe(
      "/admin/reports?from=2026-01-01&to=2026-01-31&granularity=WEEK",
    );
  });
});

describe("shopToday", () => {
  it("uses the Belgrade calendar", () => {
    expect(shopToday(new Date("2026-11-07T23:30:00Z"))).toBe("2026-11-08");
    expect(shopToday(new Date("2026-07-01T21:59:00Z"))).toBe("2026-07-01");
    expect(shopToday(new Date("2026-07-01T22:00:00Z"))).toBe("2026-07-02");
  });
});

describe("reportPresets", () => {
  it("ends every preset today and starts months on the first", () => {
    expect(reportPresets("2026-03-31").map((preset) => preset.query)).toEqual([
      { from: "2026-03-02", to: "2026-03-31", granularity: "DAY" },
      { from: "2025-10-06", to: "2026-03-31", granularity: "WEEK" },
      { from: "2025-04-01", to: "2026-03-31", granularity: "MONTH" },
      { from: "2021-04-01", to: "2026-03-31", granularity: "MONTH" },
    ]);
  });

  it("crosses a leap day", () => {
    expect(reportPresets("2028-03-29")[0].query.from).toBe("2028-02-29");
  });
});

describe("labels", () => {
  it("names buckets by granularity", () => {
    expect(bucketLabel("2026-11-03", "DAY")).toBe("3. 11.");
    expect(bucketLabel("2026-11-02", "WEEK")).toBe("od 2. 11.");
    expect(bucketLabel("2026-11-01", "MONTH")).toBe("nov 2026.");
    expect(bucketTitle("2026-11-02", "WEEK")).toBe("Nedelja od 2. 11. 2026.");
    expect(bucketTitle("2026-11-03", "DAY")).toBe("3. 11. 2026.");
  });
});

describe("amounts", () => {
  it("formats the backend string without losing para", () => {
    expect(formatAmount("30283220.00")).toBe(formatAmount("30283220"));
    expect(formatAmount("0.10")).toMatch(/0,10/);
    expect(chartValue("1234.50")).toBe(1234.5);
  });
});

describe("formatCount", () => {
  it("groups thousands the Serbian way", () => {
    expect(formatCount(212400)).toBe("212.400");
    expect(formatCount(285)).toBe("285");
  });
});

describe("bestSellersApiPath", () => {
  it("asks for the top ten in the same period as the sales report", () => {
    expect(bestSellersApiPath({ from: "2026-10-01", to: "2026-11-07", granularity: "WEEK" })).toBe(
      "/api/admin/reports/best-sellers?limit=10&from=2026-10-01&to=2026-11-07",
    );
    expect(bestSellersApiPath({ from: null, to: null, granularity: "DAY" })).toBe(
      "/api/admin/reports/best-sellers?limit=10",
    );
  });
});

describe("stockLevelLabel", () => {
  it("says when nothing is left", () => {
    expect(stockLevelLabel(0)).toBe("Nema na stanju");
    expect(stockLevelLabel(3)).toBe("3 kom.");
    expect(stockLevelLabel(1500)).toBe("1.500 kom.");
  });
});

describe("26-week preset", () => {
  it("starts on a Monday so the first week is whole, and covers 26 weeks", () => {
    for (const today of ["2026-03-31", "2026-11-07", "2026-11-09", "2026-11-15"]) {
      const from = reportPresets(today)[1].query.from as string;
      const [y, m, d] = from.split("-").map(Number);
      expect(new Date(Date.UTC(y, m - 1, d)).getUTCDay()).toBe(1);
      const weeks = (Date.parse(`${today}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / (7 * 86400000);
      expect(Math.floor(weeks) + 1).toBe(26);
    }
  });
});
