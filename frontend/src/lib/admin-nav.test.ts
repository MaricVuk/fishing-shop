import { describe, expect, it } from "vitest";
import { adminNavigation, canAccess, roleLabel } from "@/lib/admin-nav";

const hrefs = (role: Parameters<typeof adminNavigation>[0]) =>
  adminNavigation(role).map((item) => item.href);

describe("adminNavigation", () => {
  it("shows the owner every section", () => {
    expect(hrefs("OWNER")).toEqual([
      "/admin",
      "/admin/orders",
      "/admin/stock",
      "/admin/reports",
      "/admin/employees",
      "/admin/jobs",
      "/admin/audit",
      "/admin/account",
    ]);
  });

  it("shows a seller only orders", () => {
    expect(hrefs("SELLER")).toEqual(["/admin", "/admin/orders", "/admin/account"]);
  });

  it("shows the warehouse stock and reports", () => {
    expect(hrefs("WAREHOUSE")).toEqual(["/admin", "/admin/stock", "/admin/reports", "/admin/account"]);
  });
});

describe("canAccess", () => {
  it("matches a section and its sub pages", () => {
    expect(canAccess("SELLER", "/admin/orders/42")).toBe(true);
    expect(canAccess("SELLER", "/admin/stock")).toBe(false);
    expect(canAccess("WAREHOUSE", "/admin/employees")).toBe(false);
    expect(canAccess("OWNER", "/admin/audit")).toBe(true);
    expect(canAccess("WAREHOUSE", "/admin")).toBe(true);
    expect(canAccess("SELLER", "/admin/account")).toBe(true);
    expect(canAccess("SELLER", "/admin/employees/3")).toBe(false);
    expect(canAccess("OWNER", "/admin/jobs")).toBe(true);
    expect(canAccess("SELLER", "/admin/jobs")).toBe(false);
    expect(canAccess("WAREHOUSE", "/admin/jobs")).toBe(false);
    expect(canAccess("OWNER", "/admin/reports")).toBe(true);
    expect(canAccess("SELLER", "/admin/reports")).toBe(false);
    expect(canAccess("WAREHOUSE", "/admin/reports")).toBe(true);
  });
});

describe("roleLabel", () => {
  it("names the roles in Serbian", () => {
    expect(roleLabel("OWNER")).toBe("Vlasnik");
    expect(roleLabel("SELLER")).toBe("Prodavac");
    expect(roleLabel("WAREHOUSE")).toBe("Magacioner");
  });
});
