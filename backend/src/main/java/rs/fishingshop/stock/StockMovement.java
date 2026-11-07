package rs.fishingshop.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Table(name = "stock_movements")
class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "variant_id", nullable = false)
    private Long variantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockMovementType type;

    @Column(name = "quantity_change", nullable = false)
    private int quantityChange;

    @Column(name = "quantity_after", nullable = false)
    private int quantityAfter;

    @Column(name = "order_id")
    private Long orderId;

    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StockMovement() {
    }

    StockMovement(
            Long variantId, StockMovementType type, int quantityChange, int quantityAfter, Long orderId, String reason) {
        this.variantId = variantId;
        this.type = type;
        this.quantityChange = quantityChange;
        this.quantityAfter = quantityAfter;
        this.orderId = orderId;
        this.reason = reason;
        this.createdAt = Instant.now();
    }

    StockMovementEntry toEntry() {
        return new StockMovementEntry(id, type, quantityChange, quantityAfter, orderId, reason, createdAt);
    }

    Long getId() {
        return id;
    }
}
