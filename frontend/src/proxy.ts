import { NextResponse, type NextRequest } from "next/server";
import { backendTarget } from "@/lib/backend-proxy";
import { BACKEND_URL } from "@/lib/config";
import { contentSecurityPolicy, createNonce } from "@/lib/csp";

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (pathname === "/api" || pathname.startsWith("/api/")) {
    const target = backendTarget(pathname, search, BACKEND_URL);
    return target ? NextResponse.rewrite(target) : new NextResponse(null, { status: 404 });
  }

  const isAdmin = pathname === "/admin" || pathname.startsWith("/admin/");
  if (isAdmin && pathname !== "/admin/login" && !request.cookies.has("SESSION")) {
    const login = new URL("/admin/login", request.url);
    login.searchParams.set("next", `${pathname}${search}`);
    return NextResponse.redirect(login);
  }

  const policy = contentSecurityPolicy(createNonce(), process.env.NODE_ENV === "development");
  const headers = new Headers(request.headers);
  headers.set("Content-Security-Policy", policy);
  const response = NextResponse.next({ request: { headers } });
  response.headers.set("Content-Security-Policy", policy);
  return response;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|favicon.ico|icon.svg).*)"],
};
