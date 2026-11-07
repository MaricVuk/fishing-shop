import { describe, expect, it } from "vitest";
import {
  JOB_STATUSES,
  isPermanentFailure,
  jobStatusLabel,
  jobTypeLabel,
  jobsAlert,
  jobsHref,
  parseJobQuery,
} from "@/lib/admin-jobs";
import type { AdminJobSummary } from "@/lib/admin-types";

function overview(patch: Partial<AdminJobSummary> = {}): AdminJobSummary {
  return { counts: { PENDING: 0, DONE: 3, DEAD: 0, RESOLVED: 0 }, oldestOverdueSince: null, ...patch };
}

describe("parseJobQuery", () => {
  it("reads a known status and a 1-based page", () => {
    expect(parseJobQuery({ status: "DEAD", page: "3" })).toEqual({ status: "DEAD", page: 3 });
  });

  it("ignores an unknown status and broken pages", () => {
    expect(parseJobQuery({ status: "RUNNING", page: "-1" })).toEqual({ status: null, page: 1 });
  });
});

describe("jobsHref", () => {
  it("builds the list address and resets the page on a new filter", () => {
    expect(jobsHref({ status: null, page: 1 })).toBe("/admin/jobs");
    expect(jobsHref({ status: null, page: 4 }, { status: "DEAD" })).toBe("/admin/jobs?status=DEAD");
    expect(jobsHref({ status: "DONE", page: 1 }, { page: 2 })).toBe("/admin/jobs?status=DONE&page=2");
  });
});

describe("labels", () => {
  it("names every status and job type in Serbian", () => {
    expect(JOB_STATUSES.map(jobStatusLabel)).toEqual(["Čeka", "Završen", "Odustao", "Rešen"]);
    expect(jobTypeLabel("ORDER_CONFIRMATION_EMAIL")).toBe("Potvrda porudžbine");
    expect(jobTypeLabel("ORDER_INVOICE")).toBe("Račun");
    expect(jobTypeLabel("ORDER_INVOICE_EMAIL")).toBe("Mejl sa računom");
  });

  it("falls back to the raw type, also for names that exist on every object", () => {
    expect(jobTypeLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
    expect(jobTypeLabel("toString")).toBe("toString");
    expect(jobTypeLabel("constructor")).toBe("constructor");
  });
});

describe("isPermanentFailure", () => {
  it("recognises a failure that a retry cannot fix", () => {
    expect(isPermanentFailure("PermanentJobFailure: The mail server cannot use the address")).toBe(true);
    expect(isPermanentFailure("MailSendException: Mail server connection failed")).toBe(false);
    expect(isPermanentFailure(null)).toBe(false);
  });
});

describe("jobsAlert", () => {
  const now = new Date("2026-11-06T20:00:00Z");

  it("stays quiet when nothing is dead or late, resolved jobs included", () => {
    expect(jobsAlert(overview(), now)).toBeNull();
    expect(jobsAlert(overview({ counts: { PENDING: 0, DONE: 3, DEAD: 0, RESOLVED: 4 } }), now)).toBeNull();
    expect(jobsAlert(overview({ oldestOverdueSince: "2026-11-06T19:58:00Z" }), now)).toBeNull();
  });

  it("warns about dead jobs and links to them", () => {
    expect(jobsAlert(overview({ counts: { PENDING: 0, DONE: 3, DEAD: 2, RESOLVED: 0 } }), now)).toEqual({
      text: "2 pozadinska posla su odustala. Mejlovi ili računi nisu poslati.",
      href: "/admin/jobs?status=DEAD",
    });
    expect(jobsAlert(overview({ counts: { PENDING: 0, DONE: 3, DEAD: 1, RESOLVED: 0 } }), now)?.text).toBe(
      "1 pozadinski posao je odustao. Mejlovi ili računi nisu poslati.",
    );
    expect(jobsAlert(overview({ counts: { PENDING: 0, DONE: 3, DEAD: 5, RESOLVED: 0 } }), now)?.text).toBe(
      "5 pozadinskih poslova je odustalo. Mejlovi ili računi nisu poslati.",
    );
  });

  it("warns when the oldest due job has waited more than ten minutes", () => {
    expect(jobsAlert(overview({ oldestOverdueSince: "2026-11-06T19:45:00Z" }), now)).toEqual({
      text: "Pozadinski poslovi kasne 15 min. Proverite da li backend radi.",
      href: "/admin/jobs?status=PENDING",
    });
  });
});
