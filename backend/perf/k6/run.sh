#!/usr/bin/env bash
# Usage: backend/perf/k6/run.sh catalog|orders-hot|orders-spread|reports
# Needs the perf database from docs/perf/sales-report.md and a backend on it started with
# SHOP_ORDERS_MAX_PER_ADDRESS=1000000 (the per-address limit would otherwise answer 429) and
# SERVER_TOMCAT_MAX_KEEP_ALIVE_REQUESTS=-1 (see docs/perf/load-test.md: connection churn stalls Docker Desktop's NAT).
set -euo pipefail

scenario="${1:?scenario: catalog | orders-hot | orders-spread | reports}"
here="$(cd "$(dirname "$0")" && pwd)"
host_ip="$(hostname -I | awk '{print $1}')"
base_url="${BASE_URL:-http://${host_ip}:8091}"
psql_perf() { docker exec fishing-shop-perf psql -U shop -d shop_perf -Atc "$1"; }

mkdir -p "$here/results" && chmod a+rwx "$here/results"

# Each scenario runs RUNS times (default 2) and the last run counts: the first one warms up the JVM and the
# database caches (a cold first run was 30 % slower in the catalog scenario).
runs="${RUNS:-2}"

for run in $(seq 1 "$runs"); do
  echo "=== ${scenario}: run ${run} of ${runs}"
  extra_env=()

  case "$scenario" in
    orders-hot)
      variant="${VARIANT_ID:-30}"
      stock="${STOCK:-100}"
      psql_perf "UPDATE inventory SET quantity = ${stock} WHERE variant_id = ${variant}" >/dev/null
      before="$(psql_perf "SELECT COALESCE(max(id), 0) FROM orders")"
      extra_env+=(-e "VARIANT_ID=${variant}")
      ;;
    orders-spread)
      psql_perf "UPDATE inventory SET quantity = 500000" >/dev/null
      ;;
  esac

  docker run --rm -i -v "$here:/scripts:ro" -v "$here/results:/results" \
    -e "BASE_URL=${base_url}" ${extra_env[@]+"${extra_env[@]}"} \
    grafana/k6:latest run "/scripts/${scenario}.js" || true

  if [ "$scenario" = "orders-hot" ]; then
    echo "--- check against the database (variant ${variant}, stock was ${stock})"
    psql_perf "SELECT 'stock left: ' || quantity FROM inventory WHERE variant_id = ${variant}"
    psql_perf "SELECT 'units sold in this run: ' || COALESCE(sum(i.quantity), 0) FROM order_items i JOIN orders o ON o.id = i.order_id WHERE i.variant_id = ${variant} AND o.id > ${before}"
    psql_perf "SELECT 'OUT movements in this run: ' || count(*) FROM stock_movements m WHERE m.variant_id = ${variant} AND m.type = 'OUT' AND m.order_id > ${before}"
    psql_perf "SELECT 'inventory rows below zero: ' || count(*) FROM inventory WHERE quantity < 0"
  fi
done
