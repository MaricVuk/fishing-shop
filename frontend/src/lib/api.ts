import { BACKEND_URL, PAGE_SIZE } from "@/lib/config";
import type {
  Category,
  PageResponse,
  ProductDetail,
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

function request(path: string): Promise<Response> {
  return fetch(`${BACKEND_URL}${path}`, { cache: "no-store" });
}

function failure(response: Response, path: string): ApiError {
  return new ApiError(
    response.status,
    `Backend responded with ${response.status} for ${path}`,
  );
}

async function getJson<T>(path: string): Promise<T> {
  const response = await request(path);

  if (!response.ok) throw failure(response, path);

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

export async function fetchProduct(id: number): Promise<ProductDetail | null> {
  const path = `/api/products/${id}`;
  const response = await request(path);

  if (response.status === 404) return null;
  if (!response.ok) throw failure(response, path);

  return (await response.json()) as ProductDetail;
}
