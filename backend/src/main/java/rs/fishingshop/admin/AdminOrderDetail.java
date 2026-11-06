package rs.fishingshop.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import rs.fishingshop.audit.AuditEntry;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.OrderItem;
import rs.fishingshop.orders.OrderStatus;

record AdminOrderDetail(
        Long id,
        OrderStatus status,
        Instant createdAt,
        BigDecimal total,
        Customer customer,
        List<Item> items,
        List<OrderStatus> allowedStatuses,
        List<AuditEntry> history) {

    record Customer(String name, String email, String phone, String shippingAddress) {
    }

    record Item(Long variantId, String productName, String sku, BigDecimal unitPrice, int quantity,
            BigDecimal lineTotal) {

        static Item from(OrderItem item) {
            return new Item(item.getVariantId(), item.getProductName(), item.getSku(), item.getUnitPrice(),
                    item.getQuantity(), item.lineTotal());
        }
    }

    static AdminOrderDetail from(CustomerOrder order, List<AuditEntry> history) {
        return new AdminOrderDetail(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getTotal(),
                new Customer(order.getCustomerName(), order.getCustomerEmail(), order.getCustomerPhone(),
                        order.getShippingAddress()),
                order.getItems().stream().map(Item::from).toList(),
                order.getStatus().nextStatuses(),
                history);
    }
}
