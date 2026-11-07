import { describe, expect, it } from "vitest";
import { backendTarget } from "@/lib/backend-proxy";

const BACKEND = "http://backend:8080";

describe("backendTarget", () => {
  it("keeps the path and the query string", () => {
    expect(backendTarget("/api/products", "?page=2&q=stap", BACKEND)?.href).toBe(
      "http://backend:8080/api/products?page=2&q=stap",
    );
  });

  it("keeps an encoded segment as it is", () => {
    expect(backendTarget("/api/auth/%6Cogin", "", BACKEND)?.pathname).toBe("/api/auth/%6Cogin");
  });

  it("uses only the origin of the backend address", () => {
    expect(backendTarget("/api/orders", "", "http://backend:8080/ignored")?.href).toBe(
      "http://backend:8080/api/orders",
    );
  });

  it.each(["/api/../actuator/health", "/api/%2e%2e/actuator/health", "/api/%2E%2E/v3/api-docs", "/api/.%2e/actuator"])(
    "refuses a path that leaves /api/ (%s)",
    (path) => {
      expect(backendTarget(path, "", BACKEND)).toBeNull();
    },
  );

  it("refuses a path that is not under /api/", () => {
    expect(backendTarget("/actuator/health", "", BACKEND)).toBeNull();
    expect(backendTarget("/apix", "", BACKEND)).toBeNull();
  });
});
