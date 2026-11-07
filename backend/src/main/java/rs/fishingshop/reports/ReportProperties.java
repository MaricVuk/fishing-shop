package rs.fishingshop.reports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("shop.reports")
record ReportProperties(@Min(0) @Max(1_000_000) int lowStockThreshold) {
}
