#!/usr/bin/env bash
set -euo pipefail

base="${1:-http://127.0.0.1:3100}"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

status() {
  curl -s -o /dev/null -w '%{http_code}' "$@"
}

expect_status() {
  local expected="$1" url="$2" actual
  actual="$(status "$url")"
  [ "$actual" = "$expected" ] || fail "$url returned $actual, expected $expected"
  echo "ok   $url -> $actual"
}

expect_status 200 "$base/products"
expect_status 200 "$base/api/categories"
expect_status 200 "$base/api/products"
expect_status 404 "$base/actuator/health"
expect_status 404 "$base/v3/api-docs"
expect_status 404 "$base/swagger-ui/index.html"
expect_status 401 "$base/api/admin/orders"

curl -s "$base/api/products" | grep -q '"name"' || fail "the catalog is empty"
echo "ok   the catalog has products"
