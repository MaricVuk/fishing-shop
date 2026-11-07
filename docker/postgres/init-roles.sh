#!/bin/bash
set -euo pipefail

: "${SHOP_DB_APP_PASSWORD:?set SHOP_DB_APP_PASSWORD}"
: "${SHOP_DB_MIGRATOR_PASSWORD:?set SHOP_DB_MIGRATOR_PASSWORD}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v db="$POSTGRES_DB" \
  -v app_password="$SHOP_DB_APP_PASSWORD" \
  -v migrator_password="$SHOP_DB_MIGRATOR_PASSWORD" <<'SQL'
CREATE ROLE shop_migrator LOGIN PASSWORD :'migrator_password';
CREATE ROLE shop_app LOGIN PASSWORD :'app_password';

ALTER DATABASE :"db" OWNER TO shop_migrator;
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"db" TO shop_app;

ALTER SCHEMA public OWNER TO shop_migrator;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO shop_app;

ALTER DEFAULT PRIVILEGES FOR ROLE shop_migrator IN SCHEMA public
    GRANT SELECT, INSERT ON TABLES TO shop_app;
ALTER DEFAULT PRIVILEGES FOR ROLE shop_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO shop_app;

ALTER ROLE shop_app SET statement_timeout = '30s';
ALTER ROLE shop_app SET idle_in_transaction_session_timeout = '120s';
SQL
