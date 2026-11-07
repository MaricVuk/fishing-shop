package rs.fishingshop.reports;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record BestSellerReport(LocalDate from, LocalDate to, List<Item> items) {

    public record Item(
            long variantId,
            long productId,
            String productName,
            String sku,
            Map<String, String> attributes,
            boolean active,
            long quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal revenue,
            long orders) {
    }
}
