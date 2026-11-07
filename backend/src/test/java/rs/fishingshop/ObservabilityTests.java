package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import rs.fishingshop.jobs.JobWorkerHeartbeat;

@AutoConfigureMockMvc
class ObservabilityTests extends EmployeeTestSupport {

    private static final String OWNER = "vlasnik@observability.test";
    private static final String SELLER = "prodavac@observability.test";

    @Autowired
    MockMvc mvc;

    @Autowired
    MeterRegistry registry;

    @Autowired
    JobWorkerHeartbeat heartbeat;

    private final List<Long> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        removeJobs();
        employee(OWNER, "OWNER", true);
        employee(SELLER, "SELLER", true);
    }

    @AfterEach
    void removeJobs() {
        jdbc.update("DELETE FROM background_jobs");
        orders.forEach(order -> jdbc.update("DELETE FROM orders WHERE id = ?", order));
        orders.clear();
    }

    @Test
    void anonymousHealthShowsOnlyTheStatusAndStaysUpWithoutAMailServer() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void ownerSeesTheJobQueueInHealthDetails() throws Exception {
        job("PENDING", "now() - interval '10 minutes'");
        job("PENDING", "now() + interval '1 hour'");
        job("DEAD", "now()");

        mvc.perform(get("/actuator/health").cookie(logIn(mvc, OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.jobs.status").value("UP"))
                .andExpect(jsonPath("$.components.jobs.details.pending").value(2))
                .andExpect(jsonPath("$.components.jobs.details.dead").value(1))
                .andExpect(jsonPath("$.components.jobs.details.done").value(0))
                .andExpect(jsonPath("$.components.jobs.details.oldestOverdueSeconds").value(greaterThanOrEqualTo(600)));
    }

    @Test
    void sellerSeesNoHealthDetails() throws Exception {
        mvc.perform(get("/actuator/health").cookie(logIn(mvc, SELLER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void jobMetricsAreOnlyForTheOwner() throws Exception {
        job("DEAD", "now()");
        String metric = "/actuator/metrics/shop.jobs?tag=status:DEAD";

        mvc.perform(get(metric)).andExpect(status().isUnauthorized());
        mvc.perform(get(metric).cookie(logIn(mvc, SELLER))).andExpect(status().isForbidden());
        mvc.perform(get(metric).cookie(logIn(mvc, OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.measurements[0].value").value(1.0));
    }

    @Test
    void otherActuatorEndpointsStayClosedEvenForTheOwner() throws Exception {
        Cookie owner = logIn(mvc, OWNER);
        for (String endpoint : List.of("/actuator/env", "/actuator/loggers", "/actuator/beans", "/actuator/heapdump")) {
            mvc.perform(get(endpoint).cookie(owner)).andExpect(status().isForbidden());
        }
    }

    @Test
    void gaugesFollowTheQueue() {
        job("PENDING", "now() - interval '2 minutes'");
        job("DONE", "now()");
        heartbeat.recordPoll();

        assertThat(registry.get("shop.jobs").tag("status", "PENDING").gauge().value()).isEqualTo(1);
        assertThat(registry.get("shop.jobs").tag("status", "DONE").gauge().value()).isEqualTo(1);
        assertThat(registry.get("shop.jobs").tag("status", "DEAD").gauge().value()).isZero();
        assertThat(registry.get("shop.jobs.oldest.overdue").timeGauge().value(TimeUnit.SECONDS))
                .isBetween(110.0, 600.0);
        assertThat(registry.get("shop.jobs.worker.last.poll").timeGauge().value(TimeUnit.SECONDS))
                .isBetween(0.0, 5.0);
    }

    private void job(String status, String nextAttemptAt) {
        long order = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Nadzor Test', 'nadzor@example.com', 'Ulica 1', 100.00) RETURNING id
                """, Long.class);
        orders.add(order);
        boolean finished = !status.equals("PENDING");
        jdbc.update("INSERT INTO background_jobs (type, order_id, status, next_attempt_at, last_error, finished_at) "
                + "VALUES ('ORDER_CONFIRMATION_EMAIL', ?, ?, " + nextAttemptAt + ", ?, "
                + (finished ? "now()" : "NULL") + ")",
                order, status, status.equals("DEAD") ? "SMTP down" : null);
    }
}
