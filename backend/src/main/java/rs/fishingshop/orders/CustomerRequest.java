package rs.fishingshop.orders;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.InputPatterns;

public record CustomerRequest(
        @NotBlank @Size(max = 150) @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT) String name,
        @NotBlank @Size(max = 200) @Email(regexp = ".+@.+\\..+") @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT)
                String email,
        @Pattern(regexp = InputPatterns.PHONE) String phone,
        @NotBlank @Size(max = 500) @Pattern(regexp = InputPatterns.MULTI_LINE_TEXT) String shippingAddress) {
}
