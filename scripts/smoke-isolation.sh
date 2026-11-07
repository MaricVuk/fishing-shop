#!/usr/bin/env bash
set -euo pipefail

compose="${COMPOSE:-docker compose --env-file demo.env -f compose.prod.yml -f compose.demo.yml}"

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

cannot_connect() {
  local service="$1" host="$2" port="$3"
  $compose exec -T "$service" node -e "
    const socket = require('net').connect({ host: '$host', port: $port, timeout: 3000 });
    socket.on('connect', () => process.exit(1));
    socket.on('timeout', () => process.exit(0));
    socket.on('error', () => process.exit(0));
  " || fail "$service can reach $host:$port"
  echo "ok   $service cannot reach $host:$port"
}

cannot_connect frontend postgres 5432
cannot_connect frontend backend 8081

body="$($compose exec -T backend bash -c "exec 3<>/dev/tcp/127.0.0.1/8081; printf 'GET /actuator/health HTTP/1.0\r\n\r\n' >&3; cat <&3")"
echo "$body" | head -n 1 | grep -q ' 200 ' || fail "the actuator does not answer on the management port inside the backend"
echo "$body" | grep -q '"jobs"' || fail "health on the management port has no job queue details"
echo "ok   the actuator answers on 127.0.0.1:8081 inside the backend"
