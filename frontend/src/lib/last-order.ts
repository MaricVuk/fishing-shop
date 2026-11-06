import type { OrderResponse } from "@/lib/types";

const STORAGE_KEY = "fishing-shop.last-order.v1";

let snapshot: { raw: string | null; order: OrderResponse | null } = {
  raw: null,
  order: null,
};

export function parseStoredOrder(raw: string | null): OrderResponse | null {
  if (!raw) return null;

  try {
    const data = JSON.parse(raw) as Partial<OrderResponse> | null;
    const valid =
      data !== null &&
      typeof data === "object" &&
      typeof data.id === "number" &&
      typeof data.total === "number" &&
      Array.isArray(data.items);
    return valid ? (data as OrderResponse) : null;
  } catch {
    return null;
  }
}

export function saveLastOrder(order: OrderResponse): void {
  try {
    window.sessionStorage.setItem(STORAGE_KEY, JSON.stringify(order));
  } catch {
    return;
  }
}

export function getLastOrderSnapshot(): OrderResponse | null {
  let raw: string | null = null;
  try {
    raw = window.sessionStorage.getItem(STORAGE_KEY);
  } catch {
    raw = null;
  }
  if (raw !== snapshot.raw) {
    snapshot = { raw, order: parseStoredOrder(raw) };
  }
  return snapshot.order;
}

export function getServerLastOrderSnapshot(): OrderResponse | null {
  return null;
}

export function subscribeToLastOrder(): () => void {
  return () => {};
}
