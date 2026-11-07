export function backendTarget(pathname: string, search: string, backendUrl: string): URL | null {
  const target = new URL(`${pathname}${search}`, backendUrl);
  return target.pathname.startsWith("/api/") ? target : null;
}
