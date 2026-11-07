package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import rs.fishingshop.jobs.JobWorker;

@SpringBootTest
@AutoConfigureMockMvc
class RestrictedRoleFlowTests {

    private static final String PASSWORD = "correct-horse-battery";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", RolesDatabase.postgres::getJdbcUrl);
        registry.add("spring.datasource.username", () -> RolesDatabase.APP);
        registry.add("spring.datasource.password", () -> RolesDatabase.APP_PASSWORD);
        registry.add("spring.flyway.user", () -> RolesDatabase.MIGRATOR);
        registry.add("spring.flyway.password", () -> RolesDatabase.MIGRATOR_PASSWORD);
        Mailpit.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JobWorker worker;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void theWholeShopWorksWhileConnectedAsTheRestrictedRole() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo("shop_app");
        long variant = seedVariant(10);
        long owner = seedEmployee("vlasnik@roles.test", "OWNER");
        long seller = seedEmployee("prodavac@roles.test", "SELLER");
        Mailpit.clear();

        String created = mvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customer":{"name":"Ana Anić","email":"ana@roles.test","phone":"0601234567",
                                "shippingAddress":"Ulica 1, Beograd"},"items":[{"variantId":%d,"quantity":3}]}
                                """.formatted(variant)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long order = OrderNumbers.idOf(jdbc, created);
        assertThat(stock(variant)).isEqualTo(7);

        Cookie ownerSession = logIn("vlasnik@roles.test");
        Cookie sellerSession = logIn("prodavac@roles.test");

        changeStatus(ownerSession, order, "CONFIRMED");
        worker.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, order))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM background_jobs WHERE order_id = ? AND status <> 'DONE'", Integer.class, order))
                .isZero();
        assertThat(Mailpit.messages()).contains("Potvrda porudžbine").contains("Račun");

        changeStatus(ownerSession, order, "CANCELLED");
        assertThat(stock(variant)).isEqualTo(10);

        mvc.perform(post("/api/admin/employees/{id}/deactivate", seller).cookie(ownerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class, "prodavac@roles.test")).isZero();
        assertThat(sellerSession).isNotNull();

        mvc.perform(post("/api/auth/logout").cookie(ownerSession).with(csrf()))
                .andExpect(status().is2xxSuccessful());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?",
                Integer.class, "vlasnik@roles.test")).isZero();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE actor_id = ?", Integer.class, owner))
                .isGreaterThanOrEqualTo(2);
    }

    private void changeStatus(Cookie session, long order, String status) throws Exception {
        mvc.perform(post("/api/admin/orders/{id}/status", order).cookie(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"))
                .andExpect(status().isOk());
    }

    private Cookie logIn(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("email", email).param("password", PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getCookie("SESSION");
    }

    private int stock(long variant) {
        return jdbc.queryForObject("SELECT quantity FROM inventory WHERE variant_id = ?", Integer.class, variant);
    }

    private long seedVariant(int stock) throws SQLException {
        try (Connection admin = RolesDatabase.admin(); Statement s = admin.createStatement()) {
            var rs = s.executeQuery("""
                    WITH p AS (
                        INSERT INTO products (category_id, name, brand, active)
                        VALUES ((SELECT id FROM categories WHERE slug = 'stapovi'), 'Role Rod', 'Acme', TRUE)
                        RETURNING id),
                    v AS (
                        INSERT INTO product_variants (product_id, sku, attributes, price, active)
                        SELECT id, 'ROLE-1', '{"length":"2.4m"}'::jsonb, 4900.00, TRUE FROM p RETURNING id),
                    i AS (INSERT INTO inventory (variant_id, quantity) SELECT id, %d FROM v)
                    SELECT id FROM v
                    """.formatted(stock));
            rs.next();
            return rs.getLong(1);
        }
    }

    private long seedEmployee(String email, String role) throws SQLException {
        try (Connection admin = RolesDatabase.admin();
             var ps = admin.prepareStatement("""
                     INSERT INTO employees (email, full_name, password_hash, role)
                     VALUES (?, ?, ?, ?) RETURNING id
                     """)) {
            ps.setString(1, email);
            ps.setString(2, "Test " + role);
            ps.setString(3, passwordEncoder.encode(PASSWORD));
            ps.setString(4, role);
            var rs = ps.executeQuery();
            rs.next();
            return rs.getLong(1);
        }
    }
}
