package rs.fishingshop.reports;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@EnableConfigurationProperties(ReportProperties.class)
public class ProductReportService {

    static final int MAX_LOW_STOCK_ITEMS = 200;
    private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ReportProperties properties;

    ProductReportService(JdbcTemplate jdbc, JsonMapper json, ReportProperties properties) {
        this.jdbc = jdbc;
        this.json = json;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public BestSellerReport bestSellers(ReportPeriod period, int limit) {
        ReportSession.tune(jdbc);
        List<BestSellerReport.Item> items = jdbc.query("""
                WITH sold AS (
                    SELECT oi.variant_id,
                           sum(oi.quantity) AS quantity,
                           sum(oi.unit_price * oi.quantity) AS revenue,
                           count(*) AS orders
                    FROM orders o
                    JOIN order_items oi ON oi.order_id = o.id
                    WHERE o.status <> 'CANCELLED'
                      AND o.created_at >= ?::date::timestamp AT TIME ZONE 'Europe/Belgrade'
                      AND o.created_at < (?::date + 1)::timestamp AT TIME ZONE 'Europe/Belgrade'
                    GROUP BY oi.variant_id
                    ORDER BY quantity DESC, revenue DESC, oi.variant_id
                    LIMIT ?
                )
                SELECT s.variant_id, p.id AS product_id, p.name, v.sku, v.attributes::text AS attributes,
                       v.active AND p.active AS active, s.quantity, s.revenue, s.orders
                FROM sold s
                JOIN product_variants v ON v.id = s.variant_id
                JOIN products p ON p.id = v.product_id
                ORDER BY s.quantity DESC, s.revenue DESC, s.variant_id
                """,
                (rs, row) -> new BestSellerReport.Item(
                        rs.getLong("variant_id"),
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getString("sku"),
                        attributes(rs.getString("attributes")),
                        rs.getBoolean("active"),
                        rs.getLong("quantity"),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("orders")),
                period.from(), period.to(), limit);
        return new BestSellerReport(period.from(), period.to(), items);
    }

    @Transactional(readOnly = true)
    public LowStockReport lowStock() {
        int threshold = properties.lowStockThreshold();
        long total = jdbc.queryForObject("""
                SELECT count(*)
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                LEFT JOIN inventory i ON i.variant_id = v.id
                WHERE v.active AND p.active AND coalesce(i.quantity, 0) <= ?
                """, Long.class, threshold);
        List<LowStockReport.Item> items = jdbc.query("""
                SELECT v.id AS variant_id, p.id AS product_id, p.name, v.sku, v.attributes::text AS attributes,
                       coalesce(i.quantity, 0) AS quantity
                FROM product_variants v
                JOIN products p ON p.id = v.product_id
                LEFT JOIN inventory i ON i.variant_id = v.id
                WHERE v.active AND p.active AND coalesce(i.quantity, 0) <= ?
                ORDER BY quantity, p.name, v.sku
                LIMIT ?
                """,
                (rs, row) -> new LowStockReport.Item(
                        rs.getLong("variant_id"),
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getString("sku"),
                        attributes(rs.getString("attributes")),
                        rs.getInt("quantity")),
                threshold, MAX_LOW_STOCK_ITEMS);
        return new LowStockReport(threshold, total, items);
    }

    private Map<String, String> attributes(String text) {
        return json.readValue(text, ATTRIBUTES);
    }
}
