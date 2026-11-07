-- The number a customer sees must not be the sequential id, which tells a competitor how many orders there are.
-- The token is 12 random hex digits, shown as XXXX-XXXX-XXXX. The database generates it, so every insert gets one.
ALTER TABLE orders ADD COLUMN public_token VARCHAR(12);

UPDATE orders SET public_token = upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));

ALTER TABLE orders ADD CONSTRAINT chk_orders_public_token
    CHECK (public_token ~ '^[0-9A-F]{12}$') NOT VALID;
ALTER TABLE orders VALIDATE CONSTRAINT chk_orders_public_token;

ALTER TABLE orders ALTER COLUMN public_token SET NOT NULL;
ALTER TABLE orders ALTER COLUMN public_token
    SET DEFAULT upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 12));

CREATE UNIQUE INDEX uq_orders_public_token ON orders (public_token);
