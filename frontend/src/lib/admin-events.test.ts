import { describe, expect, it } from "vitest";
import { MAX_NOTICES, addNotice, describeAdminEvent, type AdminNotice } from "@/lib/admin-events";

describe("describeAdminEvent", () => {
  it("describes a new order with its total and item count", () => {
    const notice = describeAdminEvent(
      "order-placed",
      JSON.stringify({ orderId: 42, total: "9800.00", items: 2, createdAt: "2026-11-07T10:00:00Z" }),
    );
    expect(notice).toMatchObject({
      key: "order-42",
      title: "Nova porudžbina #42",
      href: "/admin/orders/42",
    });
    expect(notice?.detail).toMatch(/^2 stavke, ukupno 9\.800,00/);
  });

  it("uses the right plural for item counts", () => {
    const detail = (items: number) =>
      describeAdminEvent("order-placed", JSON.stringify({ orderId: 1, total: "1.00", items }))?.detail;
    expect(detail(1)).toMatch(/^1 stavka,/);
    expect(detail(5)).toMatch(/^5 stavki,/);
    expect(detail(11)).toMatch(/^11 stavki,/);
    expect(detail(22)).toMatch(/^22 stavke,/);
  });

  it("describes the last unit sold with the variant", () => {
    const notice = describeAdminEvent(
      "variant-sold-out",
      JSON.stringify({
        movementId: 7,
        variantId: 3,
        orderId: 42,
        productName: "Daiwa Ninja X",
        sku: "NIX-270",
        attributes: { length: "2.7m" },
      }),
    );
    expect(notice).toEqual({
      key: "sold-out-7",
      title: "Prodat poslednji komad",
      detail: "Daiwa Ninja X · Dužina: 2.7m · NIX-270",
      href: "/admin/stock/3",
      linkLabel: "Otvori u magacinu",
    });
  });

  it("ignores unknown events and malformed data", () => {
    expect(describeAdminEvent("something-else", JSON.stringify({ orderId: 1 }))).toBeNull();
    expect(describeAdminEvent("order-placed", "not json")).toBeNull();
    expect(describeAdminEvent("order-placed", "[]")).toBeNull();
    expect(describeAdminEvent("order-placed", JSON.stringify({ orderId: "1", total: "1.00", items: 1 }))).toBeNull();
    expect(describeAdminEvent("order-placed", JSON.stringify({ orderId: -1, total: "1.00", items: 1 }))).toBeNull();
    expect(describeAdminEvent("variant-sold-out", JSON.stringify({ movementId: 1, variantId: 2 }))).toBeNull();
  });

  it("does not build links from unexpected ids", () => {
    expect(
      describeAdminEvent("order-placed", JSON.stringify({ orderId: "1/../../x", total: "1.00", items: 1 })),
    ).toBeNull();
  });
});

describe("addNotice", () => {
  const notice = (key: string): AdminNotice => ({ key, title: key, detail: "", href: "/admin", linkLabel: "" });

  it("puts the newest first and keeps only a few", () => {
    let notices: AdminNotice[] = [];
    for (let i = 1; i <= MAX_NOTICES + 2; i++) notices = addNotice(notices, notice(`n${i}`));
    expect(notices.map((n) => n.key)).toEqual(["n6", "n5", "n4", "n3"]);
  });

  it("ignores a notice it already shows, for example after a replay", () => {
    const once = addNotice([], notice("order-1"));
    expect(addNotice(once, notice("order-1"))).toBe(once);
  });
});
