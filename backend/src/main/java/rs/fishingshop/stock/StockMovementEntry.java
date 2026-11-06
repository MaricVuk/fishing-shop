package rs.fishingshop.stock;

import java.time.Instant;

public record StockMovementEntry(
        Long id,
        StockMovementType type,
        int quantityChange,
        int quantityAfter,
        Long orderId,
        String reason,
        Instant createdAt) {
}
