package rs.fishingshop.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import rs.fishingshop.auth.EmployeePrincipal;
import rs.fishingshop.catalog.PageResponse;
import rs.fishingshop.stock.StockMovementEntry;

@RestController
@RequestMapping("/api/admin/stock")
@PreAuthorize("hasAnyRole('OWNER', 'WAREHOUSE')")
class AdminStockController {

    private final AdminStockService stock;

    AdminStockController(AdminStockService stock) {
        this.stock = stock;
    }

    @GetMapping
    PageResponse<StockRow> list(
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(9999) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return stock.search(q, page, size);
    }

    @GetMapping("/{variantId}")
    StockRow row(@PathVariable Long variantId) {
        return stock.row(variantId);
    }

    @GetMapping("/{variantId}/movements")
    List<StockMovementEntry> movements(@PathVariable Long variantId) {
        return stock.movements(variantId);
    }

    @PostMapping("/{variantId}/receipts")
    StockLevel receive(@AuthenticationPrincipal EmployeePrincipal employee, @PathVariable Long variantId,
            @Valid @RequestBody ReceiveStockRequest request) {
        return stock.receive(employee.toAuditActor(), variantId, request);
    }

    @PostMapping("/{variantId}/counts")
    StockLevel count(@AuthenticationPrincipal EmployeePrincipal employee, @PathVariable Long variantId,
            @Valid @RequestBody CountStockRequest request) {
        return stock.count(employee.toAuditActor(), variantId, request);
    }
}
