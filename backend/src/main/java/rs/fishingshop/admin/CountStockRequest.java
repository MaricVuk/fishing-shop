package rs.fishingshop.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.InputPatterns;

record CountStockRequest(
        @NotNull @Min(0) @Max(1_000_000) Integer countedQuantity,
        @NotBlank @Size(max = 200) @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT) String reason) {
}
