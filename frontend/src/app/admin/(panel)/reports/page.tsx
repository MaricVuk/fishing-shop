import type { Metadata } from "next";
import Link from "next/link";
import { Forbidden } from "@/components/admin/forbidden";
import { SalesChart } from "@/components/admin/sales-chart";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { accessTo } from "@/lib/admin-access";
import { AdminApiError, adminGet } from "@/lib/admin-api";
import {
  GRANULARITIES,
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
import type { SalesReport } from "@/lib/admin-types";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Izveštaji" };

async function loadReport(path: string): Promise<SalesReport | "invalid"> {
  try {
    const report = await adminGet<SalesReport>(path);
    if (!report) throw new AdminApiError(404, path);
    return report;
  } catch (failure) {
    if (failure instanceof AdminApiError && failure.status === 400) return "invalid";
    throw failure;
  }
}

function Stat({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="min-w-0 rounded-xl border px-5 py-4">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="mt-1 text-2xl font-semibold tabular-nums [overflow-wrap:anywhere]">{value}</dd>
      {hint && <dd className="mt-1 text-sm text-muted-foreground">{hint}</dd>}
    </div>
  );
}

export default async function ReportsPage({ searchParams }: PageProps<"/admin/reports">) {
  const { allowed } = await accessTo("/admin/reports");
  if (!allowed) return <Forbidden />;

  const query = parseReportQuery(await searchParams);
  const report = await loadReport(salesApiPath(query));
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
    </>
  );
}
