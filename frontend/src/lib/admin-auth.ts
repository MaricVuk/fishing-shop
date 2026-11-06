const ADMIN_HOME = "/admin";
const LOGIN_PATH = "/admin/login";

export function safeNextPath(raw: string | null | undefined): string {
  if (!raw || /[\u0000-\u001f\u007f\\]/.test(raw) || !raw.startsWith("/") || raw.startsWith("//")) {
    return ADMIN_HOME;
  }

  let url: URL;
  try {
    url = new URL(raw, "http://local");
  } catch {
    return ADMIN_HOME;
  }
  if (url.origin !== "http://local") return ADMIN_HOME;

  const path = url.pathname;
  const isAdmin = path === ADMIN_HOME || path.startsWith(`${ADMIN_HOME}/`);
  if (!isAdmin || path === LOGIN_PATH || path.startsWith(`${LOGIN_PATH}/`)) return ADMIN_HOME;
  return `${path}${url.search}`;
}

export function loginErrorMessage(status: number, retryAfterSeconds: number | null): string {
  if (status === 401) return "Pogrešan email ili lozinka.";
  if (status === 429) {
    if (retryAfterSeconds === null || !Number.isFinite(retryAfterSeconds)) {
      return "Previše neuspešnih pokušaja. Pokušajte ponovo kasnije.";
    }
    const minutes = Math.max(1, Math.ceil(retryAfterSeconds / 60));
    return `Previše neuspešnih pokušaja. Pokušajte ponovo za ${minutes} ${minutes === 1 ? "minut" : "minuta"}.`;
  }
  return "Prijava trenutno ne radi. Pokušajte ponovo za trenutak.";
}

export function readCookie(cookies: string, name: string): string | null {
  for (const part of cookies.split(";")) {
    const [key, ...value] = part.trim().split("=");
    if (key === name) return decodeURIComponent(value.join("="));
  }
  return null;
}
