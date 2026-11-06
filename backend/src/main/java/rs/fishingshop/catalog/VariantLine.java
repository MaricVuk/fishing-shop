package rs.fishingshop.catalog;

import java.math.BigDecimal;
import java.util.Map;

public record VariantLine(
        Long variantId,
        Long productId,
        String productName,
        String brand,
        String sku,
        Map<String, String> attributes,
        BigDecimal price,
        String imageUrl,
        boolean inStock) {

    public VariantLine(
            Long variantId,
            Long productId,
            String productName,
            String brand,
            String sku,
            Map<String, String> attributes,
            BigDecimal price,
            String imageUrl,
            Integer quantity) {
        this(variantId, productId, productName, brand, sku, attributes, price, imageUrl, quantity != null && quantity > 0);
    }
}
