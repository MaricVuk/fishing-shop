package rs.fishingshop.orders;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(
        @NotBlank @Size(max = 150) String name,
        @NotBlank @Size(max = 200) @Email(regexp = ".+@.+\\..+") String email,
        @Pattern(regexp = "^$|[0-9+()/ .-]{6,50}") String phone,
        @NotBlank @Size(max = 500) String shippingAddress) {
}
