package rs.fishingshop.catalog;

import java.util.List;

public record ProductDetail(
        Long id,
        String name,
        String brand,
        String description,
        String imageUrl,
        CategoryRef category,
        List<VariantResponse> variants) {

    public record CategoryRef(String name, String slug) {
    }

    static ProductDetail of(Product product, List<VariantResponse> variants) {
        Category category = product.getCategory();
        return new ProductDetail(
                product.getId(),
                product.getName(),
                product.getBrand(),
                product.getDescription(),
                product.getImageUrl(),
                new CategoryRef(category.getName(), category.getSlug()),
                variants);
    }
}
