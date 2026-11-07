package rs.fishingshop.admin;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.api.LikePatterns;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditActor;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.audit.AuditService;
import rs.fishingshop.catalog.PageResponse;
import rs.fishingshop.invoices.InvoiceService;
import rs.fishingshop.invoices.StoredInvoice;
import rs.fishingshop.jobs.JobQueue;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.CustomerOrderRepository;
import rs.fishingshop.orders.OrderStatus;
import rs.fishingshop.orders.PublicOrderNumber;
import rs.fishingshop.stock.StockService;

@Service
class AdminOrderService {

    private static final String LOCK_TIMEOUT = "3s";

    private final AdminOrderRepository rows;
    private final CustomerOrderRepository orders;
    private final StockService stock;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final JobQueue jobs;
    private final InvoiceService invoices;

    AdminOrderService(AdminOrderRepository rows, CustomerOrderRepository orders, StockService stock,
            AuditService audit, JdbcTemplate jdbc, JobQueue jobs, InvoiceService invoices) {
        this.rows = rows;
        this.orders = orders;
        this.stock = stock;
        this.audit = audit;
        this.jdbc = jdbc;
        this.jobs = jobs;
        this.invoices = invoices;
    }

    @Transactional(readOnly = true)
    PageResponse<AdminOrderRow> search(OrderStatus status, String query, int page, int size) {
        String text = query == null ? "" : query.trim();
        Long orderId = text.matches("\\d{1,18}") ? Long.valueOf(text) : null;
        String token = PublicOrderNumber.tokenFrom(text);
        String pattern = orderId != null || token != null ? "" : LikePatterns.contains(text);
        return PageResponse.from(rows.search(status, orderId, token, pattern, PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    AdminOrderDetail detail(Long id) {
        CustomerOrder order = orders.findWithItems(id).orElseThrow(() -> new OrderNotFoundException(id));
        return AdminOrderDetail.from(order, invoices.summaryFor(id), audit.history(AuditEntityType.ORDER, id));
    }

    @Transactional(readOnly = true)
    StoredInvoice invoice(Long id) {
        return invoices.findForOrder(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    @Transactional
    AdminOrderDetail changeStatus(AuditActor actor, Long id, ChangeOrderStatusRequest request) {
        jdbc.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
        CustomerOrder order = orders.findForUpdate(id).orElseThrow(() -> new OrderNotFoundException(id));
        OrderStatus current = order.getStatus();
        OrderStatus next = request.status();
        if (!current.canBecome(next)) {
            throw new OrderStatusConflictException(current, next);
        }

        Map<Long, Integer> returned = next == OrderStatus.CANCELLED ? stock.returnForOrder(id) : Map.of();
        order.moveTo(next);
        if (next == OrderStatus.CONFIRMED) {
            jobs.enqueueForOrder(JobType.ORDER_INVOICE, id);
        }

        Map<String, Object> after = new LinkedHashMap<>();
        after.put("status", next);
        after.put("reason", trimToNull(request.reason()));
        if (next == OrderStatus.CANCELLED) {
            after.put("returned", returned.entrySet().stream()
                    .map(entry -> Map.of("variantId", entry.getKey(), "quantity", entry.getValue()))
                    .toList());
        }
        audit.record(actor, AuditAction.ORDER_STATUS_CHANGED, AuditEntityType.ORDER, id,
                Map.of("status", current), after);

        return AdminOrderDetail.from(order, invoices.summaryFor(id), audit.history(AuditEntityType.ORDER, id));
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
