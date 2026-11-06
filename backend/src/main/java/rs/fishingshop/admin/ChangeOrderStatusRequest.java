package rs.fishingshop.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.InputPatterns;
import rs.fishingshop.orders.OrderStatus;

record ChangeOrderStatusRequest(
        @NotNull OrderStatus status,
        @Size(max = 200) @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT) String reason) {
}
