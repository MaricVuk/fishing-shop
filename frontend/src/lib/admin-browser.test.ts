import { afterEach, describe, expect, it, vi } from "vitest";
import { adminPost, logIn, logOut } from "@/lib/admin-browser";

const employee = { id: 1, email: "a@b.rs", fullName: "Ana", role: "OWNER" };

function response(status: number, body: unknown = {}, headers: Record<string, string> = {}) {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: new Headers(headers),
    json: async () => body,
  };
}

function stubFetch(...responses: ReturnType<typeof response>[]) {
  const fetchMock = vi.fn();
  responses.forEach((value) => fetchMock.mockResolvedValueOnce(value));
  vi.stubGlobal("fetch", fetchMock);
  return fetchMock;
}

const cookies = () => "XSRF-TOKEN=token-1";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("logIn", () => {
  it("loads a CSRF token, then posts the form with it", async () => {
    const fetchMock = stubFetch(
      response(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" }),
      response(200, employee),
    );

    await expect(logIn("a@b.rs", "secret", cookies)).resolves.toEqual({ kind: "ok", employee });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/csrf");
    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/auth/login");
    expect(init.method).toBe("POST");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("token-1");
    expect(init.headers["Content-Type"]).toBe("application/x-www-form-urlencoded");
    expect(String(init.body)).toBe("email=a%40b.rs&password=secret");
  });

  it("reports wrong credentials", async () => {
    stubFetch(response(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" }), response(401));

    await expect(logIn("a@b.rs", "nope", cookies)).resolves.toEqual({
      kind: "error",
      message: "Pogrešan email ili lozinka.",
    });
  });

  it("reports the waiting time after too many attempts", async () => {
    stubFetch(
      response(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" }),
      response(429, {}, { "Retry-After": "120" }),
    );

    await expect(logIn("a@b.rs", "nope", cookies)).resolves.toEqual({
      kind: "error",
      message: "Previše neuspešnih pokušaja. Pokušajte ponovo za 2 minuta.",
    });
  });

  it("fails softly when the server cannot be reached", async () => {
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("offline")));

    await expect(logIn("a@b.rs", "x", cookies)).resolves.toEqual({
      kind: "error",
      message: "Prijava trenutno ne radi. Pokušajte ponovo za trenutak.",
    });
  });
});

describe("logOut", () => {
  it("posts the logout with a fresh CSRF token", async () => {
    const fetchMock = stubFetch(
      response(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" }),
      response(204),
    );

    await expect(logOut(cookies)).resolves.toBe(true);

    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/auth/logout");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("token-1");
  });

  it("reports a failed logout", async () => {
    stubFetch(response(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" }), response(500));

    await expect(logOut(cookies)).resolves.toBe(false);
  });
});

describe("adminPost", () => {
  const csrf = () => response(200, { headerName: "X-XSRF-TOKEN", cookieName: "XSRF-TOKEN" });

  it("sends JSON with the CSRF header and returns the body", async () => {
    const fetchMock = stubFetch(csrf(), response(200, { variantId: 4, quantity: 9 }));

    await expect(adminPost("/api/admin/stock/4/receipts", { quantity: 4 }, cookies)).resolves.toEqual({
      kind: "ok",
      data: { variantId: 4, quantity: 9 },
    });

    const [url, init] = fetchMock.mock.calls[1];
    expect(url).toBe("/api/admin/stock/4/receipts");
    expect(init.headers["X-XSRF-TOKEN"]).toBe("token-1");
    expect(init.headers["Content-Type"]).toBe("application/json");
    expect(init.body).toBe('{"quantity":4}');
  });

  it("returns the invalid fields of a 400", async () => {
    stubFetch(csrf(), response(400, { errors: [{ field: "quantity", code: "Min" }] }));

    await expect(adminPost("/x", {}, cookies)).resolves.toEqual({ kind: "invalid", fields: ["quantity"] });
  });

  it("maps the other answers", async () => {
    for (const [status, kind] of [
      [401, "unauthorized"],
      [403, "forbidden"],
      [404, "not-found"],
      [409, "conflict"],
      [500, "failed"],
    ] as const) {
      stubFetch(csrf(), response(status));
      await expect(adminPost("/x", {}, cookies)).resolves.toEqual({ kind });
    }
  });
});
