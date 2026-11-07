package rs.fishingshop;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
class SalesReportApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@reports.test";
    private static final String SELLER = "prodavac@reports.test";
    private static final String WAREHOUSE = "magacin@reports.test";

    @Autowired
    MockMvc mvc;

    private final List<Long> orders = new ArrayList<>();
    private Cookie owner;

    @BeforeEach
    void setUp() throws Exception {
        employee(OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
        owner = logIn(mvc, OWNER);
    }

    @AfterEach
    void removeOrders() {
        orders.forEach(order -> jdbc.update("DELETE FROM orders WHERE id = ?", order));
        orders.clear();
    }

    @Test
    void daysFollowTheBelgradeCalendarNotUtc() throws Exception {
        order("2001-06-10T21:59:00Z", "100.00", "CONFIRMED");
        order("2001-06-10T22:30:00Z", "250.50", "CONFIRMED");

        report("from=2001-06-10&to=2001-06-12")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.granularity").value("DAY"))
                .andExpect(jsonPath("$.buckets", hasSize(3)))
                .andExpect(jsonPath("$.buckets[0].start").value("2001-06-10"))
                .andExpect(jsonPath("$.buckets[0].revenue").value("100.00"))
                .andExpect(jsonPath("$.buckets[1].start").value("2001-06-11"))
                .andExpect(jsonPath("$.buckets[1].revenue").value("250.50"))
                .andExpect(jsonPath("$.buckets[2].revenue").value("0"))
                .andExpect(jsonPath("$.buckets[2].orders").value(0));
    }

    @Test
    void aDaylightSavingDayHasTwentyThreeHours() throws Exception {
        order("2001-03-24T22:59:00Z", "1.00", "CONFIRMED");
        order("2001-03-24T23:30:00Z", "10.00", "CONFIRMED");
        order("2001-03-25T21:59:00Z", "20.00", "CONFIRMED");
        order("2001-03-25T22:30:00Z", "40.00", "CONFIRMED");

        report("from=2001-03-25&to=2001-03-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets", hasSize(1)))
                .andExpect(jsonPath("$.buckets[0].orders").value(2))
                .andExpect(jsonPath("$.buckets[0].revenue").value("30.00"));
    }

    @Test
    void cancelledOrdersAreLeftOutAndNewOrdersAreShownSeparately() throws Exception {
        order("2001-07-01T10:00:00Z", "100.00", "NEW");
        order("2001-07-01T11:00:00Z", "200.00", "CONFIRMED");
        order("2001-07-01T12:00:00Z", "400.00", "DELIVERED");
        order("2001-07-01T13:00:00Z", "800.00", "CANCELLED");

        report("from=2001-07-01&to=2001-07-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals.revenue").value("700.00"))
                .andExpect(jsonPath("$.totals.orders").value(3))
                .andExpect(jsonPath("$.totals.newRevenue").value("100.00"))
                .andExpect(jsonPath("$.totals.newOrders").value(1));
    }

    @Test
    void theRangeIncludesTheWholeLastDayAndNothingAfterIt() throws Exception {
        order("2001-08-31T21:59:59Z", "1.00", "CONFIRMED");
        order("2001-08-31T22:00:00Z", "2.00", "CONFIRMED");
        order("2001-07-31T21:59:59Z", "4.00", "CONFIRMED");

        report("from=2001-08-01&to=2001-08-31&granularity=MONTH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets", hasSize(1)))
                .andExpect(jsonPath("$.buckets[0].start").value("2001-08-01"))
                .andExpect(jsonPath("$.totals.revenue").value("1.00"));
    }

    @Test
    void weeksStartOnMondayAndAPartialFirstWeekStartsOnTheFirstCountedDay() throws Exception {
        order("2001-09-02T10:00:00Z", "5.00", "CONFIRMED");
        order("2001-09-03T10:00:00Z", "7.00", "CONFIRMED");

        report("from=2001-08-29&to=2001-09-05&granularity=WEEK")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets", hasSize(2)))
                .andExpect(jsonPath("$.buckets[0].start").value("2001-08-29"))
                .andExpect(jsonPath("$.buckets[0].revenue").value("5.00"))
                .andExpect(jsonPath("$.buckets[1].start").value("2001-09-03"))
                .andExpect(jsonPath("$.buckets[1].revenue").value("7.00"));
    }

    @Test
    void aPartialFirstMonthStartsOnTheFirstCountedDay() throws Exception {
        order("2001-10-10T10:00:00Z", "3.00", "CONFIRMED");
        order("2001-10-20T10:00:00Z", "4.00", "CONFIRMED");

        report("from=2001-10-15&to=2001-11-30&granularity=MONTH")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.buckets", hasSize(2)))
                .andExpect(jsonPath("$.buckets[0].start").value("2001-10-15"))
                .andExpect(jsonPath("$.buckets[0].revenue").value("4.00"))
                .andExpect(jsonPath("$.buckets[1].start").value("2001-11-01"));
    }

    @Test
    void withoutParametersTheReportCoversTheLastThirtyDaysInBelgrade() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Belgrade"));

        report("")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.to").value(today.toString()))
                .andExpect(jsonPath("$.from").value(today.minusDays(29).toString()))
                .andExpect(jsonPath("$.buckets", hasSize(30)));
    }

    @Test
    void badRangesAreRejected() throws Exception {
        report("from=2001-02-01&to=2001-01-01").andExpect(status().isBadRequest());
        report("from=2001-01-01&to=2002-12-31").andExpect(status().isBadRequest());
        report("from=2001-01-01&to=2002-12-31&granularity=WEEK").andExpect(status().isOk());
        report("from=2001-01-01&to=2001-01-31&granularity=YEAR").andExpect(status().isBadRequest());
        report("from=2001-13-01&to=2001-01-31").andExpect(status().isBadRequest());
    }

    @Test
    void onlyTheOwnerSeesSales() throws Exception {
        mvc.perform(get("/api/admin/reports/sales")).andExpect(status().isUnauthorized());
        for (String email : List.of(SELLER, WAREHOUSE)) {
            mvc.perform(get("/api/admin/reports/sales").cookie(logIn(mvc, email))).andExpect(status().isForbidden());
        }
    }

    private ResultActions report(String query) throws Exception {
        return mvc.perform(get("/api/admin/reports/sales?" + query).cookie(owner));
    }

    private void order(String createdAt, String total, String status) {
        orders.add(jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total, status, created_at)
                VALUES ('Izveštaj Test', 'izvestaj@example.com', 'Ulica 1', ?::numeric, ?, ?::timestamptz)
                RETURNING id
                """, Long.class, total, status, createdAt));
    }
}
