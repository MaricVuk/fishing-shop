package rs.fishingshop;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import rs.fishingshop.jobs.ClaimedJob;
import rs.fishingshop.jobs.JobHandler;
import rs.fishingshop.jobs.JobProperties;
import rs.fishingshop.jobs.JobType;
import rs.fishingshop.jobs.JobWorker;
import rs.fishingshop.jobs.PermanentJobFailure;

class JobWorkerTests extends CatalogTestSupport {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(60);
    private static final Duration MAX_BACKOFF = Duration.ofMinutes(10);

    @Autowired
    TransactionTemplate transactions;

    private final List<Long> testOrders = new ArrayList<>();

    @BeforeEach
    @AfterEach
    void removeJobsAndTestOrders() {
        jdbc.update("DELETE FROM background_jobs");
        testOrders.forEach(order -> jdbc.update("DELETE FROM orders WHERE id = ?", order));
        testOrders.clear();
    }

    @Test
    void handlesADueJobOnceAndMarksItDone() {
        long job = pendingJob();
        ConcurrentLinkedQueue<Long> handled = new ConcurrentLinkedQueue<>();
        JobWorker worker = worker(claimed -> handled.add(claimed.id()));

        assertThat(worker.runOnce()).isEqualTo(1);
        assertThat(worker.runOnce()).isZero();

        assertThat(handled).containsExactly(job);
        assertThat(row(job)).containsEntry("status", "DONE").containsEntry("attempts", 0);
        assertThat(row(job).get("finished_at")).isNotNull();
    }

    @Test
    void skipsAJobThatIsNotDueYet() {
        long job = pendingJob();
        jdbc.update("UPDATE background_jobs SET next_attempt_at = now() + interval '1 minute' WHERE id = ?", job);
        AtomicInteger calls = new AtomicInteger();

        assertThat(worker(claimed -> calls.incrementAndGet()).runOnce()).isZero();

        assertThat(calls).hasValue(0);
        assertThat(row(job)).containsEntry("status", "PENDING");
    }

    @Test
    void aFailureSchedulesARetryWithBackoffAndKeepsTheError() {
        long job = pendingJob();
        OffsetDateTime before = dbNow();

        worker(claimed -> {
            throw new IllegalStateException("SMTP is down");
        }).runOnce();

        Map<String, Object> row = row(job);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        assertThat((String) row.get("last_error")).contains("SMTP is down");
        OffsetDateTime next = nextAttemptAt(job);
        assertThat(next).isAfterOrEqualTo(before.plus(BASE_BACKOFF.dividedBy(2)));
        assertThat(next).isBefore(dbNow().plus(BASE_BACKOFF).plusSeconds(1));
    }

    @Test
    void theLastFailedAttemptMarksTheJobDead() {
        long job = pendingJob();
        jdbc.update("UPDATE background_jobs SET attempts = ? WHERE id = ?", MAX_ATTEMPTS - 1, job);

        worker(claimed -> {
            throw new IllegalStateException("mailbox does not exist");
        }).runOnce();

        Map<String, Object> row = row(job);
        assertThat(row).containsEntry("status", "DEAD").containsEntry("attempts", MAX_ATTEMPTS);
        assertThat((String) row.get("last_error")).contains("mailbox does not exist");
        assertThat(row.get("finished_at")).isNotNull();
    }

    @Test
    void aPermanentFailureMarksTheJobDeadWithoutRetrying() {
        long job = pendingJob();

        worker(claimed -> {
            throw new PermanentJobFailure("address is not valid");
        }).runOnce();

        Map<String, Object> row = row(job);
        assertThat(row).containsEntry("status", "DEAD").containsEntry("attempts", 1);
        assertThat((String) row.get("last_error")).contains("address is not valid");
    }

