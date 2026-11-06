import { describe, expect, it } from "vitest";
import { loginErrorMessage, readCookie, safeNextPath } from "@/lib/admin-auth";

describe("safeNextPath", () => {
  it("keeps an admin path", () => {
    expect(safeNextPath("/admin/orders?status=NEW")).toBe("/admin/orders?status=NEW");
  });

  it("falls back to the admin home for anything else", () => {
    for (const raw of [
      null,
      "",
      "/products",
      "https://evil.example/admin",
      "//evil.example/admin",
      "/admin\\..\\evil",
      "/administrator",
      "/admin/login",
      "javascript:alert(1)",
      "/admin/../products",
      "/admin/%2e%2e/products",
      "/admin/../..//evil.example",
      "/admin/\r\nSet-Cookie: x=1",
      "/admin\t/x",
      "/admin/login/../login",
    ]) {
      expect(safeNextPath(raw)).toBe("/admin");
    }
  });
});

describe("safeNextPath keeps what the browser would navigate to", () => {
  it("returns the normalized path and query", () => {
    expect(safeNextPath("/admin/./orders/7?status=NEW#top")).toBe("/admin/orders/7?status=NEW");
    expect(safeNextPath("/admin/orders/../stock")).toBe("/admin/stock");
  });
});

describe("loginErrorMessage", () => {
  it("explains wrong credentials without saying which part is wrong", () => {
    expect(loginErrorMessage(401, null)).toBe("Pogrešan email ili lozinka.");
  });

  it("says how long to wait after too many attempts", () => {
    expect(loginErrorMessage(429, 600)).toBe(
      "Previše neuspešnih pokušaja. Pokušajte ponovo za 10 minuta.",
    );
    expect(loginErrorMessage(429, 30)).toBe(
      "Previše neuspešnih pokušaja. Pokušajte ponovo za 1 minut.",
    );
    expect(loginErrorMessage(429, null)).toBe(
      "Previše neuspešnih pokušaja. Pokušajte ponovo kasnije.",
    );
  });

  it("has a general message for everything else", () => {
    expect(loginErrorMessage(500, null)).toBe("Prijava trenutno ne radi. Pokušajte ponovo za trenutak.");
  });
});

describe("readCookie", () => {
  it("finds a cookie by name", () => {
    expect(readCookie("a=1; XSRF-TOKEN=abc-123; b=2", "XSRF-TOKEN")).toBe("abc-123");
  });

  it("decodes the value and ignores similar names", () => {
    expect(readCookie("MY-XSRF-TOKEN=no; XSRF-TOKEN=a%3Db", "XSRF-TOKEN")).toBe("a=b");
  });

  it("returns null when the cookie is missing", () => {
    expect(readCookie("", "XSRF-TOKEN")).toBeNull();
  });
});
