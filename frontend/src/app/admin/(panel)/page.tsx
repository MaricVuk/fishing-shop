import type { Metadata } from "next";
import Link from "next/link";
import { requireEmployee } from "@/lib/admin-access";
import { AdminApiError, adminGet } from "@/lib/admin-api";
import { jobsAlert } from "@/lib/admin-jobs";
import { adminNavigation } from "@/lib/admin-nav";
import type { AdminJobSummary } from "@/lib/admin-types";

export const metadata: Metadata = { title: "Pregled" };

const DESCRIPTIONS: Record<string, string> = {
  "/admin/orders": "Nove porudžbine, potvrda, slanje i otkazivanje.",
  "/admin/stock": "Stanje po varijanti, prijem robe, popis i istorija kretanja.",
  "/admin/reports": "Prodaja po danima, nedeljama i mesecima.",
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

export default async function AdminHomePage() {
  const employee = await requireEmployee();
  const sections = adminNavigation(employee.role).filter((item) => item.href !== "/admin");
  const alert = employee.role === "OWNER" ? await currentJobsAlert() : null;

  return (
    <>
      <h1 className="text-3xl font-bold">Zdravo, {employee.fullName.split(" ")[0]}</h1>
      <p className="mt-2 text-muted-foreground">Izaberite deo kojim danas radite.</p>
      {alert && (
        <p role="alert" className="mt-6 rounded-xl border border-destructive/40 bg-destructive/10 px-5 py-4 text-sm">
          {alert.text}{" "}
          <Link href={alert.href} className="font-medium text-primary underline-offset-4 hover:underline">
            Pogledaj poslove
          </Link>
        </p>
      )}
      <ul className="mt-8 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {sections.map((section) => (
          <li key={section.href}>
            <Link
              href={section.href}
              className="flex h-full flex-col gap-1 rounded-xl border p-5 hover:bg-muted focus-visible:ring-3 focus-visible:ring-ring/50 focus-visible:outline-none"
            >
              <span className="text-lg font-semibold">{section.label}</span>
              <span className="text-sm text-muted-foreground">{DESCRIPTIONS[section.href]}</span>
            </Link>
          </li>
        ))}
      </ul>
    </>
  );
}