    @Test
    void controlCharactersInTheErrorDoNotBreakRecordingIt() {
        long job = pendingJob();

        worker(claimed -> {
            throw new IllegalStateException("bad\u0000byte");
        }).runOnce();

        assertThat(row(job)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    }

    @Test
    void aFailedSqlStatementInTheHandlerIsStillRecordedAsAFailure() {
        long job = pendingJob();

        worker(claimed -> jdbc.queryForObject("SELECT 1 / 0", Integer.class)).runOnce();

        assertThat(row(job)).containsEntry("status", "PENDING").containsEntry("attempts", 1);
    }

    @Test
    void aTransactionThatCannotCommitIsStillRecordedAsAFailure() {
        long job = pendingJob();

        worker(claimed -> transactions.executeWithoutResult(inner -> {
            throw new IllegalStateException("inner transaction failed");
        })).runOnce();

        Map<String, Object> row = row(job);
        assertThat(row).containsEntry("status", "PENDING").containsEntry("attempts", 1);
        assertThat(nextAttemptAt(job)).isAfter(dbNow());
    }

    @Test
    void twoWorkersNeverHandleTheSameJob() throws Exception {
        List<Long> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(pendingJob());
        }
        ConcurrentLinkedQueue<Long> handled = new ConcurrentLinkedQueue<>();
        Consumer<ClaimedJob> slowHandler = claimed -> {
            handled.add(claimed.id());
            sleep(Duration.ofMillis(20));
        };
        JobWorker first = worker(slowHandler);
        JobWorker second = worker(slowHandler);

        CompletableFuture<Void> a = CompletableFuture.runAsync(() -> drain(first));
        CompletableFuture<Void> b = CompletableFuture.runAsync(() -> drain(second));
        CompletableFuture.allOf(a, b).get(60, TimeUnit.SECONDS);

        assertThat(handled).containsExactlyInAnyOrderElementsOf(jobs);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM background_jobs WHERE status = 'DONE'", Integer.class))
                .isEqualTo(20);
    }

    @Test
    void aJobHeldByAWorkerThatDiedIsPickedUpAgain() throws Exception {
        long job = pendingJob();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch never = new CountDownLatch(1);
        Long[] deadWorkerPid = new Long[1];

        CompletableFuture<Void> deadWorker = CompletableFuture.runAsync(() -> transactions.executeWithoutResult(tx -> {
            jdbc.queryForObject("SELECT id FROM background_jobs WHERE id = ? FOR UPDATE", Long.class, job);
            deadWorkerPid[0] = jdbc.queryForObject("SELECT pg_backend_pid()", Long.class);
            locked.countDown();
            try {
                never.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
        }));
        ConcurrentLinkedQueue<Long> handled = new ConcurrentLinkedQueue<>();
        JobWorker survivor = worker(claimed -> handled.add(claimed.id()));

        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(survivor.runOnce()).isZero();

            jdbc.queryForObject("SELECT pg_terminate_backend(?::int)", Boolean.class, deadWorkerPid[0]);
            awaitBackendGone(deadWorkerPid[0]);

            assertThat(survivor.runOnce()).isEqualTo(1);
        } finally {
            never.countDown();
            deadWorker.handle((ignored, failure) -> null).get(30, TimeUnit.SECONDS);
        }

        assertThat(handled).containsExactly(job);
        assertThat(row(job)).containsEntry("status", "DONE");
    }

    private JobWorker worker(Consumer<ClaimedJob> behaviour) {
        JobHandler handler = new JobHandler() {
            @Override
            public JobType type() {
                return JobType.ORDER_CONFIRMATION_EMAIL;
            }

            @Override
            public void handle(ClaimedJob job) {
                behaviour.accept(job);
            }
        };
        JobProperties properties = new JobProperties(true, Duration.ofSeconds(5), 10, MAX_ATTEMPTS, BASE_BACKOFF,
                MAX_BACKOFF);
        return new JobWorker(jdbc, transactions, List.of(handler), properties);
    }

    private long pendingJob() {
        long order = jdbc.queryForObject("""
                INSERT INTO orders (customer_name, customer_email, shipping_address, total)
                VALUES ('Posao Test', 'posao@example.com', 'Ulica 1', 100.00) RETURNING id
                """, Long.class);
        testOrders.add(order);
        return jdbc.queryForObject(
                "INSERT INTO background_jobs (type, order_id) VALUES ('ORDER_CONFIRMATION_EMAIL', ?) RETURNING id",
                Long.class, order);
    }

    private Map<String, Object> row(long job) {
        return jdbc.queryForMap(
                "SELECT status, attempts, last_error, next_attempt_at, finished_at FROM background_jobs WHERE id = ?",
                job);
    }

    private OffsetDateTime nextAttemptAt(long job) {
        return jdbc.queryForObject(
                "SELECT next_attempt_at FROM background_jobs WHERE id = ?", OffsetDateTime.class, job);
    }

    private OffsetDateTime dbNow() {
        return jdbc.queryForObject("SELECT now()", OffsetDateTime.class);
    }

    private void awaitBackendGone(long pid) {
        for (int i = 0; i < 100; i++) {
            Integer alive = jdbc.queryForObject(
                    "SELECT count(*) FROM pg_stat_activity WHERE pid = ?", Integer.class, (int) pid);
            if (alive == 0) {
                return;
            }
            sleep(Duration.ofMillis(50));
        }
        throw new AssertionError("backend " + pid + " is still alive");
    }

    private static void drain(JobWorker worker) {
        while (worker.runOnce() > 0) {
            Thread.onSpinWait();
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
