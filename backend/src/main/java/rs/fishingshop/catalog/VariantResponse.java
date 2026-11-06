package rs.fishingshop.catalog;

import java.math.BigDecimal;
import java.util.Map;

public record VariantResponse(
        Long id,
        String sku,
        Map<String, String> attributes,
        BigDecimal price,
        boolean inStock) {

    public VariantResponse(
            Long id, String sku, Map<String, String> attributes, BigDecimal price, Integer quantity) {
        this(id, sku, attributes, price, quantity != null && quantity > 0);
    }
}
