package rs.fishingshop.orders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        Long id, OrderStatus status, BigDecimal total, Instant createdAt, List<Item> items) {

    public record Item(
            String productName, String sku, BigDecimal unitPrice, int quantity, BigDecimal lineTotal) {
    }

    static OrderResponse from(CustomerOrder order) {
        List<Item> items = order.getItems().stream()
                .map(item -> new Item(
                        item.getProductName(),
                        item.getSku(),
                        item.getUnitPrice(),
                        item.getQuantity(),
                        item.lineTotal()))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), order.getTotal(), order.getCreatedAt(), items);
    }
}
