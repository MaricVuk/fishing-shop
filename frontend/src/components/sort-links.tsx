import Link from "next/link";
import { cn } from "@/lib/utils";
import { productsHref } from "@/lib/query";
import { SORT_OPTIONS, type ProductQuery } from "@/lib/types";

export function SortLinks({ query }: { query: ProductQuery }) {
  return (
    <nav
      aria-label="Sortiranje"
      className="flex flex-wrap items-center gap-1 text-sm"
    >
      <span className="mr-1 text-muted-foreground">Sortiraj:</span>
      {SORT_OPTIONS.map((option) => {
        const active = option.key === query.sort;
        return (
          <Link
            key={option.key}
            href={productsHref(query, { sort: option.key })}
            aria-current={active ? "true" : undefined}
            className={cn(
              "flex min-h-9 items-center rounded-lg px-2.5 transition-colors",
              active
                ? "bg-accent font-medium text-accent-foreground"
                : "text-muted-foreground hover:bg-muted hover:text-foreground",
            )}
          >
            {option.label}
          </Link>
        );
      })}
    </nav>
  );
}
