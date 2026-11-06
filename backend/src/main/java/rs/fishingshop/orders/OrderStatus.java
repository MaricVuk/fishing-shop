package rs.fishingshop.orders;

import java.util.List;

public enum OrderStatus {
    NEW,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED;

    public List<OrderStatus> nextStatuses() {
        return switch (this) {
            case NEW -> List.of(CONFIRMED, CANCELLED);
            case CONFIRMED -> List.of(SHIPPED, CANCELLED);
            case SHIPPED -> List.of(DELIVERED);
            case DELIVERED, CANCELLED -> List.of();
        };
    }

    public boolean canBecome(OrderStatus next) {
        return nextStatuses().contains(next);
    }
}
