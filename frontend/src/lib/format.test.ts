import { describe, expect, it } from "vitest";
import {
  attributeLabel,
  describeAttributes,
  formatMinor,
  formatPrice,
  itemCountLabel,
  orderCountLabel,
  productCountLabel,
  variantCountLabel,
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

describe("formatMinor", () => {
  it("formats minor units as dinars", () => {
    expect(formatMinor(1390050)).toMatch(/^13\.900,50\sRSD$/);
  });
});

describe("itemCountLabel", () => {
  it.each([
    [1, "1 stavka"],
    [2, "2 stavke"],
    [4, "4 stavke"],
    [5, "5 stavki"],
    [11, "11 stavki"],
    [12, "12 stavki"],
    [21, "21 stavka"],
    [22, "22 stavke"],
  ])("labels %i as %s", (count, label) => {
    expect(itemCountLabel(count)).toBe(label);
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

describe("orderCountLabel and variantCountLabel", () => {
  it("use the right Serbian plural", () => {
    expect(orderCountLabel(1)).toBe("1 porudžbina");
    expect(orderCountLabel(2)).toBe("2 porudžbine");
    expect(orderCountLabel(5)).toBe("5 porudžbina");
    expect(orderCountLabel(11)).toBe("11 porudžbina");
    expect(orderCountLabel(21)).toBe("21 porudžbina");
    expect(orderCountLabel(0)).toBe("0 porudžbina");
    expect(variantCountLabel(1)).toBe("1 varijanta");
    expect(variantCountLabel(3)).toBe("3 varijante");
    expect(variantCountLabel(12)).toBe("12 varijanti");
  });
});
