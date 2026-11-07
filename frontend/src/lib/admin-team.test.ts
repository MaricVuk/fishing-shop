import { describe, expect, it } from "vitest";
import {
  auditEntityLabel,
  auditTypeFilterLabel,
  describeAudit,
  parseAuditQuery,
  validateNewEmployee,
  validatePassword,
} from "@/lib/admin-team";
import type { AuditEntry } from "@/lib/admin-types";

function entry(action: string, entityType: string, oldValue: unknown, newValue: unknown): AuditEntry {
  return {
    id: 1,
    actorEmail: "vlasnik@shop.test",
    action,
    entityType,
    entityId: "5",
    oldValue: oldValue as AuditEntry["oldValue"],
    newValue: newValue as AuditEntry["newValue"],
    createdAt: "2026-11-06T10:00:00Z",
  };
}

describe("validatePassword", () => {
  it("needs 12 to 64 characters that fit BCrypt", () => {
    expect(validatePassword("a-long-password")).toBeNull();
    expect(validatePassword("short")).toBe("Lozinka mora imati najmanje 12 znakova.");
    expect(validatePassword("x".repeat(65))).toBe("Lozinka može imati najviše 64 znaka.");
    expect(validatePassword("š".repeat(40))).toBe("Lozinka je predugačka. Koristite manje slova sa kvačicama.");
  });
});

describe("validateNewEmployee", () => {
  const valid = { email: "Ana@Shop.test", fullName: " Ana Anić ", role: "SELLER", password: "a-long-password" };

  it("cleans up a valid employee", () => {
    expect(validateNewEmployee(valid)).toEqual({
      ok: true,
      value: { email: "ana@shop.test", fullName: "Ana Anić", role: "SELLER", password: "a-long-password" },
    });
  });

  it("explains every problem", () => {
    expect(validateNewEmployee({ email: "x", fullName: "", role: "BOSS", password: "short" })).toEqual({
      ok: false,
      errors: {
        email: "Unesite ispravnu adresu e-pošte.",
        fullName: "Unesite ime i prezime.",
        role: "Izaberite ulogu.",
        password: "Lozinka mora imati najmanje 12 znakova.",
      },
    });
  });
});

describe("auditEntityLabel", () => {
  it("names the changed thing", () => {
    expect(auditEntityLabel("ORDER", "4")).toBe("Porudžbina #4");
    expect(auditEntityLabel("VARIANT", "1")).toBe("Varijanta #1");
    expect(auditEntityLabel("EMPLOYEE", "3")).toBe("Zaposleni #3");
  });
});

describe("describeAudit", () => {
  it("describes stock changes", () => {
    expect(describeAudit(entry("STOCK_RECEIVED", "VARIANT", { quantity: 5 }, { quantity: 12, received: 7, reason: "Otpremnica 9" })))
      .toBe("Prijem +7 (5 → 12). Otpremnica 9");
    expect(describeAudit(entry("STOCK_ADJUSTED", "VARIANT", { quantity: 5 }, { quantity: 3, change: -2, reason: "Popis" })))
      .toBe("Popis 5 → 3. Popis");
    expect(
      describeAudit(entry("STOCK_ADJUSTED", "VARIANT", { quantity: 3 }, { quantity: 2, change: -1, counted: 4, reserved: 2, reason: "Popis police" })),
    ).toBe("Popis police: izbrojano 4, rezervisano 2, slobodno 3 → 2. Popis police");
  });

  it("describes order and employee changes", () => {
    expect(describeAudit(entry("ORDER_STATUS_CHANGED", "ORDER", { status: "NEW" }, { status: "CONFIRMED" })))
      .toBe("Nova → Potvrđena.");
    expect(describeAudit(entry("EMPLOYEE_CREATED", "EMPLOYEE", null, { email: "a@b.rs", fullName: "Ana", role: "SELLER" })))
      .toBe("Dodat nalog a@b.rs (Prodavac).");
    expect(describeAudit(entry("EMPLOYEE_ROLE_CHANGED", "EMPLOYEE", { role: "SELLER" }, { role: "OWNER" })))
      .toBe("Uloga: Prodavac → Vlasnik.");
    expect(describeAudit(entry("EMPLOYEE_DEACTIVATED", "EMPLOYEE", null, null))).toBe("Nalog je isključen.");
    expect(describeAudit(entry("EMPLOYEE_ACTIVATED", "EMPLOYEE", null, null))).toBe("Nalog je ponovo uključen.");
    expect(describeAudit(entry("EMPLOYEE_PASSWORD_RESET", "EMPLOYEE", null, null))).toBe("Vlasnik je postavio novu lozinku.");
    expect(describeAudit(entry("EMPLOYEE_PASSWORD_CHANGED", "EMPLOYEE", null, null))).toBe("Zaposleni je promenio lozinku.");
    expect(describeAudit(entry("SOMETHING_NEW", "EMPLOYEE", null, null))).toBe("SOMETHING_NEW");
  });
});

describe("parseAuditQuery", () => {
  it("accepts only known entity types", () => {
    expect(parseAuditQuery({ type: "ORDER", page: "2" })).toEqual({ type: "ORDER", page: 2 });
    expect(parseAuditQuery({ type: "NOPE" })).toEqual({ type: null, page: 1 });
  });
});

describe("describeAudit for jobs", () => {
  it("describes a retried job and labels its entity", () => {
    const entry = {
      id: 1,
      actorEmail: "vlasnik@ribolov.test",
      action: "JOB_RETRIED",
      entityType: "JOB",
      entityId: "12",
      oldValue: { status: "DEAD", attempts: 14, lastError: "MailSendException: down" },
      newValue: { status: "PENDING", type: "ORDER_INVOICE", orderId: 7 },
      createdAt: "2026-11-06T20:00:00Z",
    };
    expect(describeAudit(entry)).toBe("Ponovo pokrenut posao Račun za porudžbinu #7 (posle 14 neuspelih pokušaja).");
    expect(auditEntityLabel("JOB", "12")).toBe("Posao #12");
    expect(auditTypeFilterLabel("JOB")).toBe("Poslovi");
  });
});

describe("describeAudit for a resolved job", () => {
  it("names the job and the reason", () => {
    const entry = {
      id: 2,
      actorEmail: "vlasnik@ribolov.test",
      action: "JOB_RESOLVED",
      entityType: "JOB",
      entityId: "13",
      oldValue: { status: "DEAD", attempts: 1 },
      newValue: { status: "RESOLVED", type: "ORDER_INVOICE_EMAIL", orderId: 7, reason: "Poslato ručno." },
      createdAt: "2026-11-07T10:00:00Z",
    };
    expect(describeAudit(entry)).toBe("Označen kao rešen posao Mejl sa računom za porudžbinu #7. Poslato ručno.");
    expect(describeAudit({ ...entry, newValue: { ...entry.newValue, reason: null } })).toBe(
      "Označen kao rešen posao Mejl sa računom za porudžbinu #7.",
    );
  });
});
