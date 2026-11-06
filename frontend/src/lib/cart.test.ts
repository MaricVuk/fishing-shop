import { describe, expect, it } from "vitest";
import {
  MAX_LINES,
  MAX_QUANTITY,
  addToCart,
  buildCartView,
  cartQuantity,
  parseStoredCart,
  removeLine,
  serializeCart,
  setQuantity,
  toMinor,
  type CartLine,
} from "@/lib/cart";
import type { VariantLine } from "@/lib/types";

function variant(id: number, price: number, inStock = true): VariantLine {
  return {
    variantId: id,
    productId: 1,
    productName: `Product ${id}`,
    brand: null,
    sku: `SKU-${id}`,
    attributes: {},
    price,
    imageUrl: null,
    inStock,
  };
}

describe("addToCart", () => {
  it("adds a new line", () => {
    expect(addToCart([], 5)).toEqual({ status: "added", lines: [{ variantId: 5, quantity: 1 }] });
  });

  it("increases the quantity of an existing line", () => {
    const lines: CartLine[] = [{ variantId: 5, quantity: 2 }];
    expect(addToCart(lines, 5)).toEqual({ status: "added", lines: [{ variantId: 5, quantity: 3 }] });
  });

  it("reports the quantity limit instead of silently doing nothing", () => {
    const lines: CartLine[] = [{ variantId: 5, quantity: MAX_QUANTITY }];
    expect(addToCart(lines, 5)).toEqual({ status: "quantity-limit" });
  });

  it("refuses a new line when the cart is full", () => {
    const full = Array.from({ length: MAX_LINES }, (_, index) => ({
      variantId: index + 1,
      quantity: 1,
    }));
    expect(addToCart(full, 999)).toEqual({ status: "cart-full" });
    expect(addToCart(full, 1).status).toBe("added");
  });

  it("does not mutate the original lines", () => {
    const lines: CartLine[] = [{ variantId: 5, quantity: 1 }];
    addToCart(lines, 5);
    expect(lines).toEqual([{ variantId: 5, quantity: 1 }]);
  });
});

describe("setQuantity and removeLine", () => {
  const lines: CartLine[] = [
    { variantId: 1, quantity: 2 },
    { variantId: 2, quantity: 1 },
  ];

  it("sets the quantity of one line only", () => {
    expect(setQuantity(lines, 1, 5)).toEqual([
      { variantId: 1, quantity: 5 },
      { variantId: 2, quantity: 1 },
    ]);
  });

  it.each([
    [0, 1],
    [-3, 1],
    [1000, MAX_QUANTITY],
    [2.9, 2],
  ])("clamps quantity %s to %s", (input, expected) => {
    expect(setQuantity(lines, 1, input)[0].quantity).toBe(expected);
  });

  it("removes a line", () => {
    expect(removeLine(lines, 1)).toEqual([{ variantId: 2, quantity: 1 }]);
  });

  it("counts the total quantity", () => {
    expect(cartQuantity(lines)).toBe(3);
  });
});

describe("parseStoredCart", () => {
  it("round-trips a serialized cart", () => {
    const lines: CartLine[] = [{ variantId: 3, quantity: 4 }];
    expect(parseStoredCart(serializeCart(lines))).toEqual(lines);
  });

  it.each([null, "", "not json", "{}", "42", '"text"'])(
    "returns an empty cart for %s",
    (raw) => {
      expect(parseStoredCart(raw)).toEqual([]);
    },
  );

  it("skips invalid entries", () => {
    const raw = JSON.stringify([
      { variantId: 1, quantity: 2 },
      { variantId: -1, quantity: 2 },
      { variantId: 2, quantity: 0 },
      { variantId: 3, quantity: 1.5 },
      { variantId: "4", quantity: 1 },
      null,
      "x",
    ]);
    expect(parseStoredCart(raw)).toEqual([{ variantId: 1, quantity: 2 }]);
  });

  it("merges duplicates and caps the quantity", () => {
    const raw = JSON.stringify([
      { variantId: 1, quantity: 60 },
      { variantId: 1, quantity: 60 },
    ]);
    expect(parseStoredCart(raw)).toEqual([{ variantId: 1, quantity: MAX_QUANTITY }]);
  });

  it("keeps at most the maximum number of lines", () => {
    const raw = JSON.stringify(
      Array.from({ length: MAX_LINES + 10 }, (_, index) => ({
        variantId: index + 1,
        quantity: 1,
      })),
    );
    expect(parseStoredCart(raw)).toHaveLength(MAX_LINES);
  });
});

describe("buildCartView", () => {
  it("totals in minor units without floating point drift", () => {
    expect(toMinor(0.1) * 3).toBe(30);
    const view = buildCartView(
      [{ variantId: 1, quantity: 3 }],
      [variant(1, 0.1)],
    );
    expect(view.totalMinor).toBe(30);
  });

  it("computes line totals and the overall total", () => {
    const view = buildCartView(
      [
        { variantId: 1, quantity: 2 },
        { variantId: 2, quantity: 1 },
      ],
      [variant(1, 4500), variant(2, 4900.5)],
    );

    expect(view.items.map((item) => item.lineTotalMinor)).toEqual([900000, 490050]);
    expect(view.totalMinor).toBe(1390050);
    expect(view.quantity).toBe(3);
    expect(view.hasProblems).toBe(false);
  });

  it("flags lines that are no longer available", () => {
    const view = buildCartView([{ variantId: 9, quantity: 1 }], []);

    expect(view.items[0].variant).toBeNull();
    expect(view.unavailableCount).toBe(1);
    expect(view.totalMinor).toBe(0);
    expect(view.hasProblems).toBe(true);
  });

  it("flags lines that are out of stock", () => {
    const view = buildCartView([{ variantId: 1, quantity: 1 }], [variant(1, 100, false)]);

    expect(view.outOfStockCount).toBe(1);
    expect(view.hasProblems).toBe(true);
  });
});
