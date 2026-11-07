package rs.fishingshop.invoices;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.CustomerOrderRepository;

@Service
@EnableConfigurationProperties(InvoiceProperties.class)
public class InvoiceService {

    private final JdbcTemplate jdbc;
    private final CustomerOrderRepository orders;
    private final InvoicePdf pdf;
    private final InvoiceProperties properties;

    InvoiceService(JdbcTemplate jdbc, CustomerOrderRepository orders, InvoicePdf pdf, InvoiceProperties properties) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.pdf = pdf;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StoredInvoice issue(long orderId) {
        CustomerOrder order = orders.findWithItems(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " does not exist"));
        OffsetDateTime issuedAt = jdbc.queryForObject("SELECT now()", OffsetDateTime.class);
        int year = jdbc.queryForObject(
                "SELECT EXTRACT(YEAR FROM now() AT TIME ZONE 'Europe/Belgrade')::int", Integer.class);
        int sequence = jdbc.queryForObject("""
                INSERT INTO invoice_counters (year, last_number) VALUES (?, 1)
                ON CONFLICT (year) DO UPDATE SET last_number = invoice_counters.last_number + 1
                RETURNING last_number
                """, Integer.class, year);
        String number = "RN-%d-%06d".formatted(year, sequence);
        Vat vat = Vat.ofGross(order.getTotal(), properties.vatRate());

        byte[] document = pdf.render(new InvoicePdf.Document(
                "RAČUN",
                null,
                "Ukupno za plaćanje",
                number,
                issuedAt.toInstant(),
                order.getPublicNumber(),
                order.getCustomerName(),
                order.getShippingAddress(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getItems().stream()
                        .map(item -> new InvoicePdf.Line(item.getProductName(), item.getSku(), item.getQuantity(),
                                item.getUnitPrice(), item.lineTotal()))
                        .toList(),
                order.getTotal(),
                vat,
                properties.vatRate(),
                properties));

        jdbc.update("""
                INSERT INTO invoices
                    (order_id, year, sequence_number, number, issued_at, total, vat_rate, vat_amount, pdf)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, order.getId(), year, sequence, number, issuedAt, order.getTotal(), properties.vatRate(),
                vat.amount(), document);
        return new StoredInvoice(number, document);
    }

    @Transactional(readOnly = true)
    public boolean isIssued(long orderId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM invoices WHERE order_id = ?)", Boolean.class, orderId));
    }

    @Transactional(readOnly = true)
    public Optional<StoredInvoice> findForOrder(long orderId) {
        return jdbc.query("SELECT number, pdf FROM invoices WHERE order_id = ?",
                (rs, row) -> new StoredInvoice(rs.getString("number"), rs.getBytes("pdf")), orderId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public InvoiceSummary summaryFor(long orderId) {
        List<InvoiceSummary> found = jdbc.query("""
                SELECT i.number, i.issued_at, j.status AS job_status
                FROM orders o
                LEFT JOIN invoices i ON i.order_id = o.id
                LEFT JOIN background_jobs j ON j.order_id = o.id AND j.type = 'ORDER_INVOICE'
                WHERE o.id = ?
                """, (rs, row) -> {
            String number = rs.getString("number");
            if (number != null) {
                Instant issuedAt = rs.getObject("issued_at", OffsetDateTime.class).toInstant();
                return new InvoiceSummary(InvoiceStatus.ISSUED, number, issuedAt);
            }
            String job = rs.getString("job_status");
            InvoiceStatus status = "PENDING".equals(job) ? InvoiceStatus.PENDING
                    : "DEAD".equals(job) ? InvoiceStatus.FAILED
                    : InvoiceStatus.NONE;
            return new InvoiceSummary(status, null, null);
        }, orderId);
        return found.isEmpty() ? new InvoiceSummary(InvoiceStatus.NONE, null, null) : found.getFirst();
    }
}
