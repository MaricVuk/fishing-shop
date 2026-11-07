import http from "k6/http";
import { check } from "k6";
import { BASE_URL, tableSummary } from "./lib.js";

const VUS = Number(__ENV.VUS || 50);
const NAMES = ["list", "search", "detail", "categories"];

export const options = {
  scenarios: {
    catalog: { executor: "constant-vus", vus: VUS, duration: "40s" },
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "max"],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:list}": ["p(95)<300"],
    "http_req_duration{name:search}": ["p(95)<300"],
    "http_req_duration{name:detail}": ["p(95)<300"],
    "http_req_duration{name:categories}": ["p(95)<300"],
  },
};

export function setup() {
  const page = http.get(`${BASE_URL}/api/products?size=50`).json();
  return { productIds: page.content.map((product) => product.id), pages: page.totalPages };
}

const SEARCHES = ["spin", "daiwa", "okuma", "najlon", "udica", "box"];

export default function (data) {
  const ok = (response) => check(response, { "status is 200": (r) => r.status === 200 });
  const pick = (items) => items[Math.floor(Math.random() * items.length)];

  ok(http.get(`${BASE_URL}/api/products?size=12&page=${Math.floor(Math.random() * 2)}`, { tags: { name: "list" } }));
  ok(http.get(`${BASE_URL}/api/products?size=12&q=${pick(SEARCHES)}`, { tags: { name: "search" } }));
  ok(http.get(`${BASE_URL}/api/products/${pick(data.productIds)}`, { tags: { name: "detail" } }));
  ok(http.get(`${BASE_URL}/api/categories`, { tags: { name: "categories" } }));
}

export function handleSummary(data) {
  return { stdout: tableSummary(data, NAMES), "/results/catalog.json": JSON.stringify(data) };
}
