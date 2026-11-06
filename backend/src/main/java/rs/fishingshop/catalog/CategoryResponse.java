package rs.fishingshop.catalog;

public record CategoryResponse(Long id, String name, String slug, Long parentId) {

    static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getSlug(), category.getParentId());
    }
}
