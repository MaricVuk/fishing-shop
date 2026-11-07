package rs.fishingshop.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalesReportService {

    static final int MAX_BUCKETS = 400;

    private final JdbcTemplate jdbc;

    SalesReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Day boundaries are local to the shop (Europe/Belgrade): a DST day is 23 or 25 hours long, and an
    // order at 00:30 local time belongs to that day even though it is the previous day in UTC.
    // A week or month that starts before 'from' is reported as starting on 'from', since only those days
    // are counted; otherwise a partial first week would look like a weak full week.
    @Transactional(readOnly = true)
    public SalesReport sales(ReportPeriod period, ReportGranularity granularity) {
        LocalDate from = period.from();
        LocalDate to = period.to();
        if (granularity.bucketCount(from, to) > MAX_BUCKETS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The report has more than " + MAX_BUCKETS + " points, choose a coarser granularity");
        }
        ReportSession.tune(jdbc);
        String unit = granularity.sqlUnit();
        List<SalesReport.Bucket> buckets = jdbc.query("""
                WITH sales AS (
                    SELECT date_trunc(?, o.created_at AT TIME ZONE 'Europe/Belgrade')::date AS bucket,
                           count(*) AS orders,
                           sum(o.total) AS revenue,
                           count(*) FILTER (WHERE o.status = 'NEW') AS new_orders,
                           coalesce(sum(o.total) FILTER (WHERE o.status = 'NEW'), 0) AS new_revenue
                    FROM orders o
                    WHERE o.status <> 'CANCELLED'
                      AND o.created_at >= ?::date::timestamp AT TIME ZONE 'Europe/Belgrade'
                      AND o.created_at < (?::date + 1)::timestamp AT TIME ZONE 'Europe/Belgrade'
                    GROUP BY 1
                )
                SELECT greatest(series.bucket::date, ?::date) AS bucket,
                       coalesce(s.orders, 0) AS orders,
                       coalesce(s.revenue, 0) AS revenue,
                       coalesce(s.new_orders, 0) AS new_orders,
                       coalesce(s.new_revenue, 0) AS new_revenue
                FROM generate_series(date_trunc(?, ?::date::timestamp), ?::date::timestamp,
                                     ('1 ' || ?)::interval) AS series (bucket)
                LEFT JOIN sales s ON s.bucket = series.bucket::date
                ORDER BY series.bucket
                """,
                (rs, row) -> new SalesReport.Bucket(
                        rs.getObject("bucket", LocalDate.class),
                        rs.getBigDecimal("revenue"),
                        rs.getLong("orders"),
                        rs.getBigDecimal("new_revenue"),
                        rs.getLong("new_orders")),
                unit, from, to, from, unit, from, to, unit);
        return new SalesReport(from, to, granularity, totals(buckets), buckets);
    }

    private static SalesReport.Totals totals(List<SalesReport.Bucket> buckets) {
        BigDecimal revenue = BigDecimal.ZERO;
        BigDecimal newRevenue = BigDecimal.ZERO;
        long orders = 0;
        long newOrders = 0;
        for (SalesReport.Bucket bucket : buckets) {
            revenue = revenue.add(bucket.revenue());
            newRevenue = newRevenue.add(bucket.newRevenue());
            orders += bucket.orders();
            newOrders += bucket.newOrders();
        }
        return new SalesReport.Totals(revenue, orders, newRevenue, newOrders);
    }
}
