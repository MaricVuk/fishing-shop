import type { AdminJobOverview, JobStatus, JobType } from "@/lib/admin-types";

type RawParams = Record<string, string | string[] | undefined>;

export type JobQuery = { status: JobStatus | null; page: number };
export type JobsAlert = { text: string; href: string };

export const JOB_STATUSES: JobStatus[] = ["PENDING", "DONE", "DEAD"];

const STATUS_LABELS: Record<JobStatus, string> = {
  PENDING: "Čeka",
  DONE: "Završen",
  DEAD: "Odustao",
};

const TYPE_LABELS: Record<JobType, string> = {
  ORDER_CONFIRMATION_EMAIL: "Potvrda porudžbine",
  ORDER_INVOICE: "Račun",
};

const MAX_PAGE = 10_000;
const LATE_AFTER_MINUTES = 10;

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

function isStatus(value: unknown): value is JobStatus {
  return typeof value === "string" && (JOB_STATUSES as string[]).includes(value);
}

export function parseJobQuery(raw: RawParams): JobQuery {
  const status = first(raw.status);
  const page = Number.parseInt(first(raw.page) ?? "1", 10);
  return {
    status: isStatus(status) ? status : null,
    page: Number.isInteger(page) && page > 1 ? Math.min(page, MAX_PAGE) : 1,
  };
}

export function jobsHref(query: JobQuery, patch: Partial<JobQuery> = {}): string {
  const next = { ...query, page: 1, ...patch };
  const params = new URLSearchParams();
  if (next.status) params.set("status", next.status);
  if (next.page > 1) params.set("page", String(next.page));
  const search = params.toString();
  return search ? `/admin/jobs?${search}` : "/admin/jobs";
}

export function jobStatusLabel(status: JobStatus): string {
  return STATUS_LABELS[status];
}

export function jobTypeLabel(type: string): string {
  return type in TYPE_LABELS ? TYPE_LABELS[type as JobType] : type;
}

export function isPermanentFailure(lastError: string | null): boolean {
  return lastError?.startsWith("PermanentJobFailure") ?? false;
}

function deadJobs(count: number): string {
  const lastDigit = count % 10;
  const lastTwo = count % 100;
  if (lastDigit === 1 && lastTwo !== 11) return `${count} pozadinski posao je odustao`;
  if (lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14)) {
    return `${count} pozadinska posla su odustala`;
  }
  return `${count} pozadinskih poslova je odustalo`;
}

export function jobsAlert(overview: AdminJobOverview, now: Date): JobsAlert | null {
  const dead = overview.counts.DEAD;
  if (dead > 0) {
    return { text: `${deadJobs(dead)}. Mejlovi ili računi nisu poslati.`, href: "/admin/jobs?status=DEAD" };
  }
  if (overview.oldestOverdueSince) {
    const minutes = Math.floor((now.getTime() - new Date(overview.oldestOverdueSince).getTime()) / 60_000);
    if (minutes > LATE_AFTER_MINUTES) {
      return {
        text: `Pozadinski poslovi kasne ${minutes} min. Proverite da li backend radi.`,
        href: "/admin/jobs?status=PENDING",
      };
    }
  }
  return null;
}
