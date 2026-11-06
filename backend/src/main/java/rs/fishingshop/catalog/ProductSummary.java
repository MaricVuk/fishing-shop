package rs.fishingshop.catalog;

import java.math.BigDecimal;

public record ProductSummary(
        Long id,
        String name,
        String brand,
        String categorySlug,
        String imageUrl,
        BigDecimal minPrice,
        boolean inStock) {

    public ProductSummary(
            Long id,
            String name,
            String brand,
            String categorySlug,
            String imageUrl,
            BigDecimal minPrice,
            Long totalStock) {
        this(id, name, brand, categorySlug, imageUrl, minPrice, totalStock != null && totalStock > 0);
    }
}
