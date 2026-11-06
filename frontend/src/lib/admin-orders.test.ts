import { describe, expect, it } from "vitest";
import {
  ORDER_STATUSES,
  describeChange,
  describeInvoice,
  ordersHref,
  parseOrderQuery,
  statusActionLabel,
  statusLabel,
} from "@/lib/admin-orders";
import type { AuditEntry } from "@/lib/admin-types";

describe("parseOrderQuery", () => {
  it("reads a known status, the search and a 1-based page", () => {
    expect(parseOrderQuery({ status: "SHIPPED", q: " ana ", page: "2" })).toEqual({
      status: "SHIPPED",
      q: "ana",
      page: 2,
    });
  });

  it("ignores an unknown status and broken pages", () => {
    expect(parseOrderQuery({ status: "LOST", page: "x" })).toEqual({ status: null, q: "", page: 1 });
  });
});

describe("ordersHref", () => {
  it("builds the list address", () => {
    expect(ordersHref({ status: null, q: "", page: 1 })).toBe("/admin/orders");
    expect(ordersHref({ status: null, q: "ana", page: 3 }, { status: "NEW" })).toBe(
      "/admin/orders?status=NEW&q=ana",
    );
    expect(ordersHref({ status: "NEW", q: "", page: 1 }, { page: 2 })).toBe("/admin/orders?status=NEW&page=2");
  });
});

describe("labels", () => {
  it("names every status and every action", () => {
    expect(ORDER_STATUSES.map(statusLabel)).toEqual([
      "Nova",
      "Potvrđena",
      "Poslata",
      "Isporučena",
      "Otkazana",
    ]);
    expect(statusActionLabel("CONFIRMED")).toBe("Potvrdi");
    expect(statusActionLabel("SHIPPED")).toBe("Označi kao poslatu");
    expect(statusActionLabel("DELIVERED")).toBe("Označi kao isporučenu");
    expect(statusActionLabel("CANCELLED")).toBe("Otkaži porudžbinu");
  });
});

describe("describeChange", () => {
  const entry: AuditEntry = {
    id: 1,
    actorEmail: "ana@shop.test",
    action: "ORDER_STATUS_CHANGED",
    entityType: "ORDER",
    entityId: "7",
    oldValue: { status: "CONFIRMED" },
    newValue: { status: "CANCELLED", reason: "Kupac odustao", returned: [{ variantId: 3, quantity: 2 }] },
    createdAt: "2026-11-06T10:00:00Z",
  };

  it("says what changed, why, and what went back to stock", () => {
    expect(describeChange(entry)).toBe("Potvrđena → Otkazana. Razlog: Kupac odustao. Vraćeno na stanje: 2 kom.");
  });

  it("leaves out what is missing", () => {
    expect(describeChange({ ...entry, newValue: { status: "SHIPPED", reason: null } })).toBe(
      "Potvrđena → Poslata.",
    );
  });
});

describe("describeInvoice", () => {
  it("links an issued invoice to its PDF", () => {
    expect(
      describeInvoice(7, { status: "ISSUED", number: "RN-2026-000003", issuedAt: "2026-11-06T19:49:22Z" }),
    ).toEqual({
      title: "RN-2026-000003",
      text: "Izdat 6. 11. 2026. 20:49",
      href: "/api/admin/orders/7/invoice",
    });
  });

  it("explains a pending and a failed invoice without a link", () => {
    expect(describeInvoice(7, { status: "PENDING", number: null, issuedAt: null })).toEqual({
      title: "Račun se pravi",
      text: "Pojaviće se ovde za nekoliko sekundi. Osveži stranicu.",
      href: null,
    });
    expect(describeInvoice(7, { status: "FAILED", number: null, issuedAt: null })?.title).toBe(
      "Račun nije napravljen",
    );
  });

  it("shows nothing before the order is confirmed", () => {
    expect(describeInvoice(7, { status: "NONE", number: null, issuedAt: null })).toBeNull();
  });
});
