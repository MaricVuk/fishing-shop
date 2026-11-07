package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import rs.fishingshop.jobs.JobWorker;

@AutoConfigureMockMvc
class CreditNoteTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@credit.test";
    private static final String WAREHOUSE = "magacin@credit.test";
    private static final String SKU_PREFIX = "CRN-";
    private static final String PRODUCT = "Štap za storno";

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        Mailpit.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JobWorker worker;

    @Autowired
    DataSource dataSource;

    long rod;

    @BeforeEach
    void setUp() {
        removeTestData();
        Mailpit.clear();
        employee(OWNER, "OWNER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
        long product = jdbc.queryForObject("""
                INSERT INTO products (category_id, name, brand, active)
                VALUES ((SELECT id FROM categories WHERE slug = 'stapovi'), ?, 'Acme', TRUE) RETURNING id
                """, Long.class, PRODUCT);
        rod = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, sku, attributes, price, active)
                VALUES (?, ?, '{"length":"2.7m"}'::jsonb, 4500.00, TRUE) RETURNING id
                """, Long.class, product, SKU_PREFIX + "ROD");
        jdbc.update("INSERT INTO inventory (variant_id, quantity) VALUES (?, 100)", rod);
    }

    @AfterEach
    void removeTestData() {
        clearAuditLog();
        jdbc.execute("ALTER TABLE credit_notes DISABLE TRIGGER USER");
        jdbc.execute("ALTER TABLE invoices DISABLE TRIGGER USER");
        try {
            jdbc.update("DELETE FROM credit_notes");
            jdbc.update("DELETE FROM invoices");
        } finally {
            jdbc.execute("ALTER TABLE invoices ENABLE TRIGGER USER");
            jdbc.execute("ALTER TABLE credit_notes ENABLE TRIGGER USER");
        }
        jdbc.update("DELETE FROM invoice_counters");
        jdbc.update("DELETE FROM credit_note_counters");
        List<Long> orders = jdbc.queryForList(
                "SELECT id FROM orders WHERE customer_email = 'kupac@credit.test'", Long.class);
        jdbc.update("DELETE FROM stock_movements WHERE variant_id IN "
                + "(SELECT id FROM product_variants WHERE sku LIKE ?)", SKU_PREFIX + "%");
        for (Long order : orders) {
            jdbc.update("DELETE FROM background_jobs WHERE order_id = ?", order);
            jdbc.update("DELETE FROM order_items WHERE order_id = ?", order);
            jdbc.update("DELETE FROM orders WHERE id = ?", order);
        }
        jdbc.update("DELETE FROM inventory WHERE variant_id IN "
                + "(SELECT id FROM product_variants WHERE sku LIKE ?)", SKU_PREFIX + "%");
        jdbc.update("DELETE FROM product_variants WHERE sku LIKE ?", SKU_PREFIX + "%");
        jdbc.update("DELETE FROM products WHERE name = ?", PRODUCT);
    }

    @Test
    void cancellingAnInvoicedOrderIssuesOneCreditNoteThatReversesTheInvoice() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        long order = placeOrder(2);
        changeStatus(owner, order, "CONFIRMED").andExpect(status().isOk());
        worker.runOnce();
        String invoice = "RN-" + year() + "-000001";

        changeStatus(owner, order, "CANCELLED").andExpect(status().isOk());
        worker.runOnce();
        worker.runOnce();

        String number = "ST-" + year() + "-000001";
        assertThat(creditNotes(order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT c.number FROM credit_notes c JOIN invoices i ON i.id = c.invoice_id WHERE i.order_id = ?
                """, String.class, order)).isEqualTo(number);
        assertThat(jdbc.queryForObject("""
                SELECT c.total FROM credit_notes c JOIN invoices i ON i.id = c.invoice_id WHERE i.order_id = ?
                """, BigDecimal.class, order)).isEqualByComparingTo("-9000.00");
        assertThat(jdbc.queryForObject("""
                SELECT c.vat_amount FROM credit_notes c JOIN invoices i ON i.id = c.invoice_id WHERE i.order_id = ?
                """, BigDecimal.class, order)).isEqualByComparingTo("-1500.00");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, order))
                .isEqualTo(1);

        byte[] pdf = jdbc.queryForObject("""
                SELECT c.pdf FROM credit_notes c JOIN invoices i ON i.id = c.invoice_id WHERE i.order_id = ?
                """, byte[].class, order);
        assertThat(textOf(pdf))
                .contains("STORNO RAČUN")
                .contains(number)
                .contains(invoice)
                .contains("Štap za storno")
                .contains("-2")
                .contains("-9.000,00 RSD")
                .contains("-7.500,00 RSD")
                .contains("-1.500,00 RSD");

        String messages = Mailpit.messages();
        String id = JsonPath.read(messages, "$.messages[?(@.Subject == 'Storno računa " + invoice + "')].ID")
                .toString().replaceAll("[\\[\\]\"]", "");
        assertThat(id).isNotBlank();
        String message = Mailpit.message(id);
        assertThat((List<String>) JsonPath.read(message, "$.Attachments[*].FileName"))
                .containsExactly(number + ".pdf");
        assertThat((String) JsonPath.read(message, "$.Text")).contains(invoice).contains(number);
    }

    @Test
    void creditNotesAreNumberedWithoutGapsInTheirOwnSeries() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        long first = invoicedOrder(owner);
        long second = invoicedOrder(owner);

        changeStatus(owner, second, "CANCELLED").andExpect(status().isOk());
        changeStatus(owner, first, "CANCELLED").andExpect(status().isOk());
        worker.runOnce();

        assertThat(jdbc.queryForList("SELECT number FROM credit_notes ORDER BY sequence_number", String.class))
                .containsExactly("ST-" + year() + "-000001", "ST-" + year() + "-000002");
        assertThat(jdbc.queryForObject("SELECT last_number FROM invoice_counters", Integer.class)).isEqualTo(2);
    }

    @Test
    void anOrderCancelledBeforeItsInvoiceWasIssuedGetsNeitherInvoiceNorCreditNote() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        long order = placeOrder(1);
        changeStatus(owner, order, "CONFIRMED").andExpect(status().isOk());
        changeStatus(owner, order, "CANCELLED").andExpect(status().isOk());

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, order))
                .isZero();
        assertThat(creditNoteJobs(order)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_notes", Integer.class)).isZero();
    }

    @Test
    void aNewOrderCancelledNeverQueuesACreditNote() throws Exception {
        long order = placeOrder(1);

        changeStatus(logIn(mvc, OWNER), order, "CANCELLED").andExpect(status().isOk());

        assertThat(creditNoteJobs(order)).isZero();
    }

    @Test
    void aCancelWaitsForAnInvoiceThatIsBeingIssuedAndThenReversesIt() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        invoicedOrder(owner);

        long racing = placeOrder(1);
        changeStatus(owner, racing, "CONFIRMED").andExpect(status().isOk());
        CompletableFuture<Integer> worked;
        CompletableFuture<Integer> cancelled;
        try (Connection counter = dataSource.getConnection()) {
            counter.setAutoCommit(false);
            try (Statement s = counter.createStatement()) {
                s.execute("SELECT year FROM invoice_counters WHERE year = " + year() + " FOR UPDATE");
            }
            worked = CompletableFuture.supplyAsync(() -> worker.runOnce());
            awaitWaiter("%invoice_counters%");

            cancelled = CompletableFuture.supplyAsync(() -> statusOf(changeStatusQuietly(owner, racing)));
            awaitWaiterOrDone("%orders%", cancelled);
            counter.commit();
        }

        worked.get(20, TimeUnit.SECONDS);
        assertThat(cancelled.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, racing))
                .isEqualTo(1);
        assertThat(creditNoteJobs(racing)).isEqualTo(1);
        worker.runOnce();
        assertThat(creditNotes(racing)).isEqualTo(1);
    }

    @Test
    void anInvoiceJobWaitsForACancelInProgressAndThenIssuesNothing() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        long order = placeOrder(1);
        changeStatus(owner, order, "CONFIRMED").andExpect(status().isOk());
        CompletableFuture<Integer> worked;
        try (Connection cancel = dataSource.getConnection()) {
            cancel.setAutoCommit(false);
            try (Statement s = cancel.createStatement()) {
                s.execute("SELECT id FROM orders WHERE id = " + order + " FOR UPDATE");
            }
            worked = CompletableFuture.supplyAsync(() -> worker.runOnce());
            awaitWaiterOrDone("%", worked);
            try (Statement s = cancel.createStatement()) {
                s.execute("UPDATE orders SET status = 'CANCELLED' WHERE id = " + order);
            }
            cancel.commit();
        }

        worked.get(20, TimeUnit.SECONDS);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, order))
                .isZero();
    }

    @Test
    void theDatabaseKeepsCreditNotesAsIssued() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        long order = invoicedOrder(owner);
        changeStatus(owner, order, "CANCELLED").andExpect(status().isOk());
        worker.runOnce();
        long invoiceId = jdbc.queryForObject("SELECT id FROM invoices WHERE order_id = ?", Long.class, order);

        assertThatThrownBy(() -> jdbc.update("UPDATE credit_notes SET total = 0"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("credit notes cannot be changed");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM credit_notes"))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("credit notes cannot be changed");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO credit_notes (invoice_id, year, sequence_number, number, total, vat_rate, vat_amount, pdf)
                VALUES (?, 2026, 99, 'ST-2026-000099', -1.00, 20, 0, '\\x25'::bytea)
                """, invoiceId)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO credit_notes (invoice_id, year, sequence_number, number, total, vat_rate, vat_amount, pdf)
                VALUES (?, 2026, 98, 'ST-2026-000098', 5.00, 20, 0, '\\x25'::bytea)
                """, invoiceId)).isInstanceOf(DataAccessException.class);
    }

    @Test
    void theOrderDetailShowsTheCreditNoteAndStaffWhoSeeInvoicesCanDownloadIt() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        long order = invoicedOrder(owner);
        mvc.perform(get("/api/admin/orders/{id}", order).cookie(owner))
                .andExpect(jsonPath("$.creditNote.status").value("NONE"));

        changeStatus(owner, order, "CANCELLED")
                .andExpect(jsonPath("$.creditNote.status").value("PENDING"));
        worker.runOnce();

        String number = "ST-" + year() + "-000001";
        mvc.perform(get("/api/admin/orders/{id}", order).cookie(owner))
                .andExpect(jsonPath("$.creditNote.status").value("ISSUED"))
                .andExpect(jsonPath("$.creditNote.number").value(number));
        mvc.perform(get("/api/admin/orders/{id}/credit-note", order).cookie(owner))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"" + number + ".pdf\""))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/api/admin/orders/{id}/credit-note", order).cookie(logIn(mvc, WAREHOUSE)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/orders/{id}/credit-note", placeOrder(1)).cookie(owner))
                .andExpect(status().isNotFound());
    }

    private void awaitWaiter(String queryLike) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> waiting(queryLike));
    }

    private void awaitWaiterOrDone(String queryLike, CompletableFuture<?> work) {
        Awaitility.await().atMost(10, TimeUnit.SECONDS).pollInterval(50, TimeUnit.MILLISECONDS)
                .until(() -> work.isDone() || waiting(queryLike));
    }

    private boolean waiting(String queryLike) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM pg_stat_activity
                WHERE wait_event_type = 'Lock' AND query ILIKE ? AND pid <> pg_backend_pid()
                """, Integer.class, queryLike) > 0;
    }

    private long invoicedOrder(Cookie owner) throws Exception {
        long order = placeOrder(1);
        changeStatus(owner, order, "CONFIRMED").andExpect(status().isOk());
        worker.runOnce();
        return order;
    }

    private int creditNotes(long order) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM credit_notes c JOIN invoices i ON i.id = c.invoice_id WHERE i.order_id = ?
                """, Integer.class, order);
    }

    private int creditNoteJobs(long order) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM background_jobs WHERE order_id = ? AND type = 'ORDER_CREDIT_NOTE'",
                Integer.class, order);
    }

    private long placeOrder(int quantity) throws Exception {
        String body = """
                {"customer":{"name":"Jovan Đorđević","email":"kupac@credit.test",
                 "shippingAddress":"Cara Dušana 7, Čačak"},
                 "items":[{"variantId":%d,"quantity":%d}]}""".formatted(rod, quantity);
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return OrderNumbers.idOf(jdbc, response);
    }

    private ResultActions changeStatus(Cookie session, long order, String status) throws Exception {
        return mvc.perform(post("/api/admin/orders/{id}/status", order).with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }

    private ResultActions changeStatusQuietly(Cookie session, long order) {
        try {
            return changeStatus(session, order, "CANCELLED");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int statusOf(ResultActions result) {
        return result.andReturn().getResponse().getStatus();
    }

    private static int year() {
        return LocalDate.now(ZoneId.of("Europe/Belgrade")).getYear();
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
