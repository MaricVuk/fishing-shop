package rs.fishingshop.stock;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByVariantIdOrderByIdAsc(Long variantId);
}
