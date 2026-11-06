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
        int quantity,
        int reserved) {

    public StockRow(Long variantId, Long productId, String productName, String brand, String sku,
            Map<String, String> attributes, BigDecimal price, boolean variantActive, boolean productActive,
            Integer quantity, Number reserved) {
        this(variantId, productId, productName, brand, sku, attributes, price, variantActive && productActive,
                quantity == null ? 0 : quantity, reserved == null ? 0 : reserved.intValue());
    }
}
