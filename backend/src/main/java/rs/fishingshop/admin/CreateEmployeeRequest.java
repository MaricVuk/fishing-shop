package rs.fishingshop.admin;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import rs.fishingshop.api.InputPatterns;
import rs.fishingshop.api.MaxUtf8Bytes;
import rs.fishingshop.auth.PasswordRules;
import rs.fishingshop.employees.EmployeeRole;

record CreateEmployeeRequest(
        @NotBlank @Size(max = 100) @Email(regexp = ".+@.+\\..+") @Pattern(regexp = "[\\x21-\\x7e]+")
        String email,
        @NotBlank @Size(max = 150) @Pattern(regexp = InputPatterns.SINGLE_LINE_TEXT) String fullName,
        @NotNull EmployeeRole role,
        @NotNull @Size(min = PasswordRules.MIN_LENGTH, max = PasswordRules.MAX_LENGTH)
        @MaxUtf8Bytes(PasswordRules.MAX_BYTES) String password) {
}
