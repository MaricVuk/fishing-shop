package rs.fishingshop.admin;

import java.math.BigDecimal;
import java.time.Instant;
import rs.fishingshop.orders.OrderStatus;
import rs.fishingshop.orders.PublicOrderNumber;

public record AdminOrderRow(
        Long id,
        String number,
        Instant createdAt,
        OrderStatus status,
        String customerName,
        String customerEmail,
        BigDecimal total,
        long itemCount) {

    public AdminOrderRow {
        number = PublicOrderNumber.format(number);
    }
}
