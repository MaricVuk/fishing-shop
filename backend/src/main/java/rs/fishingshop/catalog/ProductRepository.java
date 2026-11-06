package rs.fishingshop.catalog;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query(value = """
            select new rs.fishingshop.catalog.ProductSummary(
                p.id, p.name, p.brand, c.slug, p.imageUrl, min(v.price), coalesce(sum(i.quantity), 0L))
            from Product p
            join p.category c
            join ProductVariant v on v.product = p
            left join Inventory i on i.variantId = v.id
            where p.active = true
              and v.active = true
              and c.slug like :category
              and (lower(p.name) like :pattern escape '\\' or lower(coalesce(p.brand, '')) like :pattern escape '\\')
            group by p.id, p.name, p.brand, c.slug, p.imageUrl, p.createdAt
            """,
            countQuery = """
            select count(distinct p.id)
            from Product p
            join p.category c
            join ProductVariant v on v.product = p
            where p.active = true
              and v.active = true
              and c.slug like :category
              and (lower(p.name) like :pattern escape '\\' or lower(coalesce(p.brand, '')) like :pattern escape '\\')
            """)
    Page<ProductSummary> search(
            @Param("category") String category, @Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select p
            from Product p
            join fetch p.category
            where p.id = :id
              and p.active = true
            """)
    Optional<Product> findActiveWithCategory(@Param("id") Long id);
}
