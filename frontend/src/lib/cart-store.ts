import {
  addToCart,
  parseStoredCart,
  removeLine,
  serializeCart,
  setQuantity,
  type AddStatus,
  type CartLine,
} from "@/lib/cart";

const STORAGE_KEY = "fishing-shop.cart.v1";
const EMPTY: CartLine[] = [];

let storageBroken = false;
let memoryRaw: string | null = null;
let snapshot: { raw: string | null; lines: CartLine[] } = {
  raw: null,
  lines: EMPTY,
};
const listeners = new Set<() => void>();

function readRaw(): string | null {
  if (storageBroken) return memoryRaw;
  try {
    return window.localStorage.getItem(STORAGE_KEY) ?? memoryRaw;
  } catch {
    return memoryRaw;
  }
}

function persist(raw: string): void {
  memoryRaw = raw;
  try {
    window.localStorage.setItem(STORAGE_KEY, raw);
  } catch {
    storageBroken = true;
  }
}

function commit(lines: CartLine[]): void {
  persist(serializeCart(lines));
  listeners.forEach((listener) => listener());
}

export function getCartSnapshot(): CartLine[] {
  const raw = readRaw();
  if (raw !== snapshot.raw) {
    snapshot = { raw, lines: parseStoredCart(raw) };
  }
  return snapshot.lines;
}

export function getServerCartSnapshot(): CartLine[] {
  return EMPTY;
}

export function subscribeToCart(listener: () => void): () => void {
  listeners.add(listener);
  window.addEventListener("storage", listener);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", listener);
  };
}

export function addVariantToCart(variantId: number): AddStatus {
  const result = addToCart(getCartSnapshot(), variantId);
  if (result.status === "added") commit(result.lines);
  return result.status;
}

export function setVariantQuantity(variantId: number, quantity: number): void {
  commit(setQuantity(getCartSnapshot(), variantId, quantity));
}

export function removeVariantFromCart(variantId: number): void {
  commit(removeLine(getCartSnapshot(), variantId));
}

export function clearCart(): void {
  commit([]);
}
