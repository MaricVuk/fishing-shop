package rs.fishingshop;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

abstract class CommittedOrderTestSupport extends CatalogTestSupport {

    protected static final String SKU_PREFIX = "COMMIT-";
    private static final String PRODUCT_NAME = "Committed Rod";

    @BeforeEach
    @AfterEach
    void removeCommittedTestData() {
        List<Long> orderIds = jdbc.queryForList("""
                SELECT DISTINCT oi.order_id FROM order_items oi
                JOIN product_variants v ON v.id = oi.variant_id
                WHERE v.sku LIKE ?
                """, Long.class, SKU_PREFIX + "%");
        for (Long orderId : orderIds) {
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", orderId);
            jdbc.update("DELETE FROM orders WHERE id = ?", orderId);
        }
        jdbc.update("""
                DELETE FROM inventory
                WHERE variant_id IN (SELECT id FROM product_variants WHERE sku LIKE ?)
                """, SKU_PREFIX + "%");
        jdbc.update("DELETE FROM product_variants WHERE sku LIKE ?", SKU_PREFIX + "%");
        jdbc.update("DELETE FROM products WHERE name = ?", PRODUCT_NAME);
    }

    protected long variantWithStock(String skuSuffix, int stock) {
        long product = jdbc.queryForList("SELECT id FROM products WHERE name = ?", Long.class, PRODUCT_NAME)
                .stream()
                .findFirst()
                .orElseGet(() -> product("stapovi", PRODUCT_NAME, "Acme", true));
        variant(product, SKU_PREFIX + skuSuffix, "4900.00", true, stock);
        return variantId(SKU_PREFIX + skuSuffix);
    }

    protected int stockOf(long variantId) {
        return jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id = ?", Integer.class, variantId);
    }

    protected int unitsOrdered(long variantId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(quantity), 0) FROM order_items WHERE variant_id = ?", Integer.class, variantId);
    }

    protected int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }
}
