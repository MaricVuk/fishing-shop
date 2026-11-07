import type { Metadata } from "next";
import Link from "next/link";
import { Forbidden } from "@/components/admin/forbidden";
import { BestSellerTable, LowStockTable } from "@/components/admin/report-tables";
import { SalesChart } from "@/components/admin/sales-chart";
import { Stat } from "@/components/admin/stat";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { accessTo } from "@/lib/admin-access";
import { AdminApiError, adminGet } from "@/lib/admin-api";
import {
  BEST_SELLERS_LIMIT,
  GRANULARITIES,
  bestSellersApiPath,
  bucketTitle,
  formatAmount,
  formatCount,
  formatShopDate,
  granularityLabel,
  parseReportQuery,
  reportPresets,
  reportsHref,
  salesApiPath,
  shopToday,
} from "@/lib/admin-reports";
import type { BestSellerReport, LowStockReport, SalesReport } from "@/lib/admin-types";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Izveštaji" };

async function loadReport<T>(path: string): Promise<T | "invalid"> {
  try {
    const report = await adminGet<T>(path);
    if (!report) throw new AdminApiError(404, path);
    return report;
  } catch (failure) {
    if (failure instanceof AdminApiError && failure.status === 400) return "invalid";
    throw failure;
  }
}

async function loadLowStock(): Promise<LowStockReport> {
  const report = await adminGet<LowStockReport>("/api/admin/reports/low-stock");
  if (!report) throw new AdminApiError(404, "/api/admin/reports/low-stock");
  return report;
}

function LowStockSection({ report }: { report: LowStockReport }) {
  return (
    <section aria-labelledby="low-stock-heading" className="mt-10">
      <h2 id="low-stock-heading" className="text-xl font-semibold">
        Niske zalihe
      </h2>
      <p className="mt-1 text-sm text-muted-foreground">
        Varijante u prodaji sa {formatCount(report.threshold)} ili manje komada, najmanje prvo.
      </p>
      {report.items.length === 0 ? (
        <p className="mt-4 rounded-xl bg-muted px-6 py-10 text-muted-foreground">Nijednoj varijanti ne ponestaje zalihe.</p>
      ) : (
        <>
          <LowStockTable items={report.items} />
          {report.total > report.items.length && (
            <p className="mt-2 text-sm text-muted-foreground">
              Prikazano je prvih {formatCount(report.items.length)} od {formatCount(report.total)}.
            </p>
          )}
        </>
      )}
    </section>
  );
}

