import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { productsHref } from "@/lib/query";
import type { ProductQuery } from "@/lib/types";

const control = buttonVariants({ variant: "outline", size: "lg" });

export function Pagination({
  query,
  totalPages,
}: {
  query: ProductQuery;
  totalPages: number;
}) {
  if (totalPages <= 1) return null;

  const hasPrevious = query.page > 1;
  const hasNext = query.page < totalPages;

  return (
    <nav
      aria-label="Strane"
      className="mt-10 flex items-center justify-between gap-4"
    >
      {hasPrevious ? (
        <Link
          rel="prev"
          href={productsHref(query, { page: query.page - 1 })}
          className={control}
        >
          Prethodna
        </Link>
      ) : (
        <span
          aria-disabled="true"
          className={cn(control, "pointer-events-none opacity-50")}
        >
          Prethodna
        </span>
      )}
      <p className="text-sm text-muted-foreground tabular-nums">
        Strana {query.page} od {totalPages}
      </p>
      {hasNext ? (
        <Link
          rel="next"
          href={productsHref(query, { page: query.page + 1 })}
          className={control}
        >
          Sledeća
        </Link>
      ) : (
        <span
          aria-disabled="true"
          className={cn(control, "pointer-events-none opacity-50")}
        >
          Sledeća
        </span>
      )}
    </nav>
  );
}
