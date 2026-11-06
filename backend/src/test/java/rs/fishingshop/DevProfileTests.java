package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@ActiveProfiles("dev")
class DevProfileTests {

    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void devSeedLoadsProductsVariantsAndStockForEveryCategory() {
        List<Integer> productsPerCategory = jdbc.queryForList("""
                SELECT count(p.id) FROM categories c
                LEFT JOIN products p ON p.category_id = c.id
                GROUP BY c.id
                """, Integer.class);
        Integer variants = jdbc.queryForObject("SELECT count(*) FROM product_variants", Integer.class);
        Integer inventoryRows = jdbc.queryForObject("SELECT count(*) FROM inventory", Integer.class);

        assertThat(productsPerCategory).hasSize(5).allMatch(count -> count > 0);
        assertThat(variants).isGreaterThan(20);
        assertThat(inventoryRows).isEqualTo(variants);
    }

    @Test
    void devSeedHasOneAccountPerRoleWithHashedPasswords() {
        List<String> roles = jdbc.queryForList("SELECT role FROM employees ORDER BY role", String.class);
        List<String> hashes = jdbc.queryForList("SELECT password_hash FROM employees", String.class);

        assertThat(roles).containsExactly("OWNER", "SELLER", "WAREHOUSE");
        assertThat(hashes).allMatch(hash -> hash.startsWith("$2a$12$"));
    }
}
