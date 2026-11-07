-- A client may send an Idempotency-Key with a new order. A repeated request with the same key returns
-- the first order instead of creating a second one. The fingerprint is a SHA-256 of the order request,
-- so the same key with a different order can be refused.
ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN idempotency_fingerprint VARCHAR(64);

ALTER TABLE orders ADD CONSTRAINT chk_orders_idempotency
    CHECK ((idempotency_key IS NULL AND idempotency_fingerprint IS NULL)
        OR (idempotency_key ~ '^[A-Za-z0-9_-]{16,100}$' AND idempotency_fingerprint ~ '^[0-9a-f]{64}$'))
    NOT VALID;
ALTER TABLE orders VALIDATE CONSTRAINT chk_orders_idempotency;

CREATE UNIQUE INDEX uq_orders_idempotency_key ON orders (idempotency_key) WHERE idempotency_key IS NOT NULL;
