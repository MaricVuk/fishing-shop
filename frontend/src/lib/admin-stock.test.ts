import { describe, expect, it } from "vitest";
import {
  formatDateTime,
  movementLabel,
  parseStockQuery,
  stockHref,
  validateCount,
  validateReceipt,
} from "@/lib/admin-stock";

describe("parseStockQuery", () => {
  it("reads the search text and a 1-based page", () => {
    expect(parseStockQuery({ q: "  shimano ", page: "3" })).toEqual({ q: "shimano", page: 3 });
  });

  it("falls back for missing or broken values", () => {
    expect(parseStockQuery({})).toEqual({ q: "", page: 1 });
    expect(parseStockQuery({ page: "-2" })).toEqual({ q: "", page: 1 });
    expect(parseStockQuery({ page: "abc" })).toEqual({ q: "", page: 1 });
    expect(parseStockQuery({ page: "99999" })).toEqual({ q: "", page: 10000 });
    expect(parseStockQuery({ q: ["a", "b"] }).q).toBe("a");
    expect(parseStockQuery({ q: "x".repeat(300) }).q).toHaveLength(100);
  });
});

describe("stockHref", () => {
  it("keeps only what is set", () => {
    expect(stockHref({ q: "", page: 1 })).toBe("/admin/stock");
    expect(stockHref({ q: "rod", page: 1 }, { page: 2 })).toBe("/admin/stock?q=rod&page=2");
  });
});

describe("movementLabel", () => {
  it("names the movement types", () => {
    expect(movementLabel("IN")).toBe("Prijem");
    expect(movementLabel("OUT")).toBe("Porudžbina");
    expect(movementLabel("ADJUSTMENT")).toBe("Popis");
  });
});

describe("formatDateTime", () => {
  it("shows the local date and time", () => {
    expect(formatDateTime("2026-11-06T13:05:00Z")).toBe("6. 11. 2026. 14:05");
  });
});

describe("validateReceipt", () => {
  it("accepts a whole positive quantity", () => {
    expect(validateReceipt({ quantity: "12", reason: "" })).toEqual({ ok: true, quantity: 12, reason: null });
  });

  it("rejects anything else", () => {
    for (const quantity of ["", "0", "-1", "1.5", "abc", "100001"]) {
      expect(validateReceipt({ quantity, reason: "" })).toEqual({
        ok: false,
        errors: { quantity: "Unesite ceo broj od 1 do 100.000." },
      });
    }
    expect(validateReceipt({ quantity: "1", reason: "a".repeat(201) })).toEqual({
      ok: false,
      errors: { reason: "Napomena može imati najviše 200 znakova." },
    });
  });
});

describe("validateCount", () => {
  it("accepts zero and needs a reason", () => {
    expect(validateCount({ quantity: "0", reason: " Popis " })).toEqual({
      ok: true,
      quantity: 0,
      reason: "Popis",
    });
    expect(validateCount({ quantity: "3", reason: "  " })).toEqual({
      ok: false,
      errors: { reason: "Upišite razlog popisa." },
    });
    expect(validateCount({ quantity: "-1", reason: "x" })).toEqual({
      ok: false,
      errors: { quantity: "Unesite ceo broj od 0 do 1.000.000." },
    });
  });
});
