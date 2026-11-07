package rs.fishingshop.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import rs.fishingshop.invoices.StoredInvoice;
import rs.fishingshop.orders.OrderStatus;

@RestController
@RequestMapping("/api/admin/orders")
@PreAuthorize("hasAnyRole('OWNER', 'SELLER')")
class AdminOrderController {

    private final AdminOrderService orders;

    AdminOrderController(AdminOrderService orders) {
        this.orders = orders;
    }

    @GetMapping
    PageResponse<AdminOrderRow> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @Size(max = 100) String q,
            @RequestParam(defaultValue = "0") @Min(0) @Max(9999) int page,
            @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
        return orders.search(status, q, page, size);
    }

    @GetMapping("/{id}")
    AdminOrderDetail detail(@PathVariable Long id) {
        return orders.detail(id);
    }

    @GetMapping("/{id}/invoice")
    ResponseEntity<byte[]> invoice(@PathVariable Long id) {
        StoredInvoice invoice = orders.invoice(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(invoice.number() + ".pdf").build().toString())
                .cacheControl(CacheControl.noStore())
                .body(invoice.pdf());
    }

    @PostMapping("/{id}/status")
    AdminOrderDetail changeStatus(@AuthenticationPrincipal EmployeePrincipal employee, @PathVariable Long id,
            @Valid @RequestBody ChangeOrderStatusRequest request) {
        return orders.changeStatus(employee.toAuditActor(), id, request);
    }
}
