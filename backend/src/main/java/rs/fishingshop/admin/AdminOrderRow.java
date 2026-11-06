package rs.fishingshop.admin;

import java.math.BigDecimal;
import java.time.Instant;
import rs.fishingshop.orders.OrderStatus;

public record AdminOrderRow(
        Long id,
        Instant createdAt,
        OrderStatus status,
        String customerName,
        String customerEmail,
        BigDecimal total,
        long itemCount) {
}
