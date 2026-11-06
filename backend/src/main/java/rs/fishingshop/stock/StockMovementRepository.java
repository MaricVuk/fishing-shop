package rs.fishingshop.stock;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByVariantIdOrderByIdAsc(Long variantId);

    List<StockMovement> findTop200ByVariantIdOrderByIdDesc(Long variantId);

    @Query("""
            select m.variantId as variantId, sum(m.quantityChange) as netChange
            from StockMovement m
            where m.orderId = :orderId
            group by m.variantId
            order by m.variantId
            """)
    List<OrderStockBalance> balanceForOrder(@Param("orderId") Long orderId);

    interface OrderStockBalance {
        Long getVariantId();

        Long getNetChange();
    }
}
