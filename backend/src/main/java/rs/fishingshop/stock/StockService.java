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
import rs.fishingshop.catalog.ProductVariantRepository;

@Service
public class StockService {

    static final String LOCK_TIMEOUT = "3s";
    static final int MAX_DELIVERY = 100_000;
    static final int MAX_REASON_LENGTH = 200;

    private final InventoryRepository inventory;
    private final StockMovementRepository movements;
    private final ProductVariantRepository variants;
    private final JdbcTemplate jdbc;

    public StockService(
            InventoryRepository inventory,
            StockMovementRepository movements,
            ProductVariantRepository variants,
            JdbcTemplate jdbc) {
        this.inventory = inventory;
        this.movements = movements;
        this.variants = variants;
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void takeForOrder(Long orderId, Map<Long, Integer> quantities) {
        limitLockWait();

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
        locked.forEach((variantId, row) -> {
            int quantity = quantities.get(variantId);
            row.decrease(quantity);
            movements.save(new StockMovement(
                    variantId, StockMovementType.OUT, -quantity, row.getQuantity(), orderId, null));
        });
    }

    @Transactional
    public void receive(Long variantId, int quantity, String reason) {
        if (quantity <= 0 || quantity > MAX_DELIVERY) {
            throw new IllegalArgumentException("Delivered quantity must be between 1 and " + MAX_DELIVERY);
        }
        String note = optionalReason(reason);
        Inventory row = lockOrCreate(variantId);
        row.increase(quantity);
        movements.save(new StockMovement(
                variantId, StockMovementType.IN, quantity, row.getQuantity(), null, note));
    }

    @Transactional
    public void adjust(Long variantId, int countedQuantity, String reason) {
        if (countedQuantity < 0) {
            throw new IllegalArgumentException("Counted quantity cannot be negative");
        }
        String note = optionalReason(reason);
        if (note == null) {
            throw new IllegalArgumentException("An adjustment needs a reason");
        }
        Inventory row = lockOrCreate(variantId);
        int change = countedQuantity - row.getQuantity();
        if (change == 0) {
            return;
        }
        row.setCounted(countedQuantity);
        movements.save(new StockMovement(
                variantId, StockMovementType.ADJUSTMENT, change, row.getQuantity(), null, note));
    }

    @Transactional(readOnly = true)
    public List<StockMovementEntry> history(Long variantId) {
        return movements.findByVariantIdOrderByIdAsc(variantId).stream()
                .map(StockMovement::toEntry)
                .toList();
    }

    private Inventory lockOrCreate(Long variantId) {
        if (!variants.existsById(variantId)) {
            throw new IllegalArgumentException("Unknown variant " + variantId);
        }
        limitLockWait();
        inventory.insertIfAbsent(variantId);
        return inventory.findForUpdate(variantId)
                .orElseThrow(() -> new IllegalStateException("Stock row for variant " + variantId + " is missing"));
    }

    private void limitLockWait() {
        jdbc.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
    }

    private static String optionalReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.length() > MAX_REASON_LENGTH) {
            throw new IllegalArgumentException("Reason cannot be longer than " + MAX_REASON_LENGTH);
        }
        return trimmed;
    }
}
