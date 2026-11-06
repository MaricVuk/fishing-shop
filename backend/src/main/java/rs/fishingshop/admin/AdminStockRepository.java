package rs.fishingshop.admin;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import rs.fishingshop.catalog.ProductVariant;

interface AdminStockRepository extends Repository<ProductVariant, Long> {

    @Query(value = """
            select new rs.fishingshop.admin.StockRow(
                v.id, p.id, p.name, p.brand, v.sku, v.attributes, v.price, v.active, p.active, i.quantity)
            from ProductVariant v
            join v.product p
            left join Inventory i on i.variantId = v.id
            where lower(p.name) like :pattern escape '\\'
               or lower(coalesce(p.brand, '')) like :pattern escape '\\'
               or lower(v.sku) like :pattern escape '\\'
            order by p.name asc, v.sku asc
            """, countQuery = """
            select count(v)
            from ProductVariant v
            join v.product p
            where lower(p.name) like :pattern escape '\\'
               or lower(coalesce(p.brand, '')) like :pattern escape '\\'
               or lower(v.sku) like :pattern escape '\\'
            """)
    Page<StockRow> search(@Param("pattern") String pattern, Pageable pageable);

    @Query("""
            select new rs.fishingshop.admin.StockRow(
                v.id, p.id, p.name, p.brand, v.sku, v.attributes, v.price, v.active, p.active, i.quantity)
            from ProductVariant v
            join v.product p
            left join Inventory i on i.variantId = v.id
            where v.id = :variantId
            """)
    Optional<StockRow> findRow(@Param("variantId") Long variantId);
}
