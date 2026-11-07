package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@AutoConfigureMockMvc
class ProductReportApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@product-reports.test";
    private static final String SELLER = "prodavac@product-reports.test";
    private static final String WAREHOUSE = "magacin@product-reports.test";
    private static final String PERIOD = "from=2002-05-01&to=2002-05-31";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    private final List<Long> orders = new ArrayList<>();
    private final List<Long> products = new ArrayList<>();
    private Cookie owner;

    @BeforeEach
    void setUp() throws Exception {
        removeTestData();
        employee(OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
        owner = logIn(mvc, OWNER);
    }

    @AfterEach
    void removeTestData() {
        orders.forEach(order -> {
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", order);
            jdbc.update("DELETE FROM orders WHERE id = ?", order);
        });
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
    void bestSellersAreRankedByUnitsThenRevenue() throws Exception {
        long rod = product("Štap Izveštaj", true);
        long cheap = variant(rod, "RPT-ROD-27", "{\"length\":\"2.7m\"}", "100.00", true, 10);
        long pricey = variant(rod, "RPT-ROD-30", "{\"length\":\"3.0m\"}", "300.00", true, 10);
        long reel = variant(product("Mašinica Izveštaj", true), "RPT-REEL", "{}", "50.00", true, 10);
        order("2002-05-10T10:00:00Z", "CONFIRMED", line(cheap, "100.00", 2), line(reel, "50.00", 5));
        order("2002-05-11T10:00:00Z", "DELIVERED", line(pricey, "300.00", 2), line(reel, "50.00", 1));

        bestSellers(PERIOD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.from").value("2002-05-01"))
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].variantId").value(reel))
                .andExpect(jsonPath("$.items[0].quantity").value(6))
                .andExpect(jsonPath("$.items[0].revenue").value("300.00"))
                .andExpect(jsonPath("$.items[0].orders").value(2))
                .andExpect(jsonPath("$.items[1].variantId").value(pricey))
                .andExpect(jsonPath("$.items[1].revenue").value("600.00"))
                .andExpect(jsonPath("$.items[1].attributes.length").value("3.0m"))
                .andExpect(jsonPath("$.items[2].variantId").value(cheap));
    }

    @Test
    void bestSellersUseThePaidPriceButTheCurrentName() throws Exception {
        long product = product("Varalica Stara", true);
        long lure = variant(product, "RPT-LURE", "{}", "80.00", true, 10);
        order("2002-05-10T10:00:00Z", "CONFIRMED", line(lure, "70.00", 3));
        jdbc.update("UPDATE products SET name = 'Varalica Nova' WHERE id = ?", product);
        jdbc.update("UPDATE product_variants SET price = 999.00 WHERE id = ?", lure);

        bestSellers(PERIOD)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].productName").value("Varalica Nova"))
                .andExpect(jsonPath("$.items[0].revenue").value("210.00"));
    }

    @Test
    void bestSellersLeaveOutCancelledOrdersAndOrdersOutsideTheBelgradePeriod() throws Exception {
        long hook = variant(product("Udice Izveštaj", true), "RPT-HOOK", "{}", "10.00", true, 10);
        order("2002-05-10T10:00:00Z", "CONFIRMED", line(hook, "10.00", 1));
        order("2002-05-10T11:00:00Z", "CANCELLED", line(hook, "10.00", 40));
        order("2002-04-30T21:59:00Z", "CONFIRMED", line(hook, "10.00", 7));
        order("2002-05-31T22:00:00Z", "CONFIRMED", line(hook, "10.00", 9));
        order("2002-04-30T22:00:00Z", "NEW", line(hook, "10.00", 2));

        bestSellers(PERIOD)
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.items[0].orders").value(2));
    }

    @Test
    void aVariantNoLongerOnSaleIsStillListedButMarked() throws Exception {
        long product = product("Najlon Izveštaj", true);
        long line = variant(product, "RPT-LINE", "{}", "20.00", false, 0);
        order("2002-05-10T10:00:00Z", "CONFIRMED", line(line, "20.00", 1));

        bestSellers(PERIOD).andExpect(jsonPath("$.items[0].active").value(false));
    }

    @Test
    void bestSellersHonourTheLimit() throws Exception {
        long product = product("Pribor Izveštaj", true);
        for (int i = 1; i <= 4; i++) {
            long variant = variant(product, "RPT-ACC-" + i, "{\"size\":\"" + i + "\"}", "5.00", true, 10);
            order("2002-05-10T10:00:00Z", "CONFIRMED", line(variant, "5.00", i));
        }

        bestSellers(PERIOD + "&limit=2")
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].quantity").value(4));
        bestSellers(PERIOD + "&limit=0").andExpect(status().isBadRequest());
        bestSellers(PERIOD + "&limit=51").andExpect(status().isBadRequest());
        bestSellers("from=2002-06-01&to=2002-05-01").andExpect(status().isBadRequest());
    }

    @Test
    void lowStockListsActiveVariantsAtOrBelowTheThresholdFewestFirst() throws Exception {
        long product = product("Zaliha Izveštaj", true);
        long empty = variant(product, "RPT-LOW-0", "{\"size\":\"0\"}", "1.00", true, 0);
        long three = variant(product, "RPT-LOW-3", "{\"size\":\"3\"}", "1.00", true, 3);
        long four = variant(product, "RPT-LOW-4", "{\"size\":\"4\"}", "1.00", true, 4);
        long offSale = variant(product, "RPT-LOW-OFF", "{\"size\":\"off\"}", "1.00", false, 0);
        long hiddenProduct = variant(product("Skriven Izveštaj", false), "RPT-LOW-HIDDEN", "{}", "1.00", true, 0);

        Map<String, Object> report = lowStock(owner);

        assertThat(report.get("threshold")).isEqualTo(3);
        List<Long> ids = lowStockIds(report);
        assertThat(ids).contains(empty, three).doesNotContain(four, offSale, hiddenProduct);
        assertThat(ids.indexOf(empty)).isLessThan(ids.indexOf(three));
        assertThat(((Number) report.get("total")).longValue()).isGreaterThanOrEqualTo(ids.size());
    }

    @Test
    void salesReportsAreForTheOwnerAndLowStockAlsoForTheWarehouse() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        Cookie warehouse = logIn(mvc, WAREHOUSE);

        mvc.perform(get("/api/admin/reports/best-sellers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reports/low-stock")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/reports/best-sellers").cookie(seller)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/best-sellers").cookie(warehouse)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/sales").cookie(warehouse)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/low-stock").cookie(seller)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/reports/low-stock").cookie(warehouse)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/low-stock").cookie(owner)).andExpect(status().isOk());
    }

    private ResultActions bestSellers(String query) throws Exception {
        return mvc.perform(get("/api/admin/reports/best-sellers?" + query).cookie(owner));
    }

    private Map<String, Object> lowStock(Cookie session) throws Exception {
        String body = mvc.perform(get("/api/admin/reports/low-stock").cookie(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readValue(body, new TypeReference<>() {
        });
    }

    @SuppressWarnings("unchecked")
    private static List<Long> lowStockIds(Map<String, Object> report) {
        return ((List<Map<String, Object>>) report.get("items")).stream()
                .map(item -> ((Number) item.get("variantId")).longValue())
                .toList();
    }

    private long product(String name, boolean active) {
        long id = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, active)
                VALUES ((SELECT id FROM categories WHERE slug = 'varalice'), ?, ?) RETURNING id
                """, Long.class, name, active);
        products.add(id);
        return id;
    }

    private long variant(long product, String sku, String attributes, String price, boolean active, int stock) {
        long id = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price, active)
                VALUES (?, ?, ?::jsonb, ?::numeric, ?) RETURNING id
                """, Long.class, product, sku, attributes, price, active);
        jdbc.update("INSERT INTO inventory (variant_id, quantity) VALUES (?, ?)", id, stock);
        return id;
    }

    private record Line(long variantId, String unitPrice, int quantity) {
    }

    private static Line line(long variantId, String unitPrice, int quantity) {
        return new Line(variantId, unitPrice, quantity);
    }

    private void order(String createdAt, String status, Line... lines) {
        long order = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total, status, created_at)
                VALUES ('Izveštaj Test', 'izvestaj@example.com', 'Ulica 1', 0, ?, ?::timestamptz) RETURNING id
                """, Long.class, status, createdAt);
        orders.add(order);
        for (Line line : lines) {
            jdbc.update("""
                    INSERT INTO order_items (order_id, variant_id, product_name, sku, unit_price, quantity)
                    SELECT ?, v.id, p.name, v.sku, ?::numeric, ?
                    FROM product_variants v JOIN products p ON p.id = v.product_id WHERE v.id = ?
                    """, order, line.unitPrice(), line.quantity(), line.variantId());
        }
    }
}
