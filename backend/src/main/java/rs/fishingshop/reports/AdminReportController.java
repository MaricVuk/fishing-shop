package rs.fishingshop.reports;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/reports")
class AdminReportController {

    private static final ZoneId SHOP_ZONE = ZoneId.of("Europe/Belgrade");
    private static final int DEFAULT_DAYS = 30;

    private final SalesReportService sales;

    AdminReportController(SalesReportService sales) {
        this.sales = sales;
    }

    @GetMapping("/sales")
    @PreAuthorize("hasRole('OWNER')")
    SalesReport sales(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "DAY") ReportGranularity granularity) {
        LocalDate end = to != null ? to : LocalDate.now(SHOP_ZONE);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS - 1);
        return sales.sales(start, end, granularity);
    }
}
