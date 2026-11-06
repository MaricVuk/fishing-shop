-- V7: employees change order status (Phase 3). A cancelled order puts the units it took back
-- into stock as a RETURN movement, and every status change is audited.

ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_type;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_type
    CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT', 'RETURN'));

ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_sign;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_sign CHECK (
    (type = 'IN' AND quantity_change > 0)
    OR (type = 'OUT' AND quantity_change < 0)
    OR (type = 'ADJUSTMENT' AND quantity_change <> 0)
    OR (type = 'RETURN' AND quantity_change > 0)
);

ALTER TABLE stock_movements DROP CONSTRAINT chk_stock_movements_order;
ALTER TABLE stock_movements ADD CONSTRAINT chk_stock_movements_order
    CHECK ((type IN ('OUT', 'RETURN')) = (order_id IS NOT NULL));

-- An order can give each variant back only once, whatever happens in the application.
CREATE UNIQUE INDEX uq_stock_movements_return ON stock_movements (order_id, variant_id) WHERE type = 'RETURN';

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_action;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_action
    CHECK (action IN ('STOCK_RECEIVED', 'STOCK_ADJUSTED', 'ORDER_STATUS_CHANGED'));

ALTER TABLE audit_log DROP CONSTRAINT chk_audit_log_entity_type;
ALTER TABLE audit_log ADD CONSTRAINT chk_audit_log_entity_type CHECK (entity_type IN ('VARIANT', 'ORDER'));

CREATE INDEX idx_orders_status_created ON orders (status, created_at DESC, id DESC);
CREATE INDEX idx_orders_created ON orders (created_at DESC, id DESC);
