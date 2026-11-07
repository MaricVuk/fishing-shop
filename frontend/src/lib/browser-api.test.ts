import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchVariantLines, submitOrder } from "@/lib/browser-api";
import type { OrderRequest } from "@/lib/types";

const KEY = "0123456789abcdef0123456789abcdef";

const request: OrderRequest = {
  customer: { name: "A", email: "a@b.rs", phone: "", shippingAddress: "Ulica 1" },
  items: [{ variantId: 1, quantity: 2 }],
};

function mockFetch(response: Partial<Response>) {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, ...response });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fetchVariantLines", () => {
  it("requests the ids as a comma separated list", async () => {
    const fetchMock = mockFetch({ json: async () => [] });

    await fetchVariantLines([3, 5, 8]);

    expect(fetchMock.mock.calls[0][0]).toBe("/api/variants?ids=3,5,8");
  });

  it("throws when the backend fails", async () => {
    mockFetch({ ok: false, status: 500 });

    await expect(fetchVariantLines([1])).rejects.toThrow("500");
  });
});

describe("submitOrder", () => {
  it("posts JSON and returns the created order", async () => {
    const order = { id: 12, status: "NEW", total: 9000, createdAt: "x", items: [] };
    const fetchMock = mockFetch({ status: 201, json: async () => order });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "created", order });

    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe("/api/orders");
    expect(init.method).toBe("POST");
    expect(init.headers).toMatchObject({ "Content-Type": "application/json", "Idempotency-Key": KEY });
    expect(JSON.parse(init.body)).toEqual(request);
  });

  it("returns field errors for a 400", async () => {
    const errors = [{ field: "customer.email", code: "Email" }];
    mockFetch({ ok: false, status: 400, json: async () => ({ errors }) });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "invalid", errors });
  });

  it("returns an empty error list when a 400 has no details", async () => {
    mockFetch({ ok: false, status: 400, json: async () => ({}) });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "invalid", errors: [] });
  });

  it("returns the unavailable variants for a 422", async () => {
    mockFetch({
      ok: false,
      status: 422,
      json: async () => ({ unavailableVariantIds: [4, 9] }),
    });

    await expect(submitOrder(request, KEY)).resolves.toEqual({
      kind: "unavailable",
      variantIds: [4, 9],
    });
  });

  it("returns the variants without enough stock for a 422", async () => {
    mockFetch({
      ok: false,
      status: 422,
      json: async () => ({ insufficientStockVariantIds: [2, 7] }),
    });

    await expect(submitOrder(request, KEY)).resolves.toEqual({
      kind: "insufficient",
      variantIds: [2, 7],
    });
  });

  it("ignores ids that are not positive integers", async () => {
    mockFetch({
      ok: false,
      status: 422,
      json: async () => ({ insufficientStockVariantIds: [2, "x", -1, 3.5, 8] }),
    });

    await expect(submitOrder(request, KEY)).resolves.toEqual({
      kind: "insufficient",
      variantIds: [2, 8],
    });
  });

  it("reports an order that was already placed with the same key", async () => {
    mockFetch({ ok: false, status: 422, json: async () => ({ idempotencyKeyReused: true }) });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "alreadyPlaced" });
  });

  it("reports a busy shop for a 503", async () => {
    mockFetch({ ok: false, status: 503, json: async () => ({}) });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "busy" });
  });

  it("treats a 422 without unavailable variants as an invalid request", async () => {
    mockFetch({ ok: false, status: 422, json: async () => ({ variantId: 3 }) });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "invalid", errors: [] });
  });

  it("reports an unconfirmed order when a 201 body is unreadable", async () => {
    mockFetch({
      status: 201,
      json: async () => {
        throw new Error("truncated");
      },
    });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "unconfirmed" });
  });

  it("reports failure for server errors and unreadable bodies", async () => {
    mockFetch({
      ok: false,
      status: 500,
      json: async () => {
        throw new Error("not json");
      },
    });

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "failed" });
  });

  it("reports failure when the network is down", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));

    await expect(submitOrder(request, KEY)).resolves.toEqual({ kind: "failed" });
  });
});
