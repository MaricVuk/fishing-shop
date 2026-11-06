import Link from "next/link";
import { buttonVariants } from "@/components/ui/button";
import type { ProductQuery } from "@/lib/types";

export function EmptyState({ query }: { query: ProductQuery }) {
  const filtered = query.q !== "" || query.category !== null || query.page > 1;

  return (
    <div className="mt-10 flex flex-col items-start gap-3 rounded-xl bg-muted px-6 py-10">
      <h2 className="text-xl font-semibold">
        {filtered ? "Nema proizvoda za zadatu pretragu" : "Katalog je trenutno prazan"}
      </h2>
      <p className="max-w-prose text-muted-foreground">
        {filtered
          ? "Probajte drugu reč, izaberite drugu kategoriju ili poništite filtere."
          : "Proizvodi će se pojaviti čim budu dodati."}
      </p>
      {filtered && (
        <Link href="/products" className={buttonVariants({ size: "lg" })}>
          Poništi filtere
        </Link>
      )}
    </div>
  );
}
