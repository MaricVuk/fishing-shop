-- V2: hardening after the review of V1.
-- Tables are still (nearly) empty, so locking and validating constraints is cheap.
-- On a large table, add constraints as NOT VALID and VALIDATE them in a later step.

-- Orders are cancelled through status, never deleted. Without ON DELETE CASCADE a stray
-- DELETE FROM orders fails instead of silently wiping the order lines (order history).
ALTER TABLE order_items DROP CONSTRAINT order_items_order_id_fkey;
ALTER TABLE order_items
    ADD CONSTRAINT order_items_order_id_fkey FOREIGN KEY (order_id) REFERENCES orders (id);

-- Foreign keys without an index: children of a category, and "best sellers" / variant deletion checks.
CREATE INDEX idx_categories_parent ON categories (parent_id);
CREATE INDEX idx_order_items_variant ON order_items (variant_id);

-- attributes must be a JSON object, and a product cannot have two variants with identical attributes.
ALTER TABLE product_variants
    ADD CONSTRAINT chk_variants_attributes_object CHECK (jsonb_typeof(attributes) = 'object'),
    ADD CONSTRAINT uq_variants_product_attributes UNIQUE (product_id, attributes);
