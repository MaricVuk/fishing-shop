package rs.fishingshop.reports;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Dashboard(PendingOrders pendingOrders, SalesReport sales, LowStockSummary lowStock) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendingOrders(long count, Instant oldestSince) {
    }

    public record LowStockSummary(int threshold, long total) {
    }
}
