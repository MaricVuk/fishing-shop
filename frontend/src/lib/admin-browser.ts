import { loginErrorMessage, readCookie } from "@/lib/admin-auth";
import type { Employee } from "@/lib/admin-types";

export type LoginResult = { kind: "ok"; employee: Employee } | { kind: "error"; message: string };

type CookieSource = () => string;

const browserCookies: CookieSource = () => document.cookie;

async function csrfHeaders(cookies: CookieSource): Promise<Record<string, string>> {
  const response = await fetch("/api/auth/csrf", { cache: "no-store" });
  if (!response.ok) throw new Error(`CSRF lookup failed with ${response.status}`);
  const { headerName, cookieName } = (await response.json()) as {
    headerName: string;
    cookieName: string;
  };
  const token = readCookie(cookies(), cookieName);
  if (!token) throw new Error("CSRF cookie is missing");
  return { [headerName]: token };
}

export async function logIn(
  email: string,
  password: string,
  cookies: CookieSource = browserCookies,
): Promise<LoginResult> {
  try {
    const headers = await csrfHeaders(cookies);
    const response = await fetch("/api/auth/login", {
      method: "POST",
      headers: { ...headers, "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({ email, password }),
    });

    if (response.ok) {
      return { kind: "ok", employee: (await response.json()) as Employee };
    }
    const retryAfter = Number.parseInt(response.headers.get("Retry-After") ?? "", 10);
    return {
      kind: "error",
      message: loginErrorMessage(response.status, Number.isNaN(retryAfter) ? null : retryAfter),
    };
  } catch {
    return { kind: "error", message: loginErrorMessage(0, null) };
  }
}

export async function logOut(cookies: CookieSource = browserCookies): Promise<boolean> {
  try {
    const headers = await csrfHeaders(cookies);
    const response = await fetch("/api/auth/logout", { method: "POST", headers });
    return response.ok;
  } catch {
    return false;
  }
}
