package rs.fishingshop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

abstract class CatalogTestSupport extends PostgresIntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    protected long product(String categorySlug, String name, String brand, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO products (category_id, name, brand, active)
                VALUES ((SELECT id FROM categories WHERE slug = ?), ?, ?, ?) RETURNING id
                """, Long.class, categorySlug, name, brand, active);
    }

    protected void variant(long productId, String sku, String price, boolean active, int stock) {
        variantWithAttributes(productId, sku, "{\"sku\":\"" + sku + "\"}", price, active, stock);
    }

    protected void variantWithAttributes(
            long productId, String sku, String attributesJson, String price, boolean active, int stock) {
        long variantId = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price, active)
                VALUES (?, ?, ?::jsonb, ?::numeric, ?) RETURNING id
                """, Long.class, productId, sku, attributesJson, price, active);
        jdbc.update("INSERT INTO inventory (variant_id, quantity) VALUES (?, ?)", variantId, stock);
    }
}
