package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@AutoConfigureMockMvc
class AdminStockApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@stock.test";
    private static final String WAREHOUSE = "magacin@stock.test";
    private static final String SELLER = "prodaja@stock.test";
    private static final String SKU_PREFIX = "ADMSTK-";

    @Autowired
    MockMvc mvc;

    long ownerId;
    long rod;
    long reel;

    @BeforeEach
    void setUp() {
        removeStockData();
        ownerId = employee(OWNER, "OWNER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
        employee(SELLER, "SELLER", true);
        long product = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, brand, active)
                VALUES ((SELECT id FROM categories WHERE slug = 'stapovi'), 'Admin Stock Rod', 'Acme', TRUE)
                RETURNING id
                """, Long.class);
        rod = variant(product, SKU_PREFIX + "ROD", "{\"length\":\"2.7m\"}", 5);
        reel = variant(product, SKU_PREFIX + "REEL", "{\"length\":\"3.0m\"}", 0);
    }

    @AfterEach
    void removeStockData() {
        clearAuditLog();
        jdbc.update("""
                DELETE FROM stock_movements
                WHERE variant_id IN (SELECT id FROM product_variants WHERE sku LIKE ?)
                """, SKU_PREFIX + "%");
        jdbc.update("""
                DELETE FROM inventory
                WHERE variant_id IN (SELECT id FROM product_variants WHERE sku LIKE ?)
                """, SKU_PREFIX + "%");
        jdbc.update("DELETE FROM product_variants WHERE sku LIKE ?", SKU_PREFIX + "%");
        jdbc.update("DELETE FROM products WHERE name = 'Admin Stock Rod'");
    }

    @Test
    void everyStockRouteIsClosedToAnonymousCallersAndSellers() throws Exception {
        Cookie seller = logIn(mvc, SELLER);

        for (MockHttpServletRequestBuilder request : List.of(
                get("/api/admin/stock"),
                get("/api/admin/stock/{id}", rod),
                get("/api/admin/stock/{id}/movements", rod),
                post("/api/admin/stock/{id}/receipts", rod).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"),
                post("/api/admin/stock/{id}/counts", rod).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"countedQuantity\":1,\"reason\":\"x\"}"))) {
            mvc.perform(request).andExpect(status().isUnauthorized());
            mvc.perform(request.cookie(seller))
                    .andExpect(status().isForbidden())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        }

        assertThat(stockOf(rod)).isEqualTo(5);
        assertThat(auditCount()).isZero();
    }

    @Test
    void warehouseAndOwnerSeeTheStockList() throws Exception {
        for (String email : List.of(WAREHOUSE, OWNER)) {
            mvc.perform(get("/api/admin/stock").param("q", "admin stock").cookie(logIn(mvc, email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.content[0].sku").value(SKU_PREFIX + "REEL"))
                    .andExpect(jsonPath("$.content[0].quantity").value(0))
                    .andExpect(jsonPath("$.content[1].sku").value(SKU_PREFIX + "ROD"))
                    .andExpect(jsonPath("$.content[1].quantity").value(5))
                    .andExpect(jsonPath("$.content[1].reserved").value(0))
                    .andExpect(jsonPath("$.content[1].productName").value("Admin Stock Rod"))
                    .andExpect(jsonPath("$.content[1].attributes.length").value("2.7m"))
                    .andExpect(jsonPath("$.totalElements").value(2));
        }
    }

    @Test
    void theSearchTreatsWildcardsAsText() throws Exception {
        mvc.perform(get("/api/admin/stock").param("q", "%").cookie(logIn(mvc, WAREHOUSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void theListIncludesInactiveVariantsSoTheWarehouseSeesEverything() throws Exception {
        jdbc.update("UPDATE product_variants SET active = FALSE WHERE id = ?", reel);

        mvc.perform(get("/api/admin/stock").param("q", SKU_PREFIX + "REEL").cookie(logIn(mvc, WAREHOUSE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].active").value(false));
    }

    @Test
    void aReceiptRaisesTheStockAndIsAudited() throws Exception {
        Cookie warehouse = logIn(mvc, WAREHOUSE);

        mvc.perform(post("/api/admin/stock/{id}/receipts", rod).with(csrf()).cookie(warehouse)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":7,\"reason\":\"  Dostava 42  \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.variantId").value(rod))
                .andExpect(jsonPath("$.quantity").value(12));

        assertThat(stockOf(rod)).isEqualTo(12);
        Map<String, Object> audit = jdbc.queryForMap("SELECT * FROM audit_log");
        assertThat(audit.get("actor_email")).isEqualTo(WAREHOUSE);
        assertThat(audit.get("action")).isEqualTo("STOCK_RECEIVED");
        assertThat(audit.get("entity_type")).isEqualTo("VARIANT");
        assertThat(audit.get("entity_id")).isEqualTo(String.valueOf(rod));
        assertThat(jdbc.queryForObject("SELECT old_value ->> 'quantity' FROM audit_log", String.class)).isEqualTo("5");
        assertThat(jdbc.queryForObject("SELECT new_value ->> 'quantity' FROM audit_log", String.class)).isEqualTo("12");
        assertThat(jdbc.queryForObject("SELECT new_value ->> 'reason' FROM audit_log", String.class))
                .isEqualTo("Dostava 42");
    }

    @Test
    void aCountSetsTheStockAndRecordsTheDifference() throws Exception {
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(post("/api/admin/stock/{id}/counts", rod).with(csrf()).cookie(owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedQuantity\":3,\"reason\":\"Popis, dva oštećena\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(3));

        assertThat(stockOf(rod)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT action FROM audit_log", String.class)).isEqualTo("STOCK_ADJUSTED");
        assertThat(jdbc.queryForObject("SELECT actor_id FROM audit_log", Long.class)).isEqualTo(ownerId);
        assertThat(jdbc.queryForObject("SELECT new_value ->> 'change' FROM audit_log", String.class)).isEqualTo("-2");
    }

    @Test
    void aCountThatMatchesTheStockChangesNothingAndIsNotAudited() throws Exception {
        mvc.perform(post("/api/admin/stock/{id}/counts", rod).with(csrf()).cookie(logIn(mvc, WAREHOUSE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"countedQuantity\":5,\"reason\":\"Popis\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));

        assertThat(auditCount()).isZero();
    }

    @Test
    void theHistoryShowsTheNewestMovementFirst() throws Exception {
        Cookie warehouse = logIn(mvc, WAREHOUSE);
        receive(warehouse, rod, "{\"quantity\":2}");
        mvc.perform(post("/api/admin/stock/{id}/counts", rod).with(csrf()).cookie(warehouse)
                .contentType(MediaType.APPLICATION_JSON).content("{\"countedQuantity\":1,\"reason\":\"Popis\"}"));

        mvc.perform(get("/api/admin/stock/{id}/movements", rod).cookie(warehouse))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("ADJUSTMENT"))
                .andExpect(jsonPath("$[0].quantityChange").value(-6))
                .andExpect(jsonPath("$[1].type").value("IN"));
    }

    @Test
    void rejectsInvalidInputWithFieldErrors() throws Exception {
        Cookie warehouse = logIn(mvc, WAREHOUSE);

        for (String body : List.of("{\"quantity\":0}", "{\"quantity\":100001}", "{\"quantity\":1.5}",
                "{\"quantity\":1,\"reason\":\"" + "a".repeat(201) + "\"}", "{\"quantity\":1,\"reason\":\"a\\u0000b\"}")) {
            mvc.perform(post("/api/admin/stock/{id}/receipts", rod).with(csrf()).cookie(warehouse)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        for (String body : List.of("{\"countedQuantity\":-1,\"reason\":\"Popis\"}",
                "{\"countedQuantity\":1}", "{\"countedQuantity\":1,\"reason\":\"   \"}")) {
            mvc.perform(post("/api/admin/stock/{id}/counts", rod).with(csrf()).cookie(warehouse)
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }

        assertThat(stockOf(rod)).isEqualTo(5);
        assertThat(auditCount()).isZero();
    }

    @Test
    void anUnknownVariantIsNotFound() throws Exception {
        Cookie warehouse = logIn(mvc, WAREHOUSE);

        mvc.perform(get("/api/admin/stock/{id}", Long.MAX_VALUE).cookie(warehouse)).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/stock/{id}/receipts", Long.MAX_VALUE).with(csrf()).cookie(warehouse)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
        assertThat(auditCount()).isZero();
    }

    @Test
    void aChangeWithoutACsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/admin/stock/{id}/receipts", rod).cookie(logIn(mvc, WAREHOUSE))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":3}"))
                .andExpect(status().isForbidden());

        assertThat(stockOf(rod)).isEqualTo(5);
    }

    @Test
    void whenTheAuditRowCannotBeWrittenTheStockChangeIsRolledBack() throws Exception {
        Cookie warehouse = logIn(mvc, WAREHOUSE);
        jdbc.execute("ALTER TABLE audit_log ADD CONSTRAINT test_reject_all CHECK (false) NOT VALID");
        try {
            mvc.perform(post("/api/admin/stock/{id}/receipts", rod).with(csrf()).cookie(warehouse)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":4}"))
                    .andExpect(status().isInternalServerError());
        } finally {
            jdbc.execute("ALTER TABLE audit_log DROP CONSTRAINT test_reject_all");
        }

        assertThat(stockOf(rod)).isEqualTo(5);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM stock_movements WHERE variant_id = ?", Integer.class, rod)).isZero();
    }

    @Test
    void theDatabaseRefusesToChangeOrDeleteAnAuditRow() throws Exception {
        receive(logIn(mvc, WAREHOUSE), rod, "{\"quantity\":1}");

        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET actor_email = 'someone@else.test'"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_log"))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("append-only");
        assertThat(auditCount()).isEqualTo(1);
    }

    private void receive(Cookie session, long variantId, String body) throws Exception {
        mvc.perform(post("/api/admin/stock/{id}/receipts", variantId).with(csrf()).cookie(session)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    private long variant(long productId, String sku, String attributes, int stock) {
        long id = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price, active)
                VALUES (?, ?, ?::jsonb, 4900.00, TRUE) RETURNING id
                """, Long.class, productId, sku, attributes);
        jdbc.update("INSERT INTO inventory (variant_id, quantity) VALUES (?, ?)", id, stock);
        return id;
    }

    private int stockOf(long variantId) {
        return jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id = ?", Integer.class, variantId);
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class);
    }
}
