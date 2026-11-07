import type { Metadata } from "next";
import Link from "next/link";
import { Forbidden } from "@/components/admin/forbidden";
import { JobActions } from "@/components/admin/job-actions";
import { Badge } from "@/components/ui/badge";
import { buttonVariants } from "@/components/ui/button";
import { accessTo } from "@/lib/admin-access";
import { adminGet } from "@/lib/admin-api";
import {
  JOB_STATUSES,
  isPermanentFailure,
  jobStatusLabel,
  jobTypeLabel,
  jobsHref,
  parseJobQuery,
} from "@/lib/admin-jobs";
import { formatDateTime } from "@/lib/admin-stock";
import type { AdminJob, AdminJobOverview, JobStatus } from "@/lib/admin-types";
import { cn } from "@/lib/utils";

export const metadata: Metadata = { title: "Pozadinski poslovi" };

const PAGE_SIZE = 50;
const ERROR_PREVIEW = 90;

const BADGES: Record<JobStatus, "secondary" | "outline" | "destructive"> = {
  PENDING: "secondary",
  DONE: "outline",
  DEAD: "destructive",
  RESOLVED: "outline",
};

function when(job: AdminJob): string {
  if (job.status === "PENDING") return `Sledeći pokušaj ${formatDateTime(job.nextAttemptAt)}`;
  return job.finishedAt ? formatDateTime(job.finishedAt) : "";
}

function ErrorText({ error }: { error: string | null }) {
  if (!error) return <span className="text-muted-foreground">—</span>;
  if (error.length <= ERROR_PREVIEW) return <span className="break-words">{error}</span>;
  return (
    <details>
      <summary className="cursor-pointer break-words">{error.slice(0, ERROR_PREVIEW)}… prikaži celu</summary>
      <p className="mt-2 font-mono text-xs break-all whitespace-pre-wrap">{error}</p>
    </details>
  );
}

export default async function JobsPage({ searchParams }: PageProps<"/admin/jobs">) {
  const { allowed } = await accessTo("/admin/jobs");
  if (!allowed) return <Forbidden />;

  const query = parseJobQuery(await searchParams);
  const params = new URLSearchParams({ page: String(query.page - 1), size: String(PAGE_SIZE) });
  if (query.status) params.set("status", query.status);
  const overview = await adminGet<AdminJobOverview>(`/api/admin/jobs?${params}`);
  const jobs = overview?.jobs.content ?? [];
  const totalPages = overview?.jobs.totalPages ?? 0;
  const total = overview ? JOB_STATUSES.reduce((sum, status) => sum + overview.counts[status], 0) : 0;
  const filters = [null, ...JOB_STATUSES] as const;

  return (
    <>
      <h1 className="text-3xl font-bold">Pozadinski poslovi</h1>
      <p className="mt-1 text-sm text-muted-foreground">
        Mejlovi potvrde i računi, najnoviji prvo. Posao koji je odustao možete ponovo pokrenuti kad se uzrok otkloni,
        ili ga označiti kao rešen kad ponavljanje ne treba.
      </p>

      <nav aria-label="Filter po stanju" className="mt-6 -mx-1 flex gap-1 overflow-x-auto">
        {filters.map((status) => (
          <Link
            key={status ?? "all"}
            href={jobsHref(query, { status })}
            aria-current={query.status === status ? "page" : undefined}
            className={cn(
              "rounded-lg px-3 py-2 text-sm whitespace-nowrap hover:bg-muted",
              query.status === status ? "bg-muted font-semibold" : "text-muted-foreground",
            )}
          >
            {status ? jobStatusLabel(status) : "Svi"}{" "}
            <span className="tabular-nums">({status ? (overview?.counts[status] ?? 0) : total})</span>
          </Link>
        ))}
      </nav>

      {jobs.length === 0 ? (
        <p className="mt-6 rounded-xl bg-muted px-6 py-10 text-muted-foreground">
          {query.status ? "Nema poslova u ovom stanju." : "Još nema pozadinskih poslova."}
        </p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded-xl border">
          <table className="w-full text-left text-sm">
            <thead className="bg-muted text-muted-foreground">
              <tr>
                <th scope="col" className="px-4 py-3 font-medium">Posao</th>
                <th scope="col" className="px-4 py-3 font-medium">Stanje</th>
                <th scope="col" className="px-4 py-3 text-right font-medium">Pokušaji</th>
                <th scope="col" className="px-4 py-3 font-medium">Vreme</th>
                <th scope="col" className="px-4 py-3 font-medium">Poslednja greška</th>
                <th scope="col" className="px-4 py-3 font-medium"><span className="sr-only">Akcija</span></th>
              </tr>
            </thead>
            <tbody>
              {jobs.map((job) => (
                <tr key={job.id} className="border-t align-top">
                  <td className="px-4 py-3 whitespace-nowrap">
                    <span className="font-medium">{jobTypeLabel(job.type)}</span>
                    <span className="block text-muted-foreground">
                      #{job.id} ·{" "}
                      <Link href={`/admin/orders/${job.orderId}`} className="text-primary underline-offset-4 hover:underline">
                        porudžbina #{job.orderId}
                      </Link>
                    </span>
                  </td>
                  <td className="px-4 py-3">
                    <Badge variant={BADGES[job.status]}>{jobStatusLabel(job.status)}</Badge>
                  </td>
                  <td className="px-4 py-3 text-right tabular-nums">{job.attempts}</td>
                  <td className="px-4 py-3 whitespace-nowrap">{when(job)}</td>
                  <td className="max-w-md px-4 py-3">
                    <ErrorText error={job.lastError} />
                  </td>
                  <td className="px-4 py-3">
                    {job.status === "DEAD" && (
                      <JobActions jobId={job.id} permanent={isPermanentFailure(job.lastError)} />
                    )}
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
            <Link href={jobsHref(query, { page: query.page - 1 })} className={buttonVariants({ variant: "outline" })}>
              Novije
            </Link>
          )}
          <span className="text-sm text-muted-foreground">
            Strana {query.page} od {totalPages}
          </span>
          {query.page < totalPages && (
            <Link href={jobsHref(query, { page: query.page + 1 })} className={buttonVariants({ variant: "outline" })}>
              Starije
            </Link>
          )}
        </nav>
      )}
    </>
  );
}
