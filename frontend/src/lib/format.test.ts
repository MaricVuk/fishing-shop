import { describe, expect, it } from "vitest";
import { formatPrice, productCountLabel } from "@/lib/format";

describe("formatPrice", () => {
  it("formats dinars with thousands separator and decimal comma", () => {
    expect(formatPrice(4500)).toMatch(/^4\.500,00\sRSD$/);
  });

  it("keeps two decimals", () => {
    expect(formatPrice(750.5)).toMatch(/^750,50\sRSD$/);
  });
});

describe("productCountLabel", () => {
  it.each([
    [0, "0 proizvoda"],
    [1, "1 proizvod"],
    [2, "2 proizvoda"],
    [4, "4 proizvoda"],
    [5, "5 proizvoda"],
    [11, "11 proizvoda"],
    [12, "12 proizvoda"],
    [20, "20 proizvoda"],
    [21, "21 proizvod"],
    [22, "22 proizvoda"],
    [101, "101 proizvod"],
  ])("labels %i as %s", (count, label) => {
    expect(productCountLabel(count)).toBe(label);
  });
});
