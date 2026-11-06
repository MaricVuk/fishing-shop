import type { VariantLine } from "@/lib/types";

export type CartLine = { variantId: number; quantity: number };

export const MAX_QUANTITY = 99;
export const MAX_LINES = 50;

export type CartItem = {
  line: CartLine;
  variant: VariantLine | null;
  lineTotalMinor: number;
};

export type CartView = {
  items: CartItem[];
  totalMinor: number;
  quantity: number;
  unavailableCount: number;
  outOfStockCount: number;
  hasProblems: boolean;
};

export function toMinor(price: number): number {
  return Math.round(price * 100);
}

export function addToCart(
  lines: CartLine[],
  variantId: number,
  quantity = 1,
): CartLine[] | null {
  const existing = lines.find((line) => line.variantId === variantId);

  if (existing) {
    return lines.map((line) =>
      line === existing
        ? { ...line, quantity: Math.min(line.quantity + quantity, MAX_QUANTITY) }
        : line,
    );
  }

  if (lines.length >= MAX_LINES) return null;

  return [...lines, { variantId, quantity: Math.min(quantity, MAX_QUANTITY) }];
}

export function setQuantity(
  lines: CartLine[],
  variantId: number,
  quantity: number,
): CartLine[] {
  const clamped = Math.min(Math.max(Math.trunc(quantity), 1), MAX_QUANTITY);
  return lines.map((line) =>
    line.variantId === variantId ? { ...line, quantity: clamped } : line,
  );
}

export function removeLine(lines: CartLine[], variantId: number): CartLine[] {
  return lines.filter((line) => line.variantId !== variantId);
}

export function cartQuantity(lines: CartLine[]): number {
  return lines.reduce((sum, line) => sum + line.quantity, 0);
}

export function serializeCart(lines: CartLine[]): string {
  return JSON.stringify(lines);
}

function isPositiveInteger(value: unknown): value is number {
  return typeof value === "number" && Number.isSafeInteger(value) && value > 0;
}

export function parseStoredCart(raw: string | null): CartLine[] {
  if (!raw) return [];

  let data: unknown;
  try {
    data = JSON.parse(raw);
  } catch {
    return [];
  }

  if (!Array.isArray(data)) return [];

  const merged = new Map<number, number>();
  for (const entry of data) {
    if (typeof entry !== "object" || entry === null) continue;
    const { variantId, quantity } = entry as Record<string, unknown>;
    if (!isPositiveInteger(variantId) || !isPositiveInteger(quantity)) continue;
    merged.set(variantId, Math.min((merged.get(variantId) ?? 0) + quantity, MAX_QUANTITY));
  }

  return [...merged]
    .slice(0, MAX_LINES)
    .map(([variantId, quantity]) => ({ variantId, quantity }));
}

export function buildCartView(
  lines: CartLine[],
  variants: VariantLine[],
): CartView {
  const byId = new Map(variants.map((variant) => [variant.variantId, variant]));

  const items = lines.map((line): CartItem => {
    const variant = byId.get(line.variantId) ?? null;
    return {
      line,
      variant,
      lineTotalMinor: variant ? toMinor(variant.price) * line.quantity : 0,
    };
  });

  const unavailableCount = items.filter((item) => item.variant === null).length;
  const outOfStockCount = items.filter(
    (item) => item.variant !== null && !item.variant.inStock,
  ).length;

  return {
    items,
    totalMinor: items.reduce((sum, item) => sum + item.lineTotalMinor, 0),
    quantity: cartQuantity(lines),
    unavailableCount,
    outOfStockCount,
    hasProblems: unavailableCount > 0 || outOfStockCount > 0,
  };
}
