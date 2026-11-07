import { describe, expect, it } from "vitest";
import { contentSecurityPolicy, createNonce } from "@/lib/csp";

function directives(policy: string): Map<string, string[]> {
  return new Map(
    policy
      .split(";")
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        const [name, ...values] = part.split(/\s+/);
        return [name, values] as [string, string[]];
      }),
  );
}

describe("createNonce", () => {
  it("is base64 of 16 random bytes and different every time", () => {
    const nonces = new Set(Array.from({ length: 50 }, () => createNonce()));
    expect(nonces.size).toBe(50);
    for (const nonce of nonces) {
      expect(nonce).toMatch(/^[A-Za-z0-9+/]{22}==$/);
    }
  });
});

describe("contentSecurityPolicy", () => {
  const production = directives(contentSecurityPolicy("abc123==", false));

  it("allows only scripts that carry the nonce, and what they load", () => {
    expect(production.get("script-src")).toEqual(["'self'", "'nonce-abc123=='", "'strict-dynamic'"]);
  });

  it("never allows inline or evaluated scripts in production", () => {
    const scripts = production.get("script-src") ?? [];
    expect(scripts).not.toContain("'unsafe-inline'");
    expect(scripts).not.toContain("'unsafe-eval'");
    expect(production.has("script-src-attr")).toBe(false);
  });

  it("adds unsafe-eval only in development, where React needs it", () => {
    const development = directives(contentSecurityPolicy("abc123==", true));
    expect(development.get("script-src")).toContain("'unsafe-eval'");
  });

  it("keeps everything else on the shop's own origin", () => {
    expect(production.get("default-src")).toEqual(["'self'"]);
    expect(production.get("connect-src")).toEqual(["'self'"]);
    expect(production.get("font-src")).toEqual(["'self'"]);
    expect(production.get("img-src")).toEqual(["'self'", "data:", "blob:"]);
    expect(production.get("style-src")).toEqual(["'self'", "'unsafe-inline'"]);
  });

  it("keeps the rules that were already sent before the nonce", () => {
    expect(production.get("object-src")).toEqual(["'none'"]);
    expect(production.get("base-uri")).toEqual(["'self'"]);
    expect(production.get("form-action")).toEqual(["'self'"]);
    expect(production.get("frame-ancestors")).toEqual(["'none'"]);
  });

  it("is a single header line", () => {
    expect(contentSecurityPolicy("abc123==", false)).not.toMatch(/\n/);
  });
});