export default async function ReportsPage({ searchParams }: PageProps<"/admin/reports">) {
  const { employee, allowed } = await accessTo("/admin/reports");
  if (!allowed) return <Forbidden />;

  if (employee.role !== "OWNER") {
    const lowStock = await loadLowStock();
    return (
      <>
        <h1 className="text-3xl font-bold">Izveštaji</h1>
        <LowStockSection report={lowStock} />
      </>
    );
  }

  const query = parseReportQuery(await searchParams);
  const [report, bestSellers, lowStock] = await Promise.all([
    loadReport<SalesReport>(salesApiPath(query)),
    loadReport<BestSellerReport>(bestSellersApiPath(query)),
    loadLowStock(),
  ]);
  const presets = reportPresets(shopToday(new Date()));
  const shown = report === "invalid" ? query : { from: report.from, to: report.to, granularity: report.granularity };

  return (
    <>
      <h1 className="text-3xl font-bold">Izveštaji</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Prodaja po datumu porudžbine, po vremenu u Beogradu. Otkazane porudžbine nisu uračunate.
      </p>

      <nav aria-label="Brzi izbor perioda" className="mt-6 -mx-1 flex gap-1 overflow-x-auto">
        {presets.map((preset) => {
          const active =
            preset.query.from === shown.from && preset.query.to === shown.to && preset.query.granularity === shown.granularity;
          return (
            <Link
              key={preset.label}
              href={reportsHref(preset.query)}
              aria-current={active ? "page" : undefined}
              className={cn(
                "rounded-lg px-3 py-2 text-sm whitespace-nowrap hover:bg-muted",
                active ? "bg-muted font-semibold" : "text-muted-foreground",
              )}
            >
              {preset.label}
            </Link>
          );
        })}
      </nav>

      <form action="/admin/reports" method="get" className="mt-4 flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1.5">
          <label htmlFor="from" className="text-sm font-medium">
            Od
          </label>
          <Input id="from" name="from" type="date" defaultValue={shown.from ?? undefined} className="h-10 w-40" />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="to" className="text-sm font-medium">
            Do
          </label>
          <Input id="to" name="to" type="date" defaultValue={shown.to ?? undefined} className="h-10 w-40" />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor="granularity" className="text-sm font-medium">
            Prikaz
          </label>
          <select
            id="granularity"
            name="granularity"
            defaultValue={shown.granularity}
            className="h-10 rounded-lg border border-input bg-transparent px-2.5 text-sm outline-none focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 dark:bg-input/30"
          >
            {GRANULARITIES.map((granularity) => (
              <option key={granularity} value={granularity}>
                {granularityLabel(granularity)}
              </option>
            ))}
          </select>
        </div>
        <Button type="submit" size="lg" className="h-10">
          Prikaži
        </Button>
      </form>

      {report === "invalid" ? (
        <p role="alert" className="mt-6 rounded-xl border border-destructive/40 bg-destructive/10 px-5 py-4 text-sm">
          Period nije ispravan: početak mora biti pre kraja, a izveštaj može imati najviše 400 tačaka. Za duži period
          izaberite prikaz po nedeljama ili mesecima.
        </p>
      ) : (
        <section aria-labelledby="sales-heading" className="mt-8">
          <h2 id="sales-heading" className="text-xl font-semibold">
            Prodaja od {formatShopDate(report.from)} do {formatShopDate(report.to)}
          </h2>
          <dl className="mt-4 grid gap-4 lg:grid-cols-3">
            <Stat label="Prihod" value={formatAmount(report.totals.revenue)} />
            <Stat label="Porudžbine" value={formatCount(report.totals.orders)} />
            <Stat
              label="Čeka potvrdu"
              value={formatAmount(report.totals.newRevenue)}
              hint={`${formatCount(report.totals.newOrders)} nepotvrđenih porudžbina, uračunato u prihod`}
            />
          </dl>

          {report.totals.orders === 0 ? (
            <p className="mt-6 rounded-xl bg-muted px-6 py-10 text-muted-foreground">U ovom periodu nema porudžbina.</p>
          ) : (
            <>
              <div className="mt-6 rounded-xl border p-4">
                <SalesChart report={report} />
              </div>
              <details className="mt-4">
                <summary className="cursor-pointer text-sm font-medium">Tabela po periodima</summary>
                <div className="mt-3 overflow-x-auto rounded-xl border">
                  <table className="w-full text-left text-sm">
                    <thead className="bg-muted text-muted-foreground">
                      <tr>
                        <th scope="col" className="px-4 py-3 font-medium">Period</th>
                        <th scope="col" className="px-4 py-3 text-right font-medium">Porudžbine</th>
                        <th scope="col" className="px-4 py-3 text-right font-medium">Prihod</th>
                        <th scope="col" className="px-4 py-3 text-right font-medium">Čeka potvrdu</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.buckets.map((bucket) => (
                        <tr key={bucket.start} className="border-t">
                          <td className="px-4 py-2 whitespace-nowrap">{bucketTitle(bucket.start, report.granularity)}</td>
                          <td className="px-4 py-2 text-right tabular-nums">{formatCount(bucket.orders)}</td>
                          <td className="px-4 py-2 text-right tabular-nums">{formatAmount(bucket.revenue)}</td>
                          <td className="px-4 py-2 text-right tabular-nums">{formatAmount(bucket.newRevenue)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            </>
          )}
        </section>
      )}

      {bestSellers !== "invalid" && (
        <section aria-labelledby="best-sellers-heading" className="mt-10">
          <h2 id="best-sellers-heading" className="text-xl font-semibold">
            Najprodavaniji artikli
          </h2>
          <p className="mt-1 text-sm text-muted-foreground">
            Prvih {BEST_SELLERS_LIMIT} po broju prodatih komada u istom periodu. Prihod je po cenama iz porudžbina.
          </p>
          {bestSellers.items.length === 0 ? (
            <p className="mt-4 rounded-xl bg-muted px-6 py-10 text-muted-foreground">U ovom periodu ništa nije prodato.</p>
          ) : (
            <BestSellerTable items={bestSellers.items} />
          )}
        </section>
      )}

      <LowStockSection report={lowStock} />
    </>
  );
}
