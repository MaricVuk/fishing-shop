import type {
  FieldViolation,
  OrderRequest,
  OrderResponse,
  VariantLine,
} from "@/lib/types";

export type SubmitResult =
  | { kind: "created"; order: OrderResponse }
  | { kind: "invalid"; errors: FieldViolation[] }
  | { kind: "unavailable"; variantIds: number[] }
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

export async function submitOrder(request: OrderRequest): Promise<SubmitResult> {
  let response: Response;
  try {
    response = await fetch("/api/orders", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(request),
    });
  } catch {
    return { kind: "failed" };
  }

  if (response.status === 201) {
    return { kind: "created", order: (await response.json()) as OrderResponse };
  }

  const body = await readJson(response);

  if (response.status === 400) {
    return {
      kind: "invalid",
      errors: Array.isArray(body.errors) ? (body.errors as FieldViolation[]) : [],
    };
  }

  if (response.status === 422) {
    return {
      kind: "unavailable",
      variantIds: Array.isArray(body.unavailableVariantIds)
        ? (body.unavailableVariantIds as number[])
        : [],
    };
  }

  return { kind: "failed" };
}
