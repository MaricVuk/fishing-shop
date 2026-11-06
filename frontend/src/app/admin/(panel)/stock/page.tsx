import type { Metadata } from "next";
import Link from "next/link";
import { Forbidden } from "@/components/admin/forbidden";
import { StockTable } from "@/components/admin/stock-table";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import { parseStockQuery, stockHref } from "@/lib/admin-stock";
import type { AdminPage, StockRow } from "@/lib/admin-types";

export const metadata: Metadata = { title: "Magacin" };

const PAGE_SIZE = 25;

export default async function StockPage({ searchParams }: PageProps<"/admin/stock">) {
  const { allowed } = await accessTo("/admin/stock");
  if (!allowed) return <Forbidden />;

  const query = parseStockQuery(await searchParams);
  const params = new URLSearchParams({ page: String(query.page - 1), size: String(PAGE_SIZE) });
  if (query.q) params.set("q", query.q);
  const result = await adminGet<AdminPage<StockRow>>(`/api/admin/stock?${params}`);
  const rows = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Magacin</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            {result?.totalElements ?? 0} varijanti{query.q ? ` za „${query.q}"` : ""}
          </p>
        </div>
        <form action="/admin/stock" method="get" role="search" className="flex w-full max-w-sm gap-2">
          <label htmlFor="q" className="sr-only">
            Pretraga po nazivu, brendu ili SKU
          </label>
          <Input id="q" name="q" defaultValue={query.q} placeholder="Naziv, brend ili SKU" maxLength={100} className="h-10" />
          <Button type="submit" size="lg" className="h-10">
            Traži
          </Button>
        </form>
      </div>

      {rows.length === 0 ? (
        <p className="mt-8 rounded-xl bg-muted px-6 py-10 text-muted-foreground">
          {query.q ? "Nijedna varijanta ne odgovara pretrazi." : "U katalogu još nema varijanti."}
        </p>
      ) : (
        <StockTable rows={rows} />
      )}

      {totalPages > 1 && (
        <nav aria-label="Strane" className="mt-6 flex items-center gap-3">
          {query.page > 1 && (
            <Link href={stockHref(query, { page: query.page - 1 })} className={buttonVariants({ variant: "outline" })}>
              Prethodna
            </Link>
          )}
          <span className="text-sm text-muted-foreground">
            Strana {query.page} od {totalPages}
          </span>
          {query.page < totalPages && (
            <Link href={stockHref(query, { page: query.page + 1 })} className={buttonVariants({ variant: "outline" })}>
              Sledeća
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
