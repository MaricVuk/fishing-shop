-- The application role (docker/postgres/init-roles.sh) gets SELECT and INSERT on every new table by default.
-- Anything it also has to change is listed here, so a new table that is not listed stays insert-only.
-- Databases without the role (tests, the dev database) are left alone.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'shop_app') THEN
        GRANT UPDATE ON inventory, orders, employees, background_jobs, invoice_counters TO shop_app;
        GRANT UPDATE, DELETE ON spring_session, spring_session_attributes TO shop_app;
        REVOKE ALL ON flyway_schema_history FROM shop_app;
    END IF;
END
$$;
