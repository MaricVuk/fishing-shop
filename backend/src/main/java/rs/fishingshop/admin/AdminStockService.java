package rs.fishingshop.admin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.api.LikePatterns;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditActor;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.audit.AuditService;
import rs.fishingshop.catalog.PageResponse;
import rs.fishingshop.stock.StockChange;
import rs.fishingshop.stock.StockMovementEntry;
import rs.fishingshop.stock.StockService;
import rs.fishingshop.stock.StockVariantNotFoundException;

@Service
class AdminStockService {

    private final AdminStockRepository rows;
    private final StockService stock;
    private final AuditService audit;

    AdminStockService(AdminStockRepository rows, StockService stock, AuditService audit) {
        this.rows = rows;
        this.stock = stock;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    PageResponse<StockRow> search(String query, int page, int size) {
        return PageResponse.from(rows.search(LikePatterns.contains(query), PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    StockRow row(Long variantId) {
        return rows.findRow(variantId).orElseThrow(() -> new StockVariantNotFoundException(variantId));
    }

    @Transactional(readOnly = true)
    List<StockMovementEntry> movements(Long variantId) {
        row(variantId);
        return stock.recentHistory(variantId);
    }

    @Transactional
    StockLevel receive(AuditActor actor, Long variantId, ReceiveStockRequest request) {
        String reason = trimToNull(request.reason());
        StockChange change = stock.receive(variantId, request.quantity(), reason);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("quantity", change.after());
        after.put("received", request.quantity());
        after.put("reason", reason);
        audit.record(actor, AuditAction.STOCK_RECEIVED, AuditEntityType.VARIANT, variantId,
                Map.of("quantity", change.before()), after);
        return new StockLevel(variantId, change.after());
    }

    @Transactional
    StockLevel count(AuditActor actor, Long variantId, CountStockRequest request) {
        String reason = request.reason().trim();
        StockChange change = stock.adjust(variantId, request.countedQuantity(), reason);
        if (change.changed()) {
            audit.record(actor, AuditAction.STOCK_ADJUSTED, AuditEntityType.VARIANT, variantId,
                    Map.of("quantity", change.before()),
                    Map.of("quantity", change.after(), "change", change.after() - change.before(), "reason", reason));
        }
        return new StockLevel(variantId, change.after());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
