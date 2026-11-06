package rs.fishingshop.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.MaxUtf8Bytes;
import rs.fishingshop.auth.PasswordRules;

record ResetPasswordRequest(
        @NotNull @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @MaxUtf8Bytes(PasswordRules.MAX_BYTES) String password) {
}
