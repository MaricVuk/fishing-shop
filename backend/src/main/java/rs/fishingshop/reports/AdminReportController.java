package rs.fishingshop.reports;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
class AdminReportController {

    private final SalesReportService sales;
    private final ProductReportService products;

    AdminReportController(SalesReportService sales, ProductReportService products) {
        this.sales = sales;
        this.products = products;
    }

    @GetMapping("/sales")
    @PreAuthorize("hasRole('OWNER')")
    SalesReport sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity) {
        return sales.sales(ReportPeriod.resolve(from, to), granularity);
    }

    @GetMapping("/best-sellers")
    @PreAuthorize("hasRole('OWNER')")
    BestSellerReport bestSellers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return products.bestSellers(ReportPeriod.resolve(from, to), limit);
    }

    @GetMapping("/low-stock")
    @PreAuthorize("hasAnyRole('OWNER', 'WAREHOUSE')")
    LowStockReport lowStock() {
        return products.lowStock();
    }
}
