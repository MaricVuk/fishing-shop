import http from "k6/http";
import { check } from "k6";
import { BASE_URL, ownerSession, tableSummary } from "./lib.js";

const VUS = Number(__ENV.VUS || 20);
const NAMES = ["dashboard", "sales-30d", "best-sellers-30d", "low-stock", "sales-5y", "best-sellers-5y"];
const FROM_5Y = __ENV.FROM_5Y || "2021-11-01";
const TO = __ENV.TO || new Date().toISOString().slice(0, 10);
const FROM_30D = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);

export const options = {
  scenarios: {
    typical: { executor: "constant-vus", vus: VUS, duration: "30s", exec: "typical" },
    fiveYears: { executor: "constant-vus", vus: 5, duration: "30s", exec: "fiveYears", startTime: "35s" },
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "max"],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:dashboard}": ["p(95)<1000"],
    "http_req_duration{name:sales-30d}": ["p(95)<1000"],
    "http_req_duration{name:best-sellers-30d}": ["p(95)<1000"],
    "http_req_duration{name:low-stock}": ["p(95)<1000"],
    "http_req_duration{name:sales-5y}": ["p(95)<2000"],
    "http_req_duration{name:best-sellers-5y}": ["p(95)<2000"],
  },
};

export function setup() {
  return { session: ownerSession() };
}

function get(data, path, name) {
  const response = http.get(`${BASE_URL}${path}`, { headers: { Cookie: `SESSION=${data.session}` }, tags: { name } });
  check(response, { [`${name} is 200`]: (r) => r.status === 200 });
}

export function typical(data) {
  get(data, "/api/admin/dashboard", "dashboard");
  get(data, `/api/admin/reports/sales?from=${FROM_30D}&to=${TO}&granularity=DAY`, "sales-30d");
  get(data, `/api/admin/reports/best-sellers?from=${FROM_30D}&to=${TO}&limit=10`, "best-sellers-30d");
  get(data, "/api/admin/reports/low-stock", "low-stock");
}

export function fiveYears(data) {
  get(data, `/api/admin/reports/sales?from=${FROM_5Y}&to=${TO}&granularity=MONTH`, "sales-5y");
  get(data, `/api/admin/reports/best-sellers?from=${FROM_5Y}&to=${TO}&limit=10`, "best-sellers-5y");
}

export function handleSummary(data) {
  return { stdout: tableSummary(data, NAMES), "/results/reports.json": JSON.stringify(data) };
}
