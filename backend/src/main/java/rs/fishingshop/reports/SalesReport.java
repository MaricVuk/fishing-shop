package rs.fishingshop.reports;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReport(LocalDate from, LocalDate to, ReportGranularity granularity, Totals totals,
        List<Bucket> buckets) {

    public record Totals(
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal revenue,
            long orders,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal newRevenue,
            long newOrders) {
    }

    public record Bucket(
            LocalDate start,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal revenue,
            long orders,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal newRevenue,
            long newOrders) {
    }
}
