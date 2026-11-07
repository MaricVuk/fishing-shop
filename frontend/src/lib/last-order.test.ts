import { describe, expect, it } from "vitest";
import { parseStoredOrder } from "@/lib/last-order";

const order = {
  number: "1A2B-3C4D-5E6F",
  status: "NEW",
  total: 9000,
  createdAt: "2026-11-06T00:00:00Z",
  items: [
    { productName: "Catana", sku: "CAT-240", unitPrice: 4500, quantity: 2, lineTotal: 9000 },
  ],
};

describe("parseStoredOrder", () => {
  it("restores a stored order", () => {
    expect(parseStoredOrder(JSON.stringify(order))).toEqual(order);
  });

  it("ignores an order stored before orders had a public number", () => {
    const { number, ...oldFormat } = order;
    expect(number).toBeDefined();
    expect(parseStoredOrder(JSON.stringify({ ...oldFormat, id: 7 }))).toBeNull();
  });

  it.each([null, "", "not json", "null", "[]", '{"number":7}', '{"number":"1A2B-3C4D-5E6F","total":1}'])(
    "returns null for %s",
    (raw) => {
      expect(parseStoredOrder(raw)).toBeNull();
    },
  );
});
