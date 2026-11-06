-- V1: base schema for Phase 1 and 2 (catalog, variants, inventory, orders)
-- Money is NUMERIC, never double. Change the schema only with new migrations (V2, V3, ...).

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    parent_id   BIGINT REFERENCES categories (id)
);

CREATE TABLE products (
    id          BIGSERIAL PRIMARY KEY,
    category_id BIGINT       NOT NULL REFERENCES categories (id),
    name        VARCHAR(200) NOT NULL,
    brand       VARCHAR(100),
    description TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_name ON products (lower(name));

-- A variant is a concrete purchasable unit (e.g. rod 2.7m, casting weight 20-60g).
-- Price and stock belong to the variant, not to the product.
CREATE TABLE product_variants (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT         NOT NULL REFERENCES products (id),
    sku         VARCHAR(64)    NOT NULL UNIQUE,
    attributes  JSONB          NOT NULL DEFAULT '{}'::jsonb,  -- {"length":"2.7m","action":"fast"}
    price       NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    active      BOOLEAN        NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_variants_product ON product_variants (product_id);

-- Stock: one row per variant. version is used for optimistic locking (Phase 2).
CREATE TABLE inventory (
    variant_id  BIGINT PRIMARY KEY REFERENCES product_variants (id),
    quantity    INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0),
    version     BIGINT  NOT NULL DEFAULT 0
);

CREATE TABLE orders (
    id             BIGSERIAL PRIMARY KEY,
    customer_name  VARCHAR(150)   NOT NULL,
    customer_email VARCHAR(200)   NOT NULL,
    customer_phone VARCHAR(50),
    shipping_address TEXT         NOT NULL,
    status         VARCHAR(20)    NOT NULL DEFAULT 'NEW'
                   CHECK (status IN ('NEW', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    total          NUMERIC(12, 2) NOT NULL CHECK (total >= 0),
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

-- Product name, SKU and price are COPIED into the item: a later price change must not alter an old order.
CREATE TABLE order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    variant_id  BIGINT         NOT NULL REFERENCES product_variants (id),
    product_name VARCHAR(200)  NOT NULL,
    sku         VARCHAR(64)    NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    quantity    INTEGER        NOT NULL CHECK (quantity > 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- Initial development data
INSERT INTO categories (name, slug) VALUES
    ('Štapovi', 'stapovi'),
    ('Mašinice', 'masinice'),
    ('Najlon i upletenica', 'najlon-i-upletenica'),
    ('Varalice', 'varalice'),
    ('Udice i pribor', 'udice-i-pribor');
