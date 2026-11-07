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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
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
import org.springframework.transaction.support.TransactionTemplate;
import rs.fishingshop.invoices.InvoiceService;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobProperties;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.jobs.JobWorker;

@AutoConfigureMockMvc
class InvoiceTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@invoices.test";
    private static final String SELLER = "prodavac@invoices.test";
    private static final String WAREHOUSE = "magacin@invoices.test";
    private static final String SKU_PREFIX = "INV-";
    private static final String PRODUCT = "Štap Đerdap Čelik";

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        Mailpit.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JobWorker worker;

    @Autowired
    InvoiceService invoices;

    @Autowired
    TransactionTemplate transactions;

    @Autowired
    List<JobHandler> handlers;

    @Autowired
    JobProperties jobProperties;

    long rod;

    @BeforeEach
    void setUp() {
        removeTestData();
        Mailpit.clear();
        employee(OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
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
        jdbc.execute("ALTER TABLE invoices DISABLE TRIGGER USER");
        try {
            jdbc.update("DELETE FROM invoices");
        } finally {
            jdbc.execute("ALTER TABLE invoices ENABLE TRIGGER USER");
        }
        jdbc.update("DELETE FROM invoice_counters");
        List<Long> orders = jdbc.queryForList(
                "SELECT id FROM orders WHERE customer_email LIKE '%@invoices.test'", Long.class);
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
    void confirmingAnOrderQueuesExactlyOneInvoiceJob() throws Exception {
        Cookie seller = logIn(mvc, SELLER);
        long confirmed = placeOrder(2);
        long untouched = placeOrder(1);
        long cancelled = placeOrder(1);

        changeStatus(seller, confirmed, "CONFIRMED").andExpect(status().isOk());
        changeStatus(seller, cancelled, "CANCELLED").andExpect(status().isOk());

        assertThat(invoiceJobs(confirmed)).isEqualTo(1);
        assertThat(invoiceJobs(untouched)).isZero();
        assertThat(invoiceJobs(cancelled)).isZero();
    }

    @Test
    void theWorkerIssuesTheFirstInvoiceOfTheYearAndEmailsItAsAPdf() throws Exception {
        long order = confirmedOrder(2);

        worker.runOnce();

        String number = "RN-" + currentYear() + "-000001";
        assertThat(jdbc.queryForObject("SELECT number FROM invoices WHERE order_id = ?", String.class, order))
                .isEqualTo(number);
        byte[] pdf = jdbc.queryForObject("SELECT pdf FROM invoices WHERE order_id = ?", byte[].class, order);
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(textOf(pdf))
                .contains(number)
                .contains("Porudžbina #" + order)
                .contains("Jovan Đorđević Čačić")
                .contains("Štap Đerdap Čelik")
                .contains("INV-ROD")
                .contains("9.000,00 RSD")
                .contains("7.500,00 RSD")
                .contains("1.500,00 RSD")
                .contains("Ovo nije fiskalni račun");

        String messages = Mailpit.messages();
        List<String> subjects = JsonPath.read(messages, "$.messages[*].Subject");
        assertThat(subjects).contains("Račun " + number);
        String invoiceMail = JsonPath.read(messages, "$.messages[?(@.Subject == 'Račun " + number + "')].ID")
                .toString().replaceAll("[\\[\\]\"]", "");
        String message = Mailpit.message(invoiceMail);
        assertThat((List<String>) JsonPath.read(message, "$.Attachments[*].FileName")).containsExactly(number + ".pdf");
        assertThat((List<String>) JsonPath.read(message, "$.Attachments[*].ContentType"))
                .containsExactly("application/pdf");
    }

    @Test
    void numberingContinuesFromTheCounterOfTheYear() throws Exception {
        jdbc.update("INSERT INTO invoice_counters (year, last_number) VALUES (?, 41)", currentYear());
        long order = confirmedOrder(1);

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT number FROM invoices WHERE order_id = ?", String.class, order))
                .isEqualTo("RN-" + currentYear() + "-000042");
    }

    @Test
    void aRolledBackInvoiceLeavesNoGapInTheNumbering() throws Exception {
        long first = confirmedOrder(1);
        long second = confirmedOrder(1);

        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            invoices.issue(first);
            throw new IllegalStateException("mail server rejected the message");
        })).isInstanceOf(IllegalStateException.class);
        transactions.executeWithoutResult(tx -> invoices.issue(second));

        assertThat(jdbc.queryForObject("SELECT number FROM invoices WHERE order_id = ?", String.class, second))
                .isEqualTo("RN-" + currentYear() + "-000001");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, first))
                .isZero();
    }

    @Test
    void twentyInvoicesIssuedAtOnceGetNumbersOneToTwentyWithoutGaps() throws Exception {
        List<Long> orders = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            orders.add(confirmedOrder(1));
        }

        CompletableFuture.allOf(orders.stream()
                .map(order -> CompletableFuture.runAsync(
                        () -> transactions.executeWithoutResult(tx -> invoices.issue(order))))
                .toArray(CompletableFuture[]::new)).get();

        List<Integer> numbers = jdbc.queryForList(
                "SELECT sequence_number FROM invoices WHERE year = ? ORDER BY sequence_number", Integer.class,
                currentYear());
        assertThat(numbers).containsExactlyElementsOf(IntStream.rangeClosed(1, 20).boxed().toList());
    }

    @Test
    void anOrderCancelledBeforeTheWorkerRunsGetsNoInvoice() throws Exception {
        long order = confirmedOrder(1);
        changeStatus(logIn(mvc, OWNER), order, "CANCELLED").andExpect(status().isOk());

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices WHERE order_id = ?", Integer.class, order))
                .isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM background_jobs WHERE order_id = ? AND type = 'ORDER_INVOICE'", String.class,
                order)).isEqualTo("DONE");
    }

    @Test
    void aRetriedInvoiceJobNeitherIssuesASecondInvoiceNorEmailsAgain() throws Exception {
        long order = confirmedOrder(1);
        worker.runOnce();
        reopen(order, "ORDER_INVOICE");
        Mailpit.clear();

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM background_jobs WHERE order_id = ? AND type = 'ORDER_INVOICE_EMAIL'",
                Integer.class, order)).isEqualTo(1);
        assertThat((Integer) JsonPath.read(Mailpit.messages(), "$.total")).isZero();
    }

    @Test
    void aRetriedInvoiceEmailSendsTheStoredPdfAgain() throws Exception {
        long order = confirmedOrder(1);
        worker.runOnce();
        reopen(order, "ORDER_INVOICE_EMAIL");
        Mailpit.clear();

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoices", Integer.class)).isEqualTo(1);
        List<String> subjects = JsonPath.read(Mailpit.messages(), "$.messages[*].Subject");
        assertThat(subjects).containsExactly("Račun RN-" + currentYear() + "-000001");
    }

    @Test
    void theInvoiceStaysIssuedWhenItsEmailCanNeverBeSent() throws Exception {
        long order = confirmedOrder(1);
        jdbc.update("UPDATE orders SET customer_email = 'a@b@invoices.test' WHERE id = ?", order);

        worker.runOnce();

        assertThat(jdbc.queryForObject("SELECT number FROM invoices WHERE order_id = ?", String.class, order))
                .isEqualTo("RN-" + currentYear() + "-000001");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM background_jobs WHERE order_id = ? AND type = 'ORDER_INVOICE_EMAIL'",
                String.class, order)).isEqualTo("DEAD");
        mvc.perform(get("/api/admin/orders/{id}", order).cookie(logIn(mvc, OWNER)))
                .andExpect(jsonPath("$.invoice.status").value("ISSUED"));
    }

    @Test
    void anInvoiceIsNotEmailedForAnOrderCancelledAfterItWasIssued() throws Exception {
        long order = confirmedOrder(1);
        transactions.executeWithoutResult(tx -> invoices.issue(order));
        jdbc.update("UPDATE background_jobs SET status = 'DONE', finished_at = now() "
                + "WHERE order_id = ? AND type = 'ORDER_INVOICE'", order);
        jdbc.update("INSERT INTO background_jobs (type, order_id) VALUES ('ORDER_INVOICE_EMAIL', ?)", order);
        jdbc.update("UPDATE orders SET status = 'CANCELLED' WHERE id = ?", order);
        jdbc.update("UPDATE background_jobs SET status = 'DONE', finished_at = now() "
                + "WHERE order_id = ? AND type = 'ORDER_CONFIRMATION_EMAIL'", order);
        Mailpit.clear();

        worker.runOnce();

        assertThat((Integer) JsonPath.read(Mailpit.messages(), "$.total")).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT status FROM background_jobs WHERE order_id = ? AND type = 'ORDER_INVOICE_EMAIL'",
                String.class, order)).isEqualTo("DONE");
    }

    @Test
    void sendingOneInvoiceEmailDoesNotHoldUpIssuingTheNextInvoice() throws Exception {
        long first = confirmedOrder(1);
        long second = confirmedOrder(1);
        jdbc.update("UPDATE background_jobs SET status = 'DONE', finished_at = now() "
                + "WHERE type = 'ORDER_CONFIRMATION_EMAIL' AND order_id IN (?, ?)", first, second);
        jdbc.update("UPDATE background_jobs SET next_attempt_at = now() + interval '1 hour' "
                + "WHERE type = 'ORDER_INVOICE' AND order_id = ?", second);
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobWorker slowMailWorker = workerWith(JobType.ORDER_INVOICE_EMAIL, job -> {
            sending.countDown();
            await(release);
        });

        CompletableFuture<Integer> busy = CompletableFuture.supplyAsync(slowMailWorker::runOnce);
        try {
            assertThat(sending.await(20, TimeUnit.SECONDS)).isTrue();

            transactions.executeWithoutResult(tx -> {
                jdbc.execute("SET LOCAL lock_timeout = '1s'");
                invoices.issue(second);
            });
        } finally {
            release.countDown();
            busy.get(30, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT number FROM invoices WHERE order_id = ?", String.class, second))
                .isEqualTo("RN-" + currentYear() + "-000002");
    }

    private JobWorker workerWith(JobType replaced, java.util.function.Consumer<ClaimedJob> behaviour) {
        List<JobHandler> wrapped = handlers.stream()
                .map(handler -> handler.type() != replaced ? handler : new JobHandler() {
                    @Override
                    public JobType type() {
                        return replaced;
                    }

                    @Override
                    public void handle(ClaimedJob job) {
                        behaviour.accept(job);
                        handler.handle(job);
                    }
                })
                .toList();
        return new JobWorker(jdbc, transactions, wrapped, jobProperties);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void reopen(long order, String type) {
        jdbc.update("""
                UPDATE background_jobs SET status = 'PENDING', finished_at = NULL, next_attempt_at = now()
                WHERE order_id = ? AND type = ?
                """, order, type);
    }

    @Test
    void textTheFontCannotDrawStillProducesAPdf() throws Exception {
        long order = confirmedOrder(1);
        jdbc.update("UPDATE orders SET customer_name = 'Pecaroš 🎣 Ribić' WHERE id = ?", order);

        worker.runOnce();

        byte[] pdf = jdbc.queryForObject("SELECT pdf FROM invoices WHERE order_id = ?", byte[].class, order);
        assertThat(textOf(pdf)).contains("Pecaroš ? Ribić");
    }

    @Test
    void anIssuedInvoiceCannotBeChangedOrDeleted() throws Exception {
        long order = confirmedOrder(1);
        worker.runOnce();

        assertThatThrownBy(() -> jdbc.update("UPDATE invoices SET total = 1 WHERE order_id = ?", order))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM invoices WHERE order_id = ?", order))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void ownerAndSellerDownloadThePdfAndTheDetailShowsTheInvoice() throws Exception {
        long order = confirmedOrder(1);
        String number = "RN-" + currentYear() + "-000001";
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(get("/api/admin/orders/{id}", order).cookie(owner))
                .andExpect(jsonPath("$.invoice.status").value("PENDING"))
                .andExpect(jsonPath("$.invoice.number").doesNotExist());
        worker.runOnce();

        mvc.perform(get("/api/admin/orders/{id}", order).cookie(owner))
                .andExpect(jsonPath("$.invoice.status").value("ISSUED"))
                .andExpect(jsonPath("$.invoice.number").value(number));
        for (Cookie session : List.of(owner, logIn(mvc, SELLER))) {
            byte[] body = mvc.perform(get("/api/admin/orders/{id}/invoice", order).cookie(session))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "application/pdf"))
                    .andExpect(header().string("Content-Disposition",
                            "attachment; filename=\"" + number + ".pdf\""))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(body, 0, 5)).isEqualTo("%PDF-");
        }
    }

    @Test
    void downloadIsRefusedWithoutTheRightRoleOrWithoutAnInvoice() throws Exception {
        long withInvoice = confirmedOrder(1);
        worker.runOnce();
        long withoutInvoice = placeOrder(1);

        mvc.perform(get("/api/admin/orders/{id}/invoice", withInvoice)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/orders/{id}/invoice", withInvoice).cookie(logIn(mvc, WAREHOUSE)))
                .andExpect(status().isForbidden());
        Cookie owner = logIn(mvc, OWNER);
        mvc.perform(get("/api/admin/orders/{id}/invoice", withoutInvoice).cookie(owner))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/orders/{id}", withoutInvoice).cookie(owner))
                .andExpect(jsonPath("$.invoice.status").value("NONE"));
        mvc.perform(get("/api/admin/orders/{id}/invoice", 999_999_999L).cookie(owner))
                .andExpect(status().isNotFound());
    }

    private long confirmedOrder(int quantity) throws Exception {
        long order = placeOrder(quantity);
        jdbc.update("UPDATE orders SET status = 'CONFIRMED' WHERE id = ?", order);
        jdbc.update("INSERT INTO background_jobs (type, order_id) VALUES ('ORDER_INVOICE', ?)", order);
        return order;
    }

    private long placeOrder(int quantity) throws Exception {
        String body = """
                {"customer":{"name":"Jovan Đorđević Čačić","email":"kupac@invoices.test",
                 "shippingAddress":"Cara Dušana 7, Čačak"},
                 "items":[{"variantId":%d,"quantity":%d}]}""".formatted(rod, quantity);
        String response = mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.id")).longValue();
    }

    private ResultActions changeStatus(Cookie session, long order, String status) throws Exception {
        return mvc.perform(post("/api/admin/orders/{id}/status", order).with(csrf()).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"%s\"}".formatted(status)));
    }

    private int invoiceJobs(long order) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM background_jobs WHERE order_id = ? AND type = 'ORDER_INVOICE'", Integer.class,
                order);
    }

    private int currentYear() {
        return jdbc.queryForObject(
                "SELECT EXTRACT(YEAR FROM now() AT TIME ZONE 'Europe/Belgrade')::int", Integer.class);
    }

    private static String textOf(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
}
