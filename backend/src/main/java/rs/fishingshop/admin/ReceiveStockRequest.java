package rs.fishingshop.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.InputPatterns;

record ReceiveStockRequest(
        @NotNull @Min(1) @Max(100_000) Integer quantity,
        @Size(max = 200) @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT) String reason) {
}
