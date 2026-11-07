package rs.fishingshop.invoices;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.orders.CustomerOrder;
import rs.fishingshop.orders.CustomerOrderRepository;

@Service
public class CreditNoteService {

    private record IssuedInvoice(long id, String number, BigDecimal total, BigDecimal vatRate, BigDecimal vatAmount) {
    }

    private final JdbcTemplate jdbc;
    private final CustomerOrderRepository orders;
    private final InvoicePdf pdf;
    private final InvoiceProperties properties;

    CreditNoteService(JdbcTemplate jdbc, CustomerOrderRepository orders, InvoicePdf pdf, InvoiceProperties properties) {
        this.jdbc = jdbc;
        this.orders = orders;
        this.pdf = pdf;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public StoredInvoice issue(long orderId) {
        IssuedInvoice invoice = jdbc.query("""
                SELECT id, number, total, vat_rate, vat_amount FROM invoices WHERE order_id = ?
                """, (rs, row) -> new IssuedInvoice(rs.getLong("id"), rs.getString("number"),
                        rs.getBigDecimal("total"), rs.getBigDecimal("vat_rate"), rs.getBigDecimal("vat_amount")),
                orderId).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " has no invoice to reverse"));
        CustomerOrder order = orders.findWithItems(orderId)
                .orElseThrow(() -> new IllegalStateException("Order " + orderId + " does not exist"));

        OffsetDateTime issuedAt = jdbc.queryForObject("SELECT now()", OffsetDateTime.class);
        int year = jdbc.queryForObject(
                "SELECT EXTRACT(YEAR FROM now() AT TIME ZONE 'Europe/Belgrade')::int", Integer.class);
        int sequence = jdbc.queryForObject("""
                INSERT INTO credit_note_counters (year, last_number) VALUES (?, 1)
                ON CONFLICT (year) DO UPDATE SET last_number = credit_note_counters.last_number + 1
                RETURNING last_number
                """, Integer.class, year);
        String number = "ST-%d-%06d".formatted(year, sequence);
        BigDecimal total = invoice.total().negate();
        BigDecimal vatAmount = invoice.vatAmount().negate();

        byte[] document = pdf.render(new InvoicePdf.Document(
                "STORNO RAČUN",
                "Stornira račun " + invoice.number(),
                "Ukupno za povraćaj",
                number,
                issuedAt.toInstant(),
                order.getPublicNumber(),
                order.getCustomerName(),
                order.getShippingAddress(),
                order.getCustomerEmail(),
                order.getCustomerPhone(),
                order.getItems().stream()
                        .map(item -> new InvoicePdf.Line(item.getProductName(), item.getSku(), -item.getQuantity(),
                                item.getUnitPrice(), item.lineTotal().negate()))
                        .toList(),
                total,
                new Vat(total.subtract(vatAmount), vatAmount),
                invoice.vatRate(),
                properties));

        jdbc.update("""
                INSERT INTO credit_notes
                    (invoice_id, year, sequence_number, number, issued_at, total, vat_rate, vat_amount, pdf)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, invoice.id(), year, sequence, number, issuedAt, total, invoice.vatRate(), vatAmount, document);
        return new StoredInvoice(number, document);
    }

    @Transactional(readOnly = true)
    public Optional<StoredInvoice> findForOrder(long orderId) {
        return jdbc.query("""
                SELECT c.number, c.pdf FROM credit_notes c JOIN invoices i ON i.id = c.invoice_id
                WHERE i.order_id = ?
                """, (rs, row) -> new StoredInvoice(rs.getString("number"), rs.getBytes("pdf")), orderId)
                .stream()
                .findFirst();
    }

    @Transactional(readOnly = true)
    public Optional<String> reversedInvoiceNumber(long orderId) {
        return jdbc.queryForList("SELECT number FROM invoices WHERE order_id = ?", String.class, orderId)
                .stream().findFirst();
    }

    @Transactional(readOnly = true)
    public CreditNoteSummary summaryFor(long orderId) {
        List<CreditNoteSummary> found = jdbc.query("""
                SELECT c.number, c.issued_at, j.status AS job_status
                FROM orders o
                LEFT JOIN invoices i ON i.order_id = o.id
                LEFT JOIN credit_notes c ON c.invoice_id = i.id
                LEFT JOIN background_jobs j ON j.order_id = o.id AND j.type = 'ORDER_CREDIT_NOTE'
                WHERE o.id = ?
                """, (rs, row) -> {
            String number = rs.getString("number");
            if (number != null) {
                Instant issuedAt = rs.getObject("issued_at", OffsetDateTime.class).toInstant();
                return new CreditNoteSummary(InvoiceStatus.ISSUED, number, issuedAt);
            }
            String job = rs.getString("job_status");
            InvoiceStatus status = "PENDING".equals(job) ? InvoiceStatus.PENDING
                    : "DEAD".equals(job) ? InvoiceStatus.FAILED
                    : InvoiceStatus.NONE;
            return new CreditNoteSummary(status, null, null);
        }, orderId);
        return found.isEmpty() ? new CreditNoteSummary(InvoiceStatus.NONE, null, null) : found.getFirst();
    }
}
