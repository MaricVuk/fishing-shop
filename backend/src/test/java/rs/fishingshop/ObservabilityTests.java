package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import rs.fishingshop.jobs.JobWorkerHeartbeat;

class ObservabilityTests extends CatalogTestSupport {

    @Autowired
    MeterRegistry registry;

    @Autowired
    JobWorkerHeartbeat heartbeat;

    private final List<Long> orders = new ArrayList<>();

    @BeforeEach
    void setUp() {
        removeJobs();
    }

    @AfterEach
    void removeJobs() {
        jdbc.update("DELETE FROM background_jobs");
        orders.forEach(order -> jdbc.update("DELETE FROM orders WHERE id = ?", order));
        orders.clear();
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
