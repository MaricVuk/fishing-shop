package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ShopApplicationTests extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void contextLoadsAndMigrationsApplied() {
        Integer categories = jdbc.queryForObject("SELECT count(*) FROM categories", Integer.class);
        assertThat(categories).isGreaterThan(0);
    }
}
