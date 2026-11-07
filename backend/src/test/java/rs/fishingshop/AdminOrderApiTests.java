package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminOrderApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@orders.test";
    private static final String SELLER = "prodavac@orders.test";
    private static final String WAREHOUSE = "magacin@orders.test";
    private static final String SKU_PREFIX = "ADMORD-";
    private static final String PRODUCT = "Admin Order Rod";

    @Autowired
    MockMvc mvc;

    long rod;
    long reel;

    @BeforeEach
    void setUp() {
        removeOrderData();
        employee(OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
        long product = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, brand, active)
                VALUES ((SELECT id FROM categories WHERE slug = 'stapovi'), ?, 'Acme', TRUE) RETURNING id
                """, Long.class, PRODUCT);
        rod = variant(product, "ROD", "{\"length\":\"2.7m\"}", "4500.00", 10);
        reel = variant(product, "REEL", "{\"length\":\"3.0m\"}", "1250.50", 10);
    }

    @AfterEach
    void removeOrderData() {
        clearAuditLog();
        List<Long> orders = testOrders();
        jdbc.update("""
                DELETE FROM stock_movements
                WHERE variant_id IN (SELECT id FROM product_variants WHERE sku LIKE ?)
                """, SKU_PREFIX + "%");
        for (Long order : orders) {
            jdbc.update("DELETE FROM background_jobs WHERE order_id = ?", order);
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", order);
            jdbc.update("DELETE FROM orders WHERE id = ?", order);
        }
        jdbc.update("""
                DELETE FROM inventory
                WHERE variant_id IN (SELECT id FROM product_variants WHERE sku LIKE ?)
                """, SKU_PREFIX + "%");
        jdbc.update("DELETE FROM product_variants WHERE sku LIKE ?", SKU_PREFIX + "%");
        jdbc.update("DELETE FROM products WHERE name = ?", PRODUCT);
    }

    @Test
    void ordersAreClosedToAnonymousCallersAndTheWarehouse() throws Exception {
        long order = placeOrder(rod, 1);
        Cookie warehouse = logIn(mvc, WAREHOUSE);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admin/orders"),
                get("/api/admin/orders/{id}", order),
                post("/api/admin/orders/{id}/status", order).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELLED\"}"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.cookie(warehouse)).andExpect(status().isForbidden());
        }

        assertThat(statusOf(order)).isEqualTo("NEW");
    }

    @Test
    void sellerSeesTheNewestOrdersFirstAndCanFilterByStatus() throws Exception {
        long first = placeOrder(rod, 1);
        long second = placeOrder(reel, 2);
        Cookie seller = logIn(mvc, SELLER);
        changeStatus(seller, first, "CONFIRMED").andExpect(status().isOk());

        mvc.perform(get("/api/admin/orders").param("q", "orders.test").cookie(seller))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains((int) second, (int) first)))
                .andExpect(jsonPath("$.content[0].customerName").value("Kupac Test"))
                .andExpect(jsonPath("$.content[0].itemCount").value(1))
                .andExpect(jsonPath("$.content[0].total").value(2501.0));

        mvc.perform(get("/api/admin/orders").param("status", "CONFIRMED").param("q", "orders.test").cookie(seller))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(first));

        mvc.perform(get("/api/admin/orders").param("q", String.valueOf(second)).cookie(seller))
                .andExpect(jsonPath("$.content[*].id", contains((int) second)));
    }

    @Test
    void sellerFindsAnOrderByTheNumberTheCustomerWasGiven() throws Exception {
        long wanted = placeOrder(rod, 1);
        placeOrder(reel, 1);
        Cookie seller = logIn(mvc, SELLER);
        String number = OrderNumbers.numberOf(jdbc, wanted);

        for (String query : List.of(number, number.toLowerCase(), number.replace("-", ""), "  " + number + " ")) {
            mvc.perform(get("/api/admin/orders").param("q", query).cookie(seller))
                    .andExpect(jsonPath("$.content", hasSize(1)))
                    .andExpect(jsonPath("$.content[0].id").value(wanted))
                    .andExpect(jsonPath("$.content[0].number").value(number));
        }
    }

    @Test
    void theDetailAndTheListShowTheCustomerFacingNumberNextToTheInternalId() throws Exception {
        long order = placeOrder(rod, 1);
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(get("/api/admin/orders/{id}", order).cookie(owner))
                .andExpect(jsonPath("$.id").value(order))
                .andExpect(jsonPath("$.number").value(OrderNumbers.numberOf(jdbc, order)));
    }

    @Test
    void theDetailShowsTheCustomerTheItemsAndWhatCanHappenNext() throws Exception {
        long order = placeOrder(rod, 2);

        mvc.perform(get("/api/admin/orders/{id}", order).cookie(logIn(mvc, OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.email").value("kupac@orders.test"))
                .andExpect(jsonPath("$.customer.shippingAddress").value("Ulica 1, Beograd"))
                .andExpect(jsonPath("$.items[0].sku").value(SKU_PREFIX + "ROD"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.total").value(9000.0))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.allowedStatuses", contains("CONFIRMED", "CANCELLED")))
                .andExpect(jsonPath("$.history", hasSize(0)));
    }

    @Test
    void anOrderMovesForwardStepByStepAndEachStepIsAudited() throws Exception {
        long order = placeOrder(rod, 1);
        Cookie seller = logIn(mvc, SELLER);

        for (String next : List.of("CONFIRMED", "SHIPPED", "DELIVERED")) {
            changeStatus(seller, order, next).andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }

        assertThat(statusOf(order)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForList("""
                SELECT old_value ->> 'status' || '>' || (new_value ->> 'status') FROM audit_log
                WHERE action = 'ORDER_STATUS_CHANGED' AND entity_type = 'ORDER' AND entity_id = ? ORDER BY id
                """, String.class, String.valueOf(order)))
                .containsExactly("NEW>CONFIRMED", "CONFIRMED>SHIPPED", "SHIPPED>DELIVERED");
        mvc.perform(get("/api/admin/orders/{id}", order).cookie(seller))
                .andExpect(jsonPath("$.allowedStatuses", hasSize(0)))
                .andExpect(jsonPath("$.history", hasSize(3)))
                .andExpect(jsonPath("$.history[0].actorEmail").value(SELLER))
                .andExpect(jsonPath("$.history[0].newValue.status").value("DELIVERED"));
    }

    @Test
    void forbiddenStepsAreAConflictAndChangeNothing() throws Exception {
        long order = placeOrder(rod, 1);
        Cookie seller = logIn(mvc, SELLER);

        changeStatus(seller, order, "DELIVERED")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.currentStatus").value("NEW"));
        changeStatus(seller, order, "NEW").andExpect(status().isConflict());
        changeStatus(seller, order, "SHIPPED").andExpect(status().isConflict());

        changeStatus(seller, order, "CONFIRMED").andExpect(status().isOk());
        changeStatus(seller, order, "SHIPPED").andExpect(status().isOk());
        changeStatus(seller, order, "CANCELLED").andExpect(status().isConflict());

        assertThat(statusOf(order)).isEqualTo("SHIPPED");
        assertThat(stockOf(rod)).isEqualTo(9);
    }

    @Test
    void cancellingPutsTheTakenUnitsBackAndRecordsIt() throws Exception {
        long order = placeOrder(List.of(rod, reel), List.of(3, 2));
        assertThat(stockOf(rod)).isEqualTo(7);
        assertThat(stockOf(reel)).isEqualTo(8);

        changeStatus(logIn(mvc, SELLER), order, "CANCELLED", "Kupac odustao")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.allowedStatuses", hasSize(0)));

        assertThat(stockOf(rod)).isEqualTo(10);
        assertThat(stockOf(reel)).isEqualTo(10);
        assertThat(jdbc.queryForList("""
                SELECT quantity_change FROM stock_movements WHERE order_id = ? AND type = 'RETURN' ORDER BY variant_id
                """, Integer.class, order)).containsExactly(3, 2);
        assertThat(jdbc.queryForObject("""
                SELECT new_value ->> 'reason' FROM audit_log WHERE entity_type = 'ORDER' AND entity_id = ?
                """, String.class, String.valueOf(order))).isEqualTo("Kupac odustao");
    }

    @Test
    void cancellingAnOrderThatNeverTookStockReturnsNothing() throws Exception {
        long legacy = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Stari Kupac', 'stari@orders.test', 'Ulica 2', 4500.00) RETURNING id
                """, Long.class);
        jdbc.update("""
                INSERT INTO order_items (order_id, variant_id, product_name, sku, unit_price, quantity)
                VALUES (?, ?, ?, ?, 4500.00, 1)
                """, legacy, rod, PRODUCT, SKU_PREFIX + "ROD");

        changeStatus(logIn(mvc, OWNER), legacy, "CANCELLED").andExpect(status().isOk());

        assertThat(stockOf(rod)).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE order_id = ?", Integer.class, legacy)).isZero();
    }

    @Test
    void tenSellersCancellingTheSameOrderReturnTheStockOnce() throws Exception {
        long order = placeOrder(rod, 4);
        Cookie seller = logIn(mvc, SELLER);
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return changeStatus(seller, order, "CANCELLED").andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(60, TimeUnit.SECONDS));
            }

            assertThat(statuses).filteredOn(code -> code == 200).hasSize(1);
            assertThat(statuses).filteredOn(code -> code == 409).hasSize(9);
        } finally {
            pool.shutdownNow();
        }

        assertThat(stockOf(rod)).isEqualTo(10);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_log WHERE entity_type = 'ORDER'", Integer.class)).isEqualTo(1);
    }

    @Test
    void theDatabaseRefusesASecondReturnForTheSameOrderAndVariant() throws Exception {
        long order = placeOrder(rod, 1);
        changeStatus(logIn(mvc, SELLER), order, "CANCELLED").andExpect(status().isOk());

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO stock_movements (variant_id, order_id, type, quantity_change, quantity_after)
                VALUES (?, ?, 'RETURN', 1, 11)
                """, rod, order)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO stock_movements (variant_id, type, quantity_change, quantity_after)
                VALUES (?, 'RETURN', 1, 11)
                """, rod)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void whenTheAuditRowCannotBeWrittenTheCancellationIsRolledBack() throws Exception {
        long order = placeOrder(rod, 2);
        Cookie seller = logIn(mvc, SELLER);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_all CHECK (false) NOT VALID");
        try {
            changeStatus(seller, order, "CANCELLED").andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_all");
        }

        assertThat(statusOf(order)).isEqualTo("NEW");
        assertThat(stockOf(rod)).isEqualTo(8);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE order_id = ? AND type = 'RETURN'", Integer.class, order))
                .isZero();
    }

    @Test
    void rejectsBadInput() throws Exception {
        long order = placeOrder(rod, 1);
        Cookie seller = logIn(mvc, SELLER);

        for (String body : List.of("{}", "{\"status\":\"LOST\"}", "{\"status\":\"CONFIRMED\",\"reason\":\"a\\u0007\"}",
                "{\"status\":\"CONFIRMED\",\"reason\":\"" + "a".repeat(201) + "\"}")) {
            mvc.perform(post("/api/admin/orders/{id}/status", order).with(csrf()).cookie(seller)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/admin/orders").param("status", "LOST").cookie(seller)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/orders/{id}", Long.MAX_VALUE).cookie(seller)).andExpect(status().isNotFound());
        changeStatus(seller, Long.MAX_VALUE, "CONFIRMED").andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/orders/{id}/status", order).cookie(seller)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isForbidden());

        assertThat(statusOf(order)).isEqualTo("NEW");
    }

    private ResultActions changeStatus(Cookie session, long order, String status) throws Exception {
        return changeStatus(session, order, status, null);
    }

    private ResultActions changeStatus(Cookie session, long order, String status, String reason) throws Exception {
        String body = reason == null
                ? "{\"status\":\"%s\"}".formatted(status)
                : "{\"status\":\"%s\",\"reason\":\"%s\"}".formatted(status, reason);
        return mvc.perform(post("/api/admin/orders/{id}/status", order).with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long placeOrder(long variantId, int quantity) throws Exception {
        return placeOrder(List.of(variantId), List.of(quantity));
    }

    private long placeOrder(List<Long> variants, List<Integer> quantities) throws Exception {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < variants.size(); i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"variantId\":%d,\"quantity\":%d}".formatted(variants.get(i), quantities.get(i)));
        }
        String body = """
                {"customer":{"name":"Kupac Test","email":"kupac@orders.test","shippingAddress":"Ulica 1, Beograd"},
                 "items":[%s]}""".formatted(items);
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return OrderNumbers.idOf(jdbc, response);
    }

    private long variant(long productId, String sku, String attributes, String price, int stock) {
        long id = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price, active)
                VALUES (?, ?, ?::jsonb, ?::numeric, TRUE) RETURNING id
                """, Long.class, productId, SKU_PREFIX + sku, attributes, price);
        jdbc.update("INSERT INTO inventory (variant_id, quantity) VALUES (?, ?)", id, stock);
        return id;
    }

    private List<Long> testOrders() {
        return jdbc.queryForList("""
                SELECT id FROM orders WHERE customer_email LIKE '%@orders.test'
                """, Long.class);
    }

    private String statusOf(long order) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, order);
    }

    private int stockOf(long variantId) {
        return jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id = ?", Integer.class, variantId);
    }
}
