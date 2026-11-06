package rs.fishingshop.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemRequest(
        @NotNull @Positive Long variantId,
        @Min(1) @Max(OrderService.MAX_LINE_QUANTITY) int quantity) {
}
