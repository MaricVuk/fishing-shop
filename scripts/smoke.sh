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

nonce_of() {
  grep -i '^content-security-policy:' "$1" | grep -o "'nonce-[^']*'" | sed "s/'nonce-//; s/'$//"
}

for path in /products /cart /admin/login; do
  headers="$(mktemp)"
  body="$(curl -s -D "$headers" "$base$path")"
  nonce="$(nonce_of "$headers")"
  rm -f -- "$headers"
  [ -n "$nonce" ] || fail "$path has no nonce in its Content-Security-Policy"
  scripts="$(grep -o '<script' <<<"$body" | wc -l)"
  signed="$(grep -o "<script[^>]*nonce=\"$nonce\"" <<<"$body" | wc -l)"
  [ "$scripts" -gt 0 ] && [ "$scripts" -eq "$signed" ] || fail "$path: $signed of $scripts scripts carry the nonce"
  echo "ok   $path: all $scripts scripts carry the nonce"
done

first="$(curl -s -D - -o /dev/null "$base/products" | grep -io "'nonce-[^']*'")"
second="$(curl -s -D - -o /dev/null "$base/products" | grep -io "'nonce-[^']*'")"
[ "$first" != "$second" ] || fail "the nonce does not change between requests"
echo "ok   the nonce changes on every request"
