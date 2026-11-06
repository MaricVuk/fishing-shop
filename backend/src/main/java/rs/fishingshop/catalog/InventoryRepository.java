package rs.fishingshop.catalog;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Inventory i where i.variantId = :variantId")
    Optional<Inventory> findForUpdate(@Param("variantId") Long variantId);

    @Modifying
    @Query(value = "INSERT INTO inventory (variant_id) VALUES (:variantId) ON CONFLICT (variant_id) DO NOTHING",
            nativeQuery = true)
    void insertIfAbsent(@Param("variantId") Long variantId);
}
