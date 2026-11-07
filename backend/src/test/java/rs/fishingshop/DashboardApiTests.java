package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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

@AutoConfigureMockMvc
class DashboardApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@dashboard.test";
    private static final String SELLER = "prodavac@dashboard.test";
    private static final String WAREHOUSE = "magacin@dashboard.test";

    @Autowired
    MockMvc mvc;

    private final List<Long> orders = new ArrayList<>();
    private final List<Long> products = new ArrayList<>();

    @BeforeEach
    void setUp() {
        employee(OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
    }

    @AfterEach
    void removeTestData() {
        orders.forEach(order -> jdbc.update("DELETE FROM orders WHERE id = ?", order));
        products.forEach(product -> {
            jdbc.update("DELETE FROM inventory WHERE variant_id IN (SELECT id FROM product_variants WHERE product_id = ?)",
                    product);
            jdbc.update("DELETE FROM product_variants WHERE product_id = ?", product);
            jdbc.update("DELETE FROM products WHERE id = ?", product);
        });
        orders.clear();
        products.clear();
    }

    @Test
    void theOwnerSeesPendingOrdersSalesForTheLastSevenDaysAndLowStock() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        String before = body(owner);

        order("NEW", "100.00", "now() - interval '3 hours'");
        order("CONFIRMED", "250.50", "now()");
        order("CANCELLED", "999.00", "now()");
        lowStockVariant("DSH-OWNER", 0);
        String after = body(owner);

        assertThat(count(after, "$.pendingOrders.count") - count(before, "$.pendingOrders.count")).isEqualTo(1);
        assertThat(count(after, "$.lowStock.total") - count(before, "$.lowStock.total")).isEqualTo(1);
        assertThat((Integer) JsonPath.read(after, "$.lowStock.threshold")).isEqualTo(3);
        assertThat(new java.math.BigDecimal(JsonPath.<String>read(after, "$.sales.buckets[6].revenue"))
                .subtract(new java.math.BigDecimal(JsonPath.<String>read(before, "$.sales.buckets[6].revenue"))))
                .isEqualByComparingTo("350.50");
        assertThat(JsonPath.<String>read(after, "$.pendingOrders.oldestSince")).isNotNull();
    }

    @Test
    void salesCoverSevenBelgradeDaysEndingToday() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Belgrade"));

        mvc.perform(get("/api/admin/dashboard").cookie(logIn(mvc, OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sales.granularity").value("DAY"))
                .andExpect(jsonPath("$.sales.from").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.sales.to").value(today.toString()))
                .andExpect(jsonPath("$.sales.buckets", hasSize(7)));
    }

    @Test
    void aSellerSeesOnlyPendingOrdersNeverMoneyOrStock() throws Exception {
        mvc.perform(get("/api/admin/dashboard").cookie(logIn(mvc, SELLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingOrders.count").isNumber())
                .andExpect(jsonPath("$.sales").doesNotExist())
                .andExpect(jsonPath("$.lowStock").doesNotExist());
    }

    @Test
    void theWarehouseSeesOnlyLowStockNeverOrdersOrMoney() throws Exception {
        mvc.perform(get("/api/admin/dashboard").cookie(logIn(mvc, WAREHOUSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lowStock.threshold").value(3))
                .andExpect(jsonPath("$.pendingOrders").doesNotExist())
                .andExpect(jsonPath("$.sales").doesNotExist());
    }

    @Test
    void theCountIsExactAndTheDateAppearsOnlyWhenSomethingIsWaiting() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        long waiting = jdbc.queryForObject("SELECT count(*) FROM orders WHERE status = 'NEW'", Long.class);

        String body = body(seller);

        assertThat(count(body, "$.pendingOrders.count")).isEqualTo(waiting);
        assertThat(body.contains("oldestSince")).isEqualTo(waiting > 0);

        order("NEW", "10.00", "now()");
        order("SHIPPED", "10.00", "now()");

        String withOne = body(seller);
        assertThat(count(withOne, "$.pendingOrders.count")).isEqualTo(waiting + 1);
        assertThat(withOne).contains("oldestSince");
    }

    @Test
    void anonymousVisitorsGetNothingAndTheAnswerIsNeverCached() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/dashboard").cookie(logIn(mvc, OWNER)))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    private String body(Cookie session) throws Exception {
        return mvc.perform(get("/api/admin/dashboard").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long count(String body, String path) {
        return ((Number) JsonPath.read(body, path)).longValue();
    }

    private void order(String status, String total, String createdAtSql) {
        orders.add(jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total, status, created_at)
                VALUES ('Pregled Test', 'dashboard@example.com', 'Ulica 1', ?::numeric, ?, %s) RETURNING id
                """.formatted(createdAtSql), Long.class, total, status));
    }

    private void lowStockVariant(String sku, int stock) {
        long product = jdbc.queryForObject("""
                INSERT INTO products (category_id, name)
                VALUES ((SELECT id FROM categories WHERE slug = 'varalice'), 'Pregled Proizvod') RETURNING id
                """, Long.class);
        products.add(product);
        long variant = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price)
                VALUES (?, ?, '{}'::jsonb, 10.00) RETURNING id
                """, Long.class, product, sku);
        jdbc.update("INSERT INTO inventory (variant_id, quantity) VALUES (?, ?)", variant, stock);
    }
}
