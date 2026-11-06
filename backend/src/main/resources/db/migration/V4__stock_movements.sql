-- V4: stock history. Every change of inventory.quantity is written here in the same transaction,
-- so the current quantity is always the sum of the movements of its variant.

CREATE TABLE stock_movements (
    id              BIGSERIAL PRIMARY KEY,
    variant_id      BIGINT       NOT NULL REFERENCES product_variants (id),
    type            VARCHAR(20)  NOT NULL,
    quantity_change INTEGER      NOT NULL,
    quantity_after  INTEGER      NOT NULL CHECK (quantity_after >= 0),
    order_id        BIGINT       REFERENCES orders (id),
    reason          VARCHAR(200),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT chk_stock_movements_type CHECK (type IN ('IN', 'OUT', 'ADJUSTMENT')),
    CONSTRAINT chk_stock_movements_sign CHECK (
        (type = 'IN' AND quantity_change > 0)
        OR (type = 'OUT' AND quantity_change < 0)
        OR (type = 'ADJUSTMENT' AND quantity_change <> 0)
    ),
    CONSTRAINT chk_stock_movements_order CHECK ((type = 'OUT') = (order_id IS NOT NULL)),
    CONSTRAINT chk_stock_movements_adjustment_reason CHECK (
        type <> 'ADJUSTMENT' OR (reason IS NOT NULL AND btrim(reason) <> '')
    )
);

CREATE INDEX idx_stock_movements_variant ON stock_movements (variant_id, id);
CREATE INDEX idx_stock_movements_order ON stock_movements (order_id) WHERE order_id IS NOT NULL;

-- Stock that existed before this migration gets an opening entry, so the sum rule holds for it too.
INSERT INTO stock_movements (variant_id, type, quantity_change, quantity_after, reason)
SELECT variant_id, 'ADJUSTMENT', quantity, quantity, 'Opening balance'
FROM inventory
WHERE quantity > 0;
