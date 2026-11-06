package rs.fishingshop.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotNull @Valid CustomerRequest customer,
        @NotEmpty @Size(max = 50) List<@NotNull @Valid OrderItemRequest> items) {
}
