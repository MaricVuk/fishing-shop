import http from "k6/http";
import { check } from "k6";
import { BASE_URL, JSON_HEADERS, orderBody, tableSummary } from "./lib.js";

const VUS = Number(__ENV.VUS || 30);
const NAMES = ["order"];

http.setResponseCallback(http.expectedStatuses(201));

export const options = {
  scenarios: {
    spread: { executor: "constant-vus", vus: VUS, duration: "30s" },
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "max"],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:order}": ["p(95)<500"],
  },
};

export function setup() {
  const setupCall = { responseCallback: http.expectedStatuses(200) };
  const ids = http.get(`${BASE_URL}/api/products?size=50`, setupCall).json().content.map((product) => product.id);
  const variants = ids.flatMap((id) => http.get(`${BASE_URL}/api/products/${id}`, setupCall).json().variants.map((v) => v.id));
  return { variants };
}

export default function (data) {
  const lines = [];
  const count = 1 + Math.floor(Math.random() * 3);
  while (lines.length < count) {
    const variantId = data.variants[Math.floor(Math.random() * data.variants.length)];
    if (!lines.some((line) => line.variantId === variantId)) lines.push({ variantId, quantity: 1 + Math.floor(Math.random() * 2) });
  }
  const response = http.post(`${BASE_URL}/api/orders`, orderBody(lines), { ...JSON_HEADERS, tags: { name: "order" } });
  check(response, { "order created": (r) => r.status === 201 });
}

export function handleSummary(data) {
  return { stdout: tableSummary(data, NAMES), "/results/orders-spread.json": JSON.stringify(data) };
}
