package rs.fishingshop.events;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
class AdminEventFeed {

    static final int MAX_REPLAY = 50;
    private static final TypeReference<Map<String, String>> ATTRIBUTES = new TypeReference<>() {
    };
    private static final String NEW_ORDERS = """
            SELECT o.id, o.total, o.created_at, (SELECT count(*) FROM order_items i WHERE i.order_id = o.id) AS items
            FROM orders o
            """;
    private static final String SOLD_OUT = """
            SELECT m.id, m.variant_id, m.order_id, p.name, v.sku, v.attributes::text AS attributes
            FROM stock_movements m
            JOIN product_variants v ON v.id = m.variant_id
            JOIN products p ON p.id = v.product_id
            WHERE m.type = 'OUT' AND m.quantity_after = 0
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final RowMapper<AdminEvent> newOrderRow = this::newOrder;
    private final RowMapper<AdminEvent> soldOutRow = this::soldOut;

    AdminEventFeed(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    EventCursor latest() {
        return jdbc.queryForObject("""
                SELECT (SELECT coalesce(max(id), 0) FROM orders), (SELECT coalesce(max(id), 0) FROM stock_movements)
                """, (rs, row) -> new EventCursor(rs.getLong(1), rs.getLong(2)));
    }

    Optional<AdminEvent> newOrder(long orderId) {
        return jdbc.query(NEW_ORDERS + " WHERE o.id = ?", newOrderRow, orderId).stream().findFirst();
    }

    Optional<AdminEvent> soldOut(long movementId) {
        return jdbc.query(SOLD_OUT + " AND m.id = ?", soldOutRow, movementId).stream().findFirst();
    }

    List<AdminEvent> since(EventCursor cursor) {
        List<AdminEvent> events = new ArrayList<>();
        events.addAll(jdbc.query(NEW_ORDERS + " WHERE o.id > ? ORDER BY o.id LIMIT ?",
                newOrderRow, cursor.orderId(), MAX_REPLAY));
        events.addAll(jdbc.query(SOLD_OUT + " AND m.id > ? ORDER BY m.id LIMIT ?",
                soldOutRow, cursor.movementId(), MAX_REPLAY));
        events.sort(Comparator.comparingLong(AdminEventFeed::orderOf).thenComparing(AdminEvent::type));
        return events;
    }

    private static long orderOf(AdminEvent event) {
        return switch (event) {
            case AdminEvent.NewOrder order -> order.orderId();
            case AdminEvent.SoldOut soldOut -> soldOut.orderId();
        };
    }

    private AdminEvent newOrder(ResultSet rs, int row) throws SQLException {
        return new AdminEvent.NewOrder(rs.getLong("id"), rs.getBigDecimal("total"), rs.getInt("items"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }

    private AdminEvent soldOut(ResultSet rs, int row) throws SQLException {
        return new AdminEvent.SoldOut(rs.getLong("id"), rs.getLong("variant_id"), rs.getLong("order_id"),
                rs.getString("name"), rs.getString("sku"), json.readValue(rs.getString("attributes"), ATTRIBUTES));
    }
}
