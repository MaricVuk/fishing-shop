package rs.fishingshop.admin;

import java.math.BigDecimal;
import java.util.Map;

public record StockRow(
        Long variantId,
        Long productId,
        String productName,
        String brand,
        String sku,
        Map<String, String> attributes,
        BigDecimal price,
        boolean active,
        int quantity) {

    public StockRow(Long variantId, Long productId, String productName, String brand, String sku,
            Map<String, String> attributes, BigDecimal price, boolean variantActive, boolean productActive,
            Integer quantity) {
        this(variantId, productId, productName, brand, sku, attributes, price, variantActive && productActive,
                quantity == null ? 0 : quantity);
    }
}
