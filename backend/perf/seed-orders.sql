-- Synthetic order history for EXPLAIN ANALYZE and load tests. Never run against the dev or a real database:
-- it inserts about 550k orders and 1.4M items (5 years at 300 orders a day).
-- Usage: psql -v years=5 -v per_day=300 -f seed-orders.sql (on a database migrated with the dev profile).

\set ON_ERROR_STOP on
\timing on

BEGIN;

CREATE TEMP TABLE seed_variants AS
SELECT row_number() OVER (ORDER BY v.id) - 1 AS idx, v.id, v.sku, v.price, p.name
FROM product_variants v JOIN products p ON p.id = v.product_id
WHERE v.active AND p.active;

SELECT count(*) AS variant_count FROM seed_variants \gset

INSERT INTO orders (customer_name, customer_email, shipping_address, total, status, created_at)
SELECT 'Kupac ' || g, 'kupac' || g || '@example.com', 'Ulica ' || (g % 500) || ', Beograd', 0,
       CASE
           WHEN r < 0.04 THEN 'CANCELLED'
           WHEN age < interval '2 days' THEN (ARRAY['NEW', 'CONFIRMED'])[1 + (g % 2)]
           WHEN age < interval '7 days' THEN (ARRAY['CONFIRMED', 'SHIPPED'])[1 + (g % 2)]
           ELSE 'DELIVERED'
       END,
       now() - age
FROM (
    SELECT g, random() AS r, random() * (:years * interval '365 days') AS age
    FROM generate_series(1, :years * 365 * :per_day) AS g
) AS raw;

INSERT INTO order_items (order_id, variant_id, product_name, sku, unit_price, quantity)
SELECT o.id, v.id, v.name, v.sku, v.price, 1 + ((o.id + n) % 3)::int
FROM (
    SELECT id, floor(random() * :variant_count)::int AS base,
           CASE WHEN random() < 0.4 THEN 1 WHEN random() < 0.6 THEN 2 WHEN random() < 0.7 THEN 3 ELSE 4 END AS lines
    FROM orders WHERE customer_email LIKE 'kupac%@example.com' AND total = 0
) AS o
CROSS JOIN LATERAL generate_series(0, o.lines - 1) AS n
JOIN seed_variants v ON v.idx = (o.base + n * 7) % :variant_count;

UPDATE orders o SET total = t.total
FROM (SELECT order_id, sum(unit_price * quantity) AS total FROM order_items GROUP BY order_id) AS t
WHERE t.order_id = o.id AND o.total = 0;

COMMIT;

VACUUM (FULL, ANALYZE) orders;
VACUUM ANALYZE order_items;

SELECT count(*) AS orders, min(created_at), max(created_at) FROM orders;
SELECT count(*) AS order_items FROM order_items;
