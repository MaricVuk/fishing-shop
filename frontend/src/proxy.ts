import { NextResponse, type NextRequest } from "next/server";
import { backendTarget } from "@/lib/backend-proxy";
import { BACKEND_URL } from "@/lib/config";

export function proxy(request: NextRequest) {
  const { pathname, search } = request.nextUrl;

  if (pathname === "/api" || pathname.startsWith("/api/")) {
    const target = backendTarget(pathname, search, BACKEND_URL);
    return target ? NextResponse.rewrite(target) : new NextResponse(null, { status: 404 });
  }

  if (pathname === "/admin/login" || request.cookies.has("SESSION")) {
    return NextResponse.next();
  }
  const login = new URL("/admin/login", request.url);
  login.searchParams.set("next", `${pathname}${search}`);
  return NextResponse.redirect(login);
}

export const config = {
  matcher: ["/api/:path*", "/admin", "/admin/:path*"],
};
