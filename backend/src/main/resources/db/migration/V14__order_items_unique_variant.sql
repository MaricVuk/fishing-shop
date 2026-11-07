-- OrderService already merges repeated variants into one line, so each variant appears at most once per order.
-- The unique index makes the database enforce it, which lets reports count orders per variant with count(*).
CREATE UNIQUE INDEX uq_order_items_order_variant ON order_items (order_id, variant_id);

-- Covered by the leading column of the new index.
DROP INDEX idx_order_items_order;
