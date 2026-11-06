import { cookies } from "next/headers";
import { cache } from "react";
import { BACKEND_URL } from "@/lib/config";
import type { Employee } from "@/lib/admin-types";

export const SESSION_COOKIE = "SESSION";

export const getCurrentEmployee = cache(async (): Promise<Employee | null> => {
  const session = (await cookies()).get(SESSION_COOKIE);
  if (!session) return null;

  const response = await fetch(`${BACKEND_URL}/api/auth/me`, {
    cache: "no-store",
    headers: { Cookie: `${SESSION_COOKIE}=${session.value}` },
  });

  if (response.status === 401) return null;
  if (!response.ok) throw new Error(`Session lookup failed with ${response.status}`);

  return (await response.json()) as Employee;
});
