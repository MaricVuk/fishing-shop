package rs.fishingshop.invoices;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("shop.invoice")
record InvoiceProperties(
        @NotBlank String sellerName,
        @NotBlank String sellerAddress,
        @NotBlank String taxId,
        @NotBlank String registrationNumber,
        @NotBlank String bankAccount,
        @NotNull @DecimalMin("0") @DecimalMax(value = "100", inclusive = false) BigDecimal vatRate) {
}
