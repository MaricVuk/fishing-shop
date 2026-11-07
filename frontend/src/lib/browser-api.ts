import type {
  FieldViolation,
  OrderRequest,
  OrderResponse,
  VariantLine,
} from "@/lib/types";

export type SubmitResult =
  | { kind: "created"; order: OrderResponse }
  | { kind: "unconfirmed" }
  | { kind: "invalid"; errors: FieldViolation[] }
  | { kind: "unavailable"; variantIds: number[] }
  | { kind: "insufficient"; variantIds: number[] }
  | { kind: "alreadyPlaced" }
  | { kind: "busy" }
  | { kind: "failed" };

async function readJson(response: Response): Promise<Record<string, unknown>> {
  try {
    const body: unknown = await response.json();
    return typeof body === "object" && body !== null
      ? (body as Record<string, unknown>)
      : {};
  } catch {
    return {};
  }
}

function variantIds(value: unknown): number[] | null {
  if (!Array.isArray(value)) return null;
  return value.filter(
    (id): id is number => typeof id === "number" && Number.isSafeInteger(id) && id > 0,
  );
}

export async function fetchVariantLines(
  ids: number[],
  signal?: AbortSignal,
): Promise<VariantLine[]> {
  const response = await fetch(`/api/variants?ids=${ids.join(",")}`, { signal });

  if (!response.ok) {
    throw new Error(`Variant lookup failed with ${response.status}`);
  }

  return (await response.json()) as VariantLine[];
}

export async function submitOrder(request: OrderRequest, idempotencyKey: string): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Idempotency-Key": idempotencyKey },
      body: JSON.stringify(request),
    });
  } catch {
    return { kind: "failed" };
  }

  if (response.status === 201) {
    try {
      return { kind: "created", order: (await response.json()) as OrderResponse };
    } catch {
      return { kind: "unconfirmed" };
    }
  }

  const body = await readJson(response);

  if (response.status === 400) {
    return {
      kind: "invalid",
      errors: Array.isArray(body.errors) ? (body.errors as FieldViolation[]) : [],
    };
  }

  if (response.status === 422) {
    const unavailable = variantIds(body.unavailableVariantIds);
    if (unavailable) return { kind: "unavailable", variantIds: unavailable };
    const insufficient = variantIds(body.insufficientStockVariantIds);
    if (insufficient) return { kind: "insufficient", variantIds: insufficient };
    if (body.idempotencyKeyReused === true) return { kind: "alreadyPlaced" };
    return { kind: "invalid", errors: [] };
  }

  if (response.status === 503) {
    return { kind: "busy" };
  }

  return { kind: "failed" };
}
