export type Category = {
  id: number;
  name: string;
  slug: string;
  parentId: number | null;
};

export type ProductSummary = {
  id: number;
  name: string;
  brand: string | null;
  categorySlug: string;
  imageUrl: string | null;
  minPrice: number;
  inStock: boolean;
};

export type VariantDetail = {
  id: number;
  sku: string;
  attributes: Record<string, string>;
  price: number;
  inStock: boolean;
};

export type ProductDetail = {
  id: number;
  name: string;
  brand: string | null;
  description: string | null;
  imageUrl: string | null;
  category: { name: string; slug: string };
  variants: VariantDetail[];
};

export type PageResponse<T> = {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
};

export const SORT_OPTIONS = [
  { key: "name", label: "Naziv" },
  { key: "price_asc", label: "Cena: niža prvo" },
  { key: "price_desc", label: "Cena: viša prvo" },
  { key: "newest", label: "Najnovije" },
] as const;

export type SortKey = (typeof SORT_OPTIONS)[number]["key"];

export const DEFAULT_SORT: SortKey = "name";

export type ProductQuery = {
  category: string | null;
  q: string;
  sort: SortKey;
  page: number;
};
