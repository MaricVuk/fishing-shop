import http from "k6/http";
import { check, fail } from "k6";

export const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

export function ownerSession() {
  const email = __ENV.OWNER_EMAIL || "vlasnik@ribolov.test";
  const password = __ENV.OWNER_PASSWORD || "dev-vlasnik-lozinka";
  const jar = http.cookieJar();

  http.get(`${BASE_URL}/api/auth/csrf`);
  const token = (jar.cookiesForURL(BASE_URL)["XSRF-TOKEN"] || [])[0];
  if (!token) fail("no XSRF-TOKEN cookie from /api/auth/csrf");

  const login = http.post(`${BASE_URL}/api/auth/login`, { email, password }, {
    headers: { "X-XSRF-TOKEN": token },
  });
  if (!check(login, { "login is 200": (r) => r.status === 200 })) fail(`login failed with ${login.status}`);

  const session = (jar.cookiesForURL(BASE_URL).SESSION || [])[0];
  if (!session) fail("no SESSION cookie after login");
  return session;
}

export function orderBody(lines) {
  const n = Math.floor(Math.random() * 1_000_000);
  return JSON.stringify({
    customer: {
      name: `Load Test ${n}`,
      email: `load${n}@example.com`,
      shippingAddress: "Ulica 1, Beograd",
    },
    items: lines,
  });
}

export const JSON_HEADERS = { headers: { "Content-Type": "application/json" } };

export function tableSummary(data, names) {
  const lines = [];
  const stat = (metric, key) => {
    const value = metric && metric.values ? metric.values[key] : undefined;
    return value === undefined ? "-" : value.toFixed(1);
  };
  const all = data.metrics.http_req_duration;
  lines.push(`requests: ${data.metrics.http_reqs.values.count}  rate: ${data.metrics.http_reqs.values.rate.toFixed(1)}/s`);
  lines.push(`failed (unexpected status): ${(data.metrics.http_req_failed.values.rate * 100).toFixed(2)} %`);
  lines.push(`all  p50 ${stat(all, "med")}  p95 ${stat(all, "p(95)")}  max ${stat(all, "max")} ms`);
  names.forEach((name) => {
    const metric = data.metrics[`http_req_duration{name:${name}}`];
    lines.push(`${name.padEnd(18)} p50 ${stat(metric, "med")}  p95 ${stat(metric, "p(95)")}  max ${stat(metric, "max")} ms`);
  });
  return lines.join("\n") + "\n";
}
