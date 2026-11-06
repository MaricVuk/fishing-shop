package rs.fishingshop.catalog;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    @Query("""
            select new rs.fishingshop.catalog.VariantResponse(
                v.id, v.sku, v.attributes, v.price, coalesce(i.quantity, 0))
            from ProductVariant v
            left join Inventory i on i.variantId = v.id
            where v.product.id = :productId
              and v.active = true
            order by v.price asc, v.id asc
            """)
    List<VariantResponse> findActiveByProductId(@Param("productId") Long productId);
}
