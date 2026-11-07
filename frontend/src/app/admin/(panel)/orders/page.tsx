import type { Metadata } from "next";
import Link from "next/link";
import { Forbidden } from "@/components/admin/forbidden";
import { OrderStatusBadge } from "@/components/admin/order-status-badge";
import { Button, buttonVariants } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import { ORDER_STATUSES, ordersHref, parseOrderQuery, statusLabel } from "@/lib/admin-orders";
import { formatDateTime } from "@/lib/admin-stock";
import type { AdminOrderRow, AdminPage } from "@/lib/admin-types";
import { formatPrice } from "@/lib/format";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Porudžbine" };

const PAGE_SIZE = 25;

export default async function OrdersPage({ searchParams }: PageProps<"/admin/orders">) {
  const { allowed } = await accessTo("/admin/orders");
  if (!allowed) return <Forbidden />;

  const query = parseOrderQuery(await searchParams);
  const params = new URLSearchParams({ page: String(query.page - 1), size: String(PAGE_SIZE) });
  if (query.status) params.set("status", query.status);
  if (query.q) params.set("q", query.q);
  const result = await adminGet<AdminPage<AdminOrderRow>>(`/api/admin/orders?${params}`);
  const rows = result?.content ?? [];
  const totalPages = result?.totalPages ?? 0;
  const filters = [null, ...ORDER_STATUSES] as const;

  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold">Porudžbine</h1>
          <p className="mt-1 text-sm text-muted-foreground">{result?.totalElements ?? 0} porudžbina</p>
        </div>
        <form action="/admin/orders" method="get" role="search" className="flex w-full max-w-sm gap-2">
          {query.status && <input type="hidden" name="status" value={query.status} />}
          <label htmlFor="q" className="sr-only">
            Pretraga po broju (internom ili iz mejla kupca), imenu ili emailu
          </label>
          <Input id="q" name="q" defaultValue={query.q} placeholder="Broj, ime ili email" maxLength={100} className="h-10" />
          <Button type="submit" size="lg" className="h-10">
            Traži
          </Button>
        </form>
      </div>

      <nav aria-label="Filter po statusu" className="mt-6 -mx-1 flex gap-1 overflow-x-auto">
        {filters.map((status) => (
          <Link
            key={status ?? "all"}
            href={ordersHref(query, { status })}
            aria-current={query.status === status ? "page" : undefined}
            className={cn(
              "rounded-lg px-3 py-2 text-sm whitespace-nowrap hover:bg-muted",
              query.status === status ? "bg-muted font-semibold" : "text-muted-foreground",
            )}
          >
            {status ? statusLabel(status) : "Sve"}
          </Link>
        ))}
      </nav>

      {rows.length === 0 ? (
        <p className="mt-6 rounded-xl bg-muted px-6 py-10 text-muted-foreground">
          Nema porudžbina za izabrani filter.
        </p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Broj</th>
                <th scope="col" className="px-4 py-3 font-medium">Vreme</th>
                <th scope="col" className="px-4 py-3 font-medium">Kupac</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Stavke</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Ukupno</th>
                <th scope="col" className="px-4 py-3 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-t">
                  <td className="px-4 py-3">
                    <Link href={`/admin/orders/${row.id}`} className="font-semibold text-primary underline-offset-4 hover:underline">
                      #{row.id}
                    </Link>
                    <span className="block text-xs tabular-nums text-muted-foreground">{row.number}</span>
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap">{formatDateTime(row.createdAt)}</td>
                  <td className="px-4 py-3">
                    <span className="font-medium">{row.customerName}</span>
                    <span className="block text-muted-foreground">{row.customerEmail}</span>
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{row.itemCount}</td>
                  <td className="px-4 py-3 text-right whitespace-nowrap tabular-nums">{formatPrice(row.total)}</td>
                  <td className="px-4 py-3">
                    <OrderStatusBadge status={row.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 1 && (
        <nav aria-label="Strane" className="mt-6 flex items-center gap-3">
          {query.page > 1 && (
            <Link href={ordersHref(query, { page: query.page - 1 })} className={buttonVariants({ variant: "outline" })}>
              Prethodna
            </Link>
          )}
          <span className="text-sm text-muted-foreground">
            Strana {query.page} od {totalPages}
          </span>
          {query.page < totalPages && (
            <Link href={ordersHref(query, { page: query.page + 1 })} className={buttonVariants({ variant: "outline" })}>
              Sledeća
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
