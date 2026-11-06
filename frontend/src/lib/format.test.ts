import { describe, expect, it } from "vitest";
import {
  attributeLabel,
  describeAttributes,
  formatPrice,
  productCountLabel,
} from "@/lib/format";

describe("attributeLabel", () => {
  it("translates known attribute keys", () => {
    expect(attributeLabel("length")).toBe("Dužina");
    expect(attributeLabel("color")).toBe("Boja");
  });

  it("capitalizes unknown keys", () => {
    expect(attributeLabel("weight")).toBe("Weight");
  });
});

describe("describeAttributes", () => {
  it("joins labeled attributes", () => {
    expect(describeAttributes({ length: "2.7m", action: "brza" })).toBe(
      "Dužina: 2.7m · Akcija: brza",
    );
  });

  it("names a variant without attributes", () => {
    expect(describeAttributes({})).toBe("Osnovna varijanta");
  });
});

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
