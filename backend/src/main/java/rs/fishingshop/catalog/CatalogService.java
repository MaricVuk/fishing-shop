package rs.fishingshop.catalog;

import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CatalogService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    public List<CategoryResponse> listCategories() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    public PageResponse<ProductSummary> searchProducts(
            String category, String query, ProductSort sort, int page, int size) {
        String categoryFilter = category == null ? "%" : category;
        String pattern = "%" + escapeLike(query == null ? "" : query.trim().toLowerCase(Locale.ROOT)) + "%";
        return PageResponse.from(products.search(categoryFilter, pattern, PageRequest.of(page, size, sort.toSort())));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
