import Link from "next/link";
import { cn } from "@/lib/utils";
import { productsHref } from "@/lib/query";
import type { Category, ProductQuery } from "@/lib/types";

export function CategoryNav({
  categories,
  query,
}: {
  categories: Category[];
  query: ProductQuery;
}) {
  const items = [
    { slug: null, name: "Sve kategorije" },
    ...categories.map(({ slug, name }) => ({ slug, name })),
  ];

  return (
    <nav aria-label="Kategorije">
      <ul className="-mx-4 flex gap-1 overflow-x-auto px-4 pb-2 sm:-mx-6 sm:px-6 lg:mx-0 lg:flex-col lg:overflow-visible lg:px-0 lg:pb-0">
        {items.map((item) => {
          const active = item.slug === query.category;
          return (
            <li key={item.slug ?? "all"} className="shrink-0">
              <Link
                href={productsHref(query, { category: item.slug })}
                aria-current={active ? "page" : undefined}
                className={cn(
                  "flex min-h-10 items-center rounded-lg px-3 text-sm whitespace-nowrap transition-colors",
                  active
                    ? "bg-accent font-medium text-accent-foreground"
                    : "text-muted-foreground hover:bg-muted hover:text-foreground",
                )}
              >
                {item.name}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );
}
