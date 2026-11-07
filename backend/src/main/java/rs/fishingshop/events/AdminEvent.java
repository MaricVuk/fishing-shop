package rs.fishingshop.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

sealed interface AdminEvent {

    AdminEventType type();

    long sourceId();

    record NewOrder(long orderId, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal total, int items,
            Instant createdAt) implements AdminEvent {

        @Override
        public AdminEventType type() {
            return AdminEventType.ORDER_PLACED;
        }

        @Override
        public long sourceId() {
            return orderId;
        }
    }

    record SoldOut(long movementId, long variantId, long orderId, String productName, String sku,
            Map<String, String> attributes) implements AdminEvent {

        @Override
        public AdminEventType type() {
            return AdminEventType.VARIANT_SOLD_OUT;
        }

        @Override
        public long sourceId() {
            return movementId;
        }
    }
}
