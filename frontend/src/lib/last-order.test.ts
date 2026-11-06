import { describe, expect, it } from "vitest";
import { parseStoredOrder } from "@/lib/last-order";

const order = {
  id: 7,
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

  it.each([null, "", "not json", "null", "[]", '{"id":"7"}', '{"id":7,"total":1}'])(
    "returns null for %s",
    (raw) => {
      expect(parseStoredOrder(raw)).toBeNull();
    },
  );
});
