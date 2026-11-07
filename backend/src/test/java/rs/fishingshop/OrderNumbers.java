package rs.fishingshop;

import com.jayway.jsonpath.JsonPath;
import org.springframework.jdbc.core.JdbcTemplate;

final class OrderNumbers {

    private OrderNumbers() {
    }

    static String tokenOf(JdbcTemplate jdbc, long id) {
        return jdbc.queryForObject("SELECT public_token FROM orders WHERE id = ?", String.class, id);
    }

    static String numberOf(JdbcTemplate jdbc, long id) {
        String token = tokenOf(jdbc, id);
        return token.substring(0, 4) + "-" + token.substring(4, 8) + "-" + token.substring(8);
    }

    static long idOf(JdbcTemplate jdbc, String orderResponseJson) {
        String number = JsonPath.read(orderResponseJson, "$.number");
        return jdbc.queryForObject("SELECT id FROM orders WHERE public_token = ?", Long.class,
                number.replace("-", ""));
    }
}
