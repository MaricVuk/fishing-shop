package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class SchemaConstraintsTests extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void orderWithItemsCannotBeDeleted() {
        long variantId = insertVariant("SKU-ORDER", "{\"length\":\"2.7m\"}");
        long orderId = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Test Kupac', 'kupac@example.com', 'Ulica 1', 100.00) RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO order_items (order_id, variant_id, product_name, sku, unit_price, quantity)
                VALUES (?, ?, 'Stap', 'SKU-ORDER', 100.00, 1)
                """, orderId, variantId);

        assertThatThrownBy(() -> jdbc.update("DELETE FROM orders WHERE id = ?", orderId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void variantAttributesMustBeJsonObject() {
        long productId = insertProduct();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product_variants (product_id, sku, attributes, price)
                VALUES (?, 'SKU-ARRAY', '[]'::jsonb, 10.00)
                """, productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productCannotHaveTwoVariantsWithSameAttributes() {
        long productId = insertProduct();
        jdbc.update("""
                INSERT INTO product_variants (product_id, sku, attributes, price)
                VALUES (?, 'SKU-A', '{"length":"2.7m"}'::jsonb, 10.00)
                """, productId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO product_variants (product_id, sku, attributes, price)
                VALUES (?, 'SKU-B', '{"length":"2.7m"}'::jsonb, 10.00)
                """, productId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void foreignKeyIndexesExist() {
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'", String.class);

        assertThat(indexes).contains("idx_categories_parent", "idx_order_items_variant");
    }

    private long insertProduct() {
        long categoryId = jdbc.queryForObject("""
                INSERT INTO categories (name, slug) VALUES ('Test kategorija', 'test-kategorija') RETURNING id
                """, Long.class);
        return jdbc.queryForObject("""
                INSERT INTO products (category_id, name) VALUES (?, 'Test proizvod') RETURNING id
                """, Long.class, categoryId);
    }

    private long insertVariant(String sku, String attributesJson) {
        long productId = insertProduct();
        return jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price)
                VALUES (?, ?, ?::jsonb, 100.00) RETURNING id
                """, Long.class, productId, sku, attributesJson);
    }
}
