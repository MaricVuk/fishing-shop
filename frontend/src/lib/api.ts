import { BACKEND_URL, PAGE_SIZE } from "@/lib/config";
import type {
  Category,
  PageResponse,
  ProductQuery,
  ProductSummary,
} from "@/lib/types";

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${BACKEND_URL}${path}`, { cache: "no-store" });

  if (!response.ok) {
    throw new ApiError(
      response.status,
      `Backend responded with ${response.status} for ${path}`,
    );
  }

  return (await response.json()) as T;
}

export function fetchCategories(): Promise<Category[]> {
  return getJson<Category[]>("/api/categories");
}

export function fetchProducts(
  query: ProductQuery,
  size: number = PAGE_SIZE,
): Promise<PageResponse<ProductSummary>> {
  const params = new URLSearchParams({
    sort: query.sort,
    page: String(query.page - 1),
    size: String(size),
  });

  if (query.category) params.set("category", query.category);
  if (query.q) params.set("q", query.q);

  return getJson<PageResponse<ProductSummary>>(`/api/products?${params}`);
}
