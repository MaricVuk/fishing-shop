package rs.fishingshop.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.MaxUtf8Bytes;

record ChangePasswordRequest(
        @NotBlank @Size(max = 200) String currentPassword,
        @NotNull @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @MaxUtf8Bytes(PasswordRules.MAX_BYTES) String newPassword) {
}
