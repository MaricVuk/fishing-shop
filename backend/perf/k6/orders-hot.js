import http from "k6/http";
import { check, fail } from "k6";
import { Counter } from "k6/metrics";
import { BASE_URL, JSON_HEADERS, orderBody, tableSummary } from "./lib.js";

const VARIANT_ID = Number(__ENV.VARIANT_ID);
const VUS = Number(__ENV.VUS || 50);
const NAMES = ["order"];

const created = new Counter("orders_created");
const outOfStock = new Counter("orders_out_of_stock");
const busy = new Counter("orders_busy_503");
const other = new Counter("orders_other_status");

http.setResponseCallback(http.expectedStatuses(201, 422));

export const options = {
  scenarios: {
    hotVariant: { executor: "constant-vus", vus: VUS, duration: "30s" },
  },
  summaryTrendStats: ["avg", "med", "p(90)", "p(95)", "max"],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:order}": ["p(95)<1000"],
    orders_other_status: ["count==0"],
  },
};

export function setup() {
  if (!VARIANT_ID) fail("set VARIANT_ID to a variant prepared with a small known stock");
}

export default function () {
  const response = http.post(`${BASE_URL}/api/orders`, orderBody([{ variantId: VARIANT_ID, quantity: 1 }]),
    { ...JSON_HEADERS, tags: { name: "order" } });
  if (response.status === 201) created.add(1);
  else if (response.status === 422) outOfStock.add(1);
  else if (response.status === 503) busy.add(1);
  else other.add(1);
  check(response, { "201 or 422": (r) => r.status === 201 || r.status === 422 });
}

export function handleSummary(data) {
  const count = (name) => (data.metrics[name] ? data.metrics[name].values.count : 0);
  const extra = `created ${count("orders_created")}  out of stock (422) ${count("orders_out_of_stock")}  busy (503) ${count("orders_busy_503")}  other ${count("orders_other_status")}\n`;
  return { stdout: tableSummary(data, NAMES) + extra, "/results/orders-hot.json": JSON.stringify(data) };
}
