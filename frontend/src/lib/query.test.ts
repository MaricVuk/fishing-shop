import { describe, expect, it } from "vitest";
import { parseProductQuery, productsHref } from "@/lib/query";
import type { ProductQuery } from "@/lib/types";

const base: ProductQuery = { category: null, q: "", sort: "name", page: 1 };

describe("parseProductQuery", () => {
  it("returns defaults for empty params", () => {
    expect(parseProductQuery({})).toEqual(base);
  });

  it("reads valid params", () => {
    expect(
      parseProductQuery({
        category: "stapovi",
        q: "shimano",
        sort: "price_desc",
        page: "3",
      }),
    ).toEqual({ category: "stapovi", q: "shimano", sort: "price_desc", page: 3 });
  });

  it("falls back to defaults for invalid values", () => {
    expect(
      parseProductQuery({ category: "Not A Slug", sort: "bogus", page: "abc" }),
    ).toEqual(base);
  });

  it.each(["0", "-4", "1.5x"])("treats page %s as the first page", (page) => {
    expect(parseProductQuery({ page }).page).toBe(1);
  });

  it("trims and limits the search text", () => {
    expect(parseProductQuery({ q: "  catana  " }).q).toBe("catana");
    expect(parseProductQuery({ q: "x".repeat(150) }).q).toHaveLength(100);
  });

  it("uses the first value when a param is repeated", () => {
    expect(parseProductQuery({ category: ["varalice", "stapovi"] }).category).toBe(
      "varalice",
    );
  });
});

describe("productsHref", () => {
  it("omits default values", () => {
    expect(productsHref(base)).toBe("/products");
  });

  it("serializes non-default values", () => {
    expect(
      productsHref(base, {
        category: "masinice",
        q: "daiwa",
        sort: "newest",
        page: 2,
      }),
    ).toBe("/products?category=masinice&q=daiwa&sort=newest&page=2");
  });

  it("resets the page when another filter changes", () => {
    expect(productsHref({ ...base, page: 4 }, { sort: "price_asc" })).toBe(
      "/products?sort=price_asc",
    );
  });

  it("keeps the page when the patch sets it", () => {
    expect(productsHref(base, { page: 3 })).toBe("/products?page=3");
  });

  it("clears the category with null", () => {
    expect(productsHref({ ...base, category: "stapovi" }, { category: null })).toBe(
      "/products",
    );
  });

  it("encodes the search text", () => {
    expect(productsHref({ ...base, q: "štap & mašinica" })).toBe(
      "/products?q=%C5%A1tap+%26+ma%C5%A1inica",
    );
  });
});
