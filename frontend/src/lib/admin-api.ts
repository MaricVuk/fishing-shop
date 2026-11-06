import { redirect } from "next/navigation";
import { cookies } from "next/headers";
import { BACKEND_URL } from "@/lib/config";
import { SESSION_COOKIE } from "@/lib/admin-session";

export class AdminApiError extends Error {
  constructor(readonly status: number, path: string) {
    super(`Backend responded with ${status} for ${path}`);
    this.name = "AdminApiError";
  }
}

export async function adminGet<T>(path: string): Promise<T | null> {
  const session = (await cookies()).get(SESSION_COOKIE);
  if (!session) redirect("/admin/login");

  const response = await fetch(`${BACKEND_URL}${path}`, {
    cache: "no-store",
    headers: { Cookie: `${SESSION_COOKIE}=${session.value}` },
  });

  if (response.status === 401) redirect("/admin/login");
  if (response.status === 404) return null;
  if (!response.ok) throw new AdminApiError(response.status, path);

  return (await response.json()) as T;
}
