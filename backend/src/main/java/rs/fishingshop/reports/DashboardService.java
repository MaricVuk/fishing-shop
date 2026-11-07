package rs.fishingshop.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.employees.EmployeeRole;

@Service
public class DashboardService {

    static final int SALES_DAYS = 7;
    private static final Set<EmployeeRole> SEES_ORDERS = Set.of(EmployeeRole.OWNER, EmployeeRole.SELLER);
    private static final Set<EmployeeRole> SEES_SALES = Set.of(EmployeeRole.OWNER);
    private static final Set<EmployeeRole> SEES_STOCK = Set.of(EmployeeRole.OWNER, EmployeeRole.WAREHOUSE);

    private final JdbcTemplate jdbc;
    private final SalesReportService sales;
    private final ProductReportService products;

    DashboardService(JdbcTemplate jdbc, SalesReportService sales, ProductReportService products) {
        this.jdbc = jdbc;
        this.sales = sales;
        this.products = products;
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(EmployeeRole role) {
        return new Dashboard(
                SEES_ORDERS.contains(role) ? pendingOrders() : null,
                SEES_SALES.contains(role) ? lastDays() : null,
                SEES_STOCK.contains(role) ? products.lowStockSummary() : null);
    }

    private Dashboard.PendingOrders pendingOrders() {
        return jdbc.queryForObject("SELECT count(*), min(created_at) FROM orders WHERE status = 'NEW'",
                (rs, row) -> {
                    OffsetDateTime oldest = rs.getObject(2, OffsetDateTime.class);
                    Instant since = oldest == null ? null : oldest.toInstant();
                    return new Dashboard.PendingOrders(rs.getLong(1), since);
                });
    }

    private SalesReport lastDays() {
        LocalDate today = LocalDate.now(ReportPeriod.SHOP_ZONE);
        return sales.sales(new ReportPeriod(today.minusDays(SALES_DAYS - 1), today), ReportGranularity.DAY);
    }
}
