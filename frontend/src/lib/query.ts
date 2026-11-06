import { DEFAULT_SORT, SORT_OPTIONS, type ProductQuery } from "@/lib/types";

type RawParams = Record<string, string | string[] | undefined>;

const SLUG = /^[a-z0-9-]{1,120}$/;
const MAX_SEARCH_LENGTH = 100;
const MAX_PAGE = 10_000;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export function parseProductQuery(raw: RawParams): ProductQuery {
  const category = first(raw.category);
  const sort = SORT_OPTIONS.find((option) => option.key === first(raw.sort))?.key;
  const page = Number.parseInt(first(raw.page) ?? "1", 10);

  return {
    category: category && SLUG.test(category) ? category : null,
    q: (first(raw.q) ?? "").trim().slice(0, MAX_SEARCH_LENGTH),
    sort: sort ?? DEFAULT_SORT,
    page: Number.isInteger(page) && page > 1 ? Math.min(page, MAX_PAGE) : 1,
  };
}

export function productsHref(
  query: ProductQuery,
  patch: Partial<ProductQuery> = {},
): string {
  const next = { ...query, page: 1, ...patch };
  const params = new URLSearchParams();

  if (next.category) params.set("category", next.category);
  if (next.q) params.set("q", next.q);
  if (next.sort !== DEFAULT_SORT) params.set("sort", next.sort);
  if (next.page > 1) params.set("page", String(next.page));

  const search = params.toString();
  return search ? `/products?${search}` : "/products";
}
