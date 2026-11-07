-- Same query as ProductReportService.bestSellers. Usage: psql -v from=2026-10-09 -v to=2026-11-07 -v lim=10 -f explain-best-sellers.sql
EXPLAIN (ANALYZE, BUFFERS)
WITH sold AS (
    SELECT oi.variant_id,
           sum(oi.quantity) AS quantity,
           sum(oi.unit_price * oi.quantity) AS revenue,
           count(*) AS orders
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.id
    WHERE o.status <> 'CANCELLED'
      AND o.created_at >= :'from'::date::timestamp AT TIME ZONE 'Europe/Belgrade'
      AND o.created_at < (:'to'::date + 1)::timestamp AT TIME ZONE 'Europe/Belgrade'
    GROUP BY oi.variant_id
    ORDER BY quantity DESC, revenue DESC, oi.variant_id
    LIMIT :lim
)
SELECT s.variant_id, p.id AS product_id, p.name, v.sku, v.attributes::text AS attributes,
       v.active AND p.active AS active, s.quantity, s.revenue, s.orders
FROM sold s
JOIN product_variants v ON v.id = s.variant_id
JOIN products p ON p.id = v.product_id
ORDER BY s.quantity DESC, s.revenue DESC, s.variant_id;
