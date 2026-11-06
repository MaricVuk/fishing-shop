package rs.fishingshop.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
class CatalogController {

    private final CatalogService catalog;

    CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/categories")
    List<CategoryResponse> categories() {
        return catalog.listCategories();
    }

    @GetMapping("/products")
    PageResponse<ProductSummary> products(
            @RequestParam(required = false) @Pattern(regexp = "[a-z0-9-]{1,120}") String category,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "name") @Pattern(regexp = "name|price_asc|price_desc|newest") String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return catalog.searchProducts(category, q, ProductSort.fromParameter(sort), page, size);
    }

    @GetMapping("/products/{id}")
    ProductDetail product(@PathVariable @Positive Long id) {
        return catalog.getProduct(id);
    }

    @GetMapping("/variants")
    List<VariantLine> variants(@RequestParam @Size(min = 1, max = 50) List<@Positive Long> ids) {
        return catalog.listVariantLines(ids);
    }
}
