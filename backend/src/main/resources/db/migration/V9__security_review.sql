-- V9: findings of the Phase 3 security review.
-- Employees get a session version. A session is only valid while its version equals the one in
-- this table, so a login that was still in flight while an owner changed an account cannot keep
-- the session it just received.
ALTER TABLE employees ADD COLUMN session_version INTEGER NOT NULL DEFAULT 0;

-- Sessions created before this migration carry no version.
DELETE FROM spring_session;

-- One OUT movement per order and variant, like the RETURN index from V7.
CREATE UNIQUE INDEX uq_stock_movements_out ON stock_movements (order_id, variant_id) WHERE type = 'OUT';

-- No code path reads either of these indexes.
DROP INDEX idx_audit_log_created;
DROP INDEX idx_audit_log_actor;

ALTER TABLE inventory ADD CONSTRAINT chk_inventory_quantity_max CHECK (quantity <= 1000000);
