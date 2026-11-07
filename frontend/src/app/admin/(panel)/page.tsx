import type { Metadata } from "next";
import Link from "next/link";
import { SalesChart } from "@/components/admin/sales-chart";
import { Stat } from "@/components/admin/stat";
import { requireEmployee } from "@/lib/admin-access";
import { AdminApiError, adminGet } from "@/lib/admin-api";
import { todaysBucket, waitingHint, weekRevenue } from "@/lib/admin-dashboard";
import { jobsAlert } from "@/lib/admin-jobs";
import { adminNavigation } from "@/lib/admin-nav";
import { formatAmount, formatCount } from "@/lib/admin-reports";
import { formatDateTime } from "@/lib/admin-stock";
import type { AdminJobSummary, Dashboard } from "@/lib/admin-types";
import { orderCountLabel, variantCountLabel } from "@/lib/format";

export const metadata: Metadata = { title: "Pregled" };

const DESCRIPTIONS: Record<string, string> = {
  "/admin/orders": "Nove porudžbine, potvrda, slanje i otkazivanje.",
  "/admin/stock": "Stanje po varijanti, prijem robe, popis i istorija kretanja.",
  "/admin/reports": "Prodaja, najprodavaniji artikli i niske zalihe.",
  "/admin/employees": "Nalozi zaposlenih i njihove uloge.",
  "/admin/jobs": "Mejlovi i računi koji se šalju u pozadini.",
  "/admin/audit": "Ko je šta promenio i kada.",
  "/admin/account": "Promena vaše lozinke.",
};

async function currentJobsAlert() {
  try {
    const summary = await adminGet<AdminJobSummary>("/api/admin/jobs/summary");
    return summary ? jobsAlert(summary, new Date()) : null;
  } catch (failure) {
    if (failure instanceof AdminApiError) return null;
    throw failure;
  }
}

async function loadDashboard(): Promise<Dashboard | null> {
  try {
    return await adminGet<Dashboard>("/api/admin/dashboard");
  } catch (failure) {
    if (failure instanceof AdminApiError) return null;
    throw failure;
  }
}

const LINK = "text-primary underline-offset-4 hover:underline";

function DashboardSection({ dashboard }: { dashboard: Dashboard }) {
  const { pendingOrders, sales, lowStock } = dashboard;
  const today = sales ? todaysBucket(sales) : null;

  return (
    <section aria-labelledby="dashboard-heading" className="mt-8">
      <h2 id="dashboard-heading" className="text-xl font-semibold">
        Danas
      </h2>
      <dl className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {pendingOrders && (
          <Stat
            label="Čeka potvrdu"
            value={orderCountLabel(pendingOrders.count)}
            hint={
              <>
                {waitingHint(pendingOrders, formatDateTime)}
                {pendingOrders.count > 0 && (
                  <Link href="/admin/orders?status=NEW" className={`${LINK} ${pendingOrders.oldestSince ? "ml-2" : ""}`}>
                    Otvori
                  </Link>
                )}
              </>
            }
          />
        )}
        {sales && today && (
          <Stat label="Prihod danas" value={formatAmount(today.revenue)} hint={orderCountLabel(today.orders)} />
        )}
        {sales && (
          <Stat
            label="Prihod, 7 dana"
            value={formatAmount(weekRevenue(sales))}
            hint={orderCountLabel(sales.totals.orders)}
          />
        )}
        {lowStock && (
          <Stat
            label="Niske zalihe"
            value={variantCountLabel(lowStock.total)}
            hint={
              <>
                Sa {formatCount(lowStock.threshold)} ili manje komada.{" "}
                <Link href="/admin/reports" className={LINK}>
                  Pogledaj
                </Link>
              </>
            }
          />
        )}
      </dl>
      {sales && sales.totals.orders > 0 && (
        <div className="mt-4 rounded-xl border p-4">
          <p className="mb-2 text-sm font-medium">Prodaja u poslednjih 7 dana</p>
          <SalesChart report={sales} className="h-44" />
        </div>
      )}
    </section>
  );
}

export default async function AdminHomePage() {
  const employee = await requireEmployee();
  const sections = adminNavigation(employee.role).filter((item) => item.href !== "/admin");
  const [alert, dashboard] = await Promise.all([
    employee.role === "OWNER" ? currentJobsAlert() : Promise.resolve(null),
    loadDashboard(),
  ]);

  return (
    <>
      <h1 className="text-3xl font-bold">Zdravo, {employee.fullName.split(" ")[0]}</h1>
      {alert && (
        <p role="alert" className="mt-6 rounded-xl border border-destructive/40 bg-destructive/10 px-5 py-4 text-sm">
          {alert.text}{" "}
          <Link href={alert.href} className="font-medium text-primary underline-offset-4 hover:underline">
            Pogledaj poslove
          </Link>
        </p>
      )}
      {dashboard && <DashboardSection dashboard={dashboard} />}
      <h2 className="mt-10 text-xl font-semibold">Izaberite deo kojim danas radite</h2>
      <ul className="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {sections.map((section) => (
          <li key={section.href}>
            <Link
              href={section.href}
              className="flex h-full flex-col gap-1 rounded-xl border p-5 hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            >
              <span className="text-lg font-semibold">{section.label}</span>
              <span className="text-sm text-muted-foreground">
                {section.href === "/admin/reports" && employee.role === "WAREHOUSE"
                  ? "Varijante kojima ponestaje zalihe."
                  : DESCRIPTIONS[section.href]}
              </span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
