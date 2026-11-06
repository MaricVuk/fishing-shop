package rs.fishingshop.stock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.catalog.Inventory;
import rs.fishingshop.catalog.InventoryRepository;

@Service
public class StockService {

    static final String LOCK_TIMEOUT = "3s";

    private final InventoryRepository inventory;
    private final JdbcTemplate jdbc;

    public StockService(InventoryRepository inventory, JdbcTemplate jdbc) {
        this.inventory = inventory;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void take(Map<Long, Integer> quantities) {
        jdbc.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");

        // Rows are locked one at a time in ascending id order: every transaction then waits in the
        // same order, so two orders can never hold each other's rows. A single IN query would not
        // guarantee the order in which Postgres takes the locks.
        Map<Long, Inventory> locked = new TreeMap<>();
        List<Long> insufficient = new ArrayList<>();
        for (Map.Entry<Long, Integer> line : new TreeMap<>(quantities).entrySet()) {
            Inventory row = inventory.findForUpdate(line.getKey()).orElse(null);
            if (row == null || row.getQuantity() < line.getValue()) {
                insufficient.add(line.getKey());
            } else {
                locked.put(line.getKey(), row);
            }
        }

        if (!insufficient.isEmpty()) {
            throw new InsufficientStockException(insufficient);
        }
        locked.forEach((variantId, row) -> row.decrease(quantities.get(variantId)));
    }
}
