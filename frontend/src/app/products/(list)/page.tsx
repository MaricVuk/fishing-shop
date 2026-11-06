import type { Metadata } from "next";
import { CategoryNav } from "@/components/category-nav";
import { EmptyState } from "@/components/empty-state";
import { Pagination } from "@/components/pagination";
import { ProductCard } from "@/components/product-card";
import { SearchForm } from "@/components/search-form";
import { SortLinks } from "@/components/sort-links";
import { fetchCategories, fetchProducts } from "@/lib/api";
import { productCountLabel } from "@/lib/format";
import { parseProductQuery } from "@/lib/query";

export const metadata: Metadata = { title: "Proizvodi" };

export default async function ProductsPage({
  searchParams,
}: PageProps<"/products">) {
  const query = parseProductQuery(await searchParams);
  const [categories, products] = await Promise.all([
    fetchCategories(),
    fetchProducts(query),
  ]);

  const activeCategory = categories.find(({ slug }) => slug === query.category);
  const heading = query.q
    ? `Rezultati za „${query.q}“`
    : (activeCategory?.name ?? "Ribolovačka oprema");

  return (
    <div className="grid gap-6 lg:grid-cols-[14rem_1fr] lg:gap-10">
      <aside>
        <CategoryNav categories={categories} query={query} />
      </aside>
      <div className="min-w-0">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h1 className="text-3xl font-bold sm:text-4xl">{heading}</h1>
            <p className="mt-1 text-sm text-muted-foreground" aria-live="polite">
              {productCountLabel(products.totalElements)}
            </p>
          </div>
          <SearchForm query={query} />
        </div>
        <div className="mt-4">
          <SortLinks query={query} />
        </div>
        {products.content.length === 0 ? (
          <EmptyState query={query} />
        ) : (
          <>
            <ul className="mt-6 grid grid-cols-2 gap-x-4 gap-y-8 lg:grid-cols-3 xl:grid-cols-4">
              {products.content.map((product) => (
                <li key={product.id}>
                  <ProductCard product={product} />
                </li>
              ))}
            </ul>
            <Pagination query={query} totalPages={products.totalPages} />
          </>
        )}
      </div>
    </div>
  );
}
