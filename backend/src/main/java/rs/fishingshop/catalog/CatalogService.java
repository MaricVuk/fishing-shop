package rs.fishingshop.catalog;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.api.LikePatterns;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductVariantRepository variants;

    public CatalogService(
            CategoryRepository categories, ProductRepository products, ProductVariantRepository variants) {
        this.categories = categories;
        this.products = products;
        this.variants = variants;
    }

    public List<CategoryResponse> listCategories() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    public PageResponse<ProductSummary> searchProducts(
            String category, String query, ProductSort sort, int page, int size) {
        String categoryFilter = category == null ? "%" : category;
        String pattern = LikePatterns.contains(query);
        return PageResponse.from(products.search(categoryFilter, pattern, PageRequest.of(page, size, sort.toSort())));
    }

    public ProductDetail getProduct(Long id) {
        Product product = products.findActiveWithCategory(id).orElseThrow(() -> new ProductNotFoundException(id));
        List<VariantResponse> activeVariants = variants.findActiveByProductId(id);
        if (activeVariants.isEmpty()) {
            throw new ProductNotFoundException(id);
        }
        return ProductDetail.of(product, activeVariants);
    }

    public List<VariantLine> listVariantLines(List<Long> ids) {
        return variants.findLines(ids);
    }
}
