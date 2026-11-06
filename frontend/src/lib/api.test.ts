import { afterEach, describe, expect, it, vi } from "vitest";
import {
  ApiError,
  fetchCategories,
  fetchProduct,
  fetchProducts,
} from "@/lib/api";
import type { ProductQuery } from "@/lib/types";

const query: ProductQuery = { category: null, q: "", sort: "name", page: 1 };

function mockFetch(response: Partial<Response>) {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, ...response });
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("fetchProducts", () => {
  it("sends a zero-based page and the page size", async () => {
    const fetchMock = mockFetch({ json: async () => ({ content: [] }) });

    await fetchProducts({ ...query, page: 3 }, 12);

    const url = new URL(fetchMock.mock.calls[0][0] as string);
    expect(url.pathname).toBe("/api/products");
    expect(url.searchParams.get("page")).toBe("2");
    expect(url.searchParams.get("size")).toBe("12");
    expect(url.searchParams.get("sort")).toBe("name");
  });

  it("sends category and search only when present", async () => {
    const fetchMock = mockFetch({ json: async () => ({ content: [] }) });

    await fetchProducts({ ...query, category: "varalice", q: "rapala" });
    await fetchProducts(query);

    const withFilters = new URL(fetchMock.mock.calls[0][0] as string);
    const withoutFilters = new URL(fetchMock.mock.calls[1][0] as string);
    expect(withFilters.searchParams.get("category")).toBe("varalice");
    expect(withFilters.searchParams.get("q")).toBe("rapala");
    expect(withoutFilters.searchParams.has("category")).toBe(false);
    expect(withoutFilters.searchParams.has("q")).toBe(false);
  });

  it("never serves cached catalog data", async () => {
    const fetchMock = mockFetch({ json: async () => ({ content: [] }) });

    await fetchProducts(query);

    expect(fetchMock.mock.calls[0][1]).toMatchObject({ cache: "no-store" });
  });

  it("throws ApiError with the status when the backend fails", async () => {
    mockFetch({ ok: false, status: 400 });

    await expect(fetchProducts(query)).rejects.toMatchObject({
      name: "ApiError",
      status: 400,
    });
    await expect(fetchProducts(query)).rejects.toBeInstanceOf(ApiError);
  });
});

describe("fetchProduct", () => {
  it("requests the product by id and returns the parsed body", async () => {
    const product = { id: 7, name: "Catana", variants: [] };
    const fetchMock = mockFetch({ json: async () => product });

    await expect(fetchProduct(7)).resolves.toEqual(product);

    const url = new URL(fetchMock.mock.calls[0][0] as string);
    expect(url.pathname).toBe("/api/products/7");
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ cache: "no-store" });
  });

  it("returns null when the product does not exist", async () => {
    mockFetch({ ok: false, status: 404 });

    await expect(fetchProduct(999)).resolves.toBeNull();
  });

  it("throws ApiError for other failures", async () => {
    mockFetch({ ok: false, status: 500 });

    await expect(fetchProduct(1)).rejects.toMatchObject({
      name: "ApiError",
      status: 500,
    });
  });
});

describe("fetchCategories", () => {
  it("returns the parsed list", async () => {
    const categories = [{ id: 1, name: "Štapovi", slug: "stapovi", parentId: null }];
    mockFetch({ json: async () => categories });

    await expect(fetchCategories()).resolves.toEqual(categories);
  });
});
