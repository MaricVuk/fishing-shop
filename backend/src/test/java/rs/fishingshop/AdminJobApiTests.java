package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import rs.fishingshop.jobs.JobWorker;

@AutoConfigureMockMvc
class AdminJobApiTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@jobs.test";
    private static final String SECOND_OWNER = "vlasnik2@jobs.test";
    private static final String SELLER = "prodavac@jobs.test";
    private static final String WAREHOUSE = "magacin@jobs.test";

    @DynamicPropertySource
    static void mailProps(DynamicPropertyRegistry registry) {
        Mailpit.register(registry);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JobWorker worker;

    private final List<Long> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        removeTestData();
        Mailpit.clear();
        employee(OWNER, "OWNER", true);
        employee(SECOND_OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
        employee(WAREHOUSE, "WAREHOUSE", true);
    }

    @AfterEach
    void removeTestData() {
        clearAuditLog();
        jdbc.update("DELETE FROM background_jobs");
        orders.forEach(order -> jdbc.update("DELETE FROM orders WHERE id = ?", order));
        orders.clear();
    }

    @Test
    void theOwnerSeesCountsTheOldestOverdueJobAndAFilteredPage() throws Exception {
        long pending = job("ORDER_CONFIRMATION_EMAIL", "PENDING");
        jdbc.update("UPDATE background_jobs SET next_attempt_at = now() - interval '10 minutes' WHERE id = ?", pending);
        job("ORDER_INVOICE", "DONE");
        job("ORDER_CONFIRMATION_EMAIL", "DONE");
        long dead = job("ORDER_INVOICE", "DEAD");
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(get("/api/admin/jobs").cookie(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.counts.PENDING").value(1))
                .andExpect(jsonPath("$.counts.DONE").value(2))
                .andExpect(jsonPath("$.counts.DEAD").value(1))
                .andExpect(jsonPath("$.oldestOverdueSince").isNotEmpty())
                .andExpect(jsonPath("$.jobs.totalElements").value(4))
                .andExpect(jsonPath("$.jobs.content[0].id").value(dead));

        mvc.perform(get("/api/admin/jobs").param("status", "DEAD").cookie(owner))
                .andExpect(jsonPath("$.jobs.content", hasSize(1)))
                .andExpect(jsonPath("$.jobs.content[0].id").value(dead))
                .andExpect(jsonPath("$.jobs.content[0].type").value("ORDER_INVOICE"))
                .andExpect(jsonPath("$.jobs.content[0].lastError").value("IllegalStateException: SMTP is down"))
                .andExpect(jsonPath("$.counts.DONE").value(2));

        mvc.perform(get("/api/admin/jobs").param("type", "ORDER_CONFIRMATION_EMAIL").cookie(owner))
                .andExpect(jsonPath("$.jobs.content[*].type",
                        contains("ORDER_CONFIRMATION_EMAIL", "ORDER_CONFIRMATION_EMAIL")));
    }

    @Test
    void noOverdueJobMeansNoOldestOverdueTime() throws Exception {
        long pending = job("ORDER_INVOICE", "PENDING");
        jdbc.update("UPDATE background_jobs SET next_attempt_at = now() + interval '10 minutes' WHERE id = ?", pending);

        mvc.perform(get("/api/admin/jobs").cookie(logIn(mvc, OWNER)))
                .andExpect(jsonPath("$.counts.PENDING").value(1))
                .andExpect(jsonPath("$.oldestOverdueSince").doesNotExist());
    }

    @Test
    void onlyTheOwnerMaySeeOrRetryJobs() throws Exception {
        long dead = job("ORDER_INVOICE", "DEAD");

        mvc.perform(get("/api/admin/jobs")).andExpect(status().isUnauthorized());
        for (String email : List.of(SELLER, WAREHOUSE)) {
            Cookie session = logIn(mvc, email);
            mvc.perform(get("/api/admin/jobs").cookie(session)).andExpect(status().isForbidden());
            retry(session, dead).andExpect(status().isForbidden());
        }
        assertThat(statusOf(dead)).isEqualTo("DEAD");
    }

    @Test
    void badFiltersAreRejected() throws Exception {
        Cookie owner = logIn(mvc, OWNER);

        mvc.perform(get("/api/admin/jobs").param("status", "RUNNING").cookie(owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/jobs").param("size", "0").cookie(owner)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/jobs").param("size", "101").cookie(owner)).andExpect(status().isBadRequest());
    }

    @Test
    void retryingADeadJobQueuesItAgainAndIsAudited() throws Exception {
        long dead = job("ORDER_INVOICE", "DEAD");

        retry(logIn(mvc, OWNER), dead)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.attempts").value(0))
                .andExpect(jsonPath("$.finishedAt").doesNotExist())
                .andExpect(jsonPath("$.lastError").value("IllegalStateException: SMTP is down"));

        Map<String, Object> audit = jdbc.queryForMap("""
                SELECT actor_email, action, entity_type, entity_id, old_value::text AS old_value,
                       new_value::text AS new_value
                FROM audit_log
                """);
        assertThat(audit).containsEntry("actor_email", OWNER)
                .containsEntry("action", "JOB_RETRIED")
                .containsEntry("entity_type", "JOB")
                .containsEntry("entity_id", String.valueOf(dead));
        assertThat((String) audit.get("old_value")).contains("\"status\": \"DEAD\"").contains("\"attempts\": 14")
                .contains("SMTP is down");
        assertThat((String) audit.get("new_value")).contains("\"status\": \"PENDING\"");
    }

    @Test
    void onlyADeadJobCanBeRetried() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        for (String state : List.of("PENDING", "DONE")) {
            long job = job("ORDER_CONFIRMATION_EMAIL", state);
            retry(owner, job).andExpect(status().isConflict());
            assertThat(statusOf(job)).isEqualTo(state);
        }
        retry(owner, 999_999_999L).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isZero();
    }

    @Test
    void retryNeedsTheCsrfToken() throws Exception {
        long dead = job("ORDER_INVOICE", "DEAD");

        mvc.perform(post("/api/admin/jobs/{id}/retry", dead).cookie(logIn(mvc, OWNER)))
                .andExpect(status().isForbidden());
        assertThat(statusOf(dead)).isEqualTo("DEAD");
    }

    @Test
    void twoOwnersRetryingTheSameJobAtOnceSucceedExactlyOnce() throws Exception {
        long dead = job("ORDER_INVOICE", "DEAD");
        Cookie first = logIn(mvc, OWNER);
        Cookie second = logIn(mvc, SECOND_OWNER);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (Cookie session : List.of(first, second)) {
                Callable<Integer> call = () -> {
                    start.await();
                    return retry(session, dead).andReturn().getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> result : results) {
                statuses.add(result.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE action = 'JOB_RETRIED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void theWorkerRunsARetriedJob() throws Exception {
        long dead = job("ORDER_CONFIRMATION_EMAIL", "DEAD");

        retry(logIn(mvc, OWNER), dead).andExpect(status().isOk());
        worker.runOnce();

        assertThat(statusOf(dead)).isEqualTo("DONE");
        assertThat((Integer) JsonPath.read(Mailpit.messages(), "$.total")).isEqualTo(1);
    }

    private long job(String type, String status) {
        long order = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Posao Test', 'kupac@jobs.test', 'Ulica 1', 100.00) RETURNING id
                """, Long.class);
        orders.add(order);
        return switch (status) {
            case "PENDING" -> jdbc.queryForObject(
                    "INSERT INTO background_jobs (type, order_id) VALUES (?, ?) RETURNING id", Long.class, type, order);
            case "DONE" -> jdbc.queryForObject("""
                    INSERT INTO background_jobs (type, order_id, status, finished_at)
                    VALUES (?, ?, 'DONE', now()) RETURNING id
                    """, Long.class, type, order);
            default -> jdbc.queryForObject("""
                    INSERT INTO background_jobs (type, order_id, status, attempts, last_error, finished_at)
                    VALUES (?, ?, 'DEAD', 14, 'IllegalStateException: SMTP is down', now()) RETURNING id
                    """, Long.class, type, order);
        };
    }

    private ResultActions retry(Cookie session, long job) throws Exception {
        return mvc.perform(post("/api/admin/jobs/{id}/retry", job).with(csrf()).cookie(session));
    }

    private String statusOf(long job) {
        return jdbc.queryForObject("SELECT status FROM background_jobs WHERE id = ?", String.class, job);
    }
}
