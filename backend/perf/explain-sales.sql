-- Same query as SalesReportService.sales. Usage: psql -v from=2026-10-09 -v to=2026-11-07 -v unit=day -f explain-sales.sql
EXPLAIN (ANALYZE, BUFFERS)
WITH sales AS (
    SELECT date_trunc(:'unit', o.created_at AT TIME ZONE 'Europe/Belgrade')::date AS bucket,
           count(*) AS orders,
           sum(o.total) AS revenue,
           count(*) FILTER (WHERE o.status = 'NEW') AS new_orders,
           coalesce(sum(o.total) FILTER (WHERE o.status = 'NEW'), 0) AS new_revenue
    FROM orders o
    WHERE o.status <> 'CANCELLED'
      AND o.created_at >= :'from'::date::timestamp AT TIME ZONE 'Europe/Belgrade'
      AND o.created_at < (:'to'::date + 1)::timestamp AT TIME ZONE 'Europe/Belgrade'
    GROUP BY 1
)
SELECT greatest(series.bucket::date, :'from'::date) AS bucket,
       coalesce(s.orders, 0) AS orders,
       coalesce(s.revenue, 0) AS revenue,
       coalesce(s.new_orders, 0) AS new_orders,
       coalesce(s.new_revenue, 0) AS new_revenue
FROM generate_series(date_trunc(:'unit', :'from'::date::timestamp), :'to'::date::timestamp,
     ('1 ' || :'unit')::interval) AS series (bucket)
LEFT JOIN sales s ON s.bucket = series.bucket::date
ORDER BY series.bucket;
