package rs.fishingshop.jobs;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public class JobWorker {

    private static final Logger log = LoggerFactory.getLogger(JobWorker.class);
    private static final int MAX_ERROR_LENGTH = 2000;
    private static final int MAX_BACKOFF_SHIFT = 30;

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Map<JobType, JobHandler> handlers = new EnumMap<>(JobType.class);
    private final JobProperties properties;

    public JobWorker(JdbcTemplate jdbc, TransactionTemplate transactions, List<JobHandler> handlers,
            JobProperties properties) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.properties = properties;
        for (JobHandler handler : handlers) {
            if (this.handlers.put(handler.type(), handler) != null) {
                throw new IllegalStateException("Two handlers for job type " + handler.type());
            }
        }
        for (JobType type : JobType.values()) {
            if (!this.handlers.containsKey(type)) {
                throw new IllegalStateException("No handler for job type " + type);
            }
        }
    }

    public int runOnce() {
        int handled = 0;
        while (handled < properties.batchSize() && runOne()) {
            handled++;
        }
        return handled;
    }

    private boolean runOne() {
        ClaimedJob[] claimed = new ClaimedJob[1];
        try {
            return Boolean.TRUE.equals(transactions.execute(tx -> {
                claimed[0] = claimNext();
                if (claimed[0] == null) {
                    return false;
                }
                handlers.get(claimed[0].type()).handle(claimed[0]);
                markDone(claimed[0]);
                return true;
            }));
        } catch (RuntimeException failure) {
            if (claimed[0] == null) {
                throw failure;
            }
            recordFailure(claimed[0].id(), failure);
            return true;
        }
    }

    private ClaimedJob claimNext() {
        return jdbc.query("""
                SELECT id, type, order_id, attempts FROM background_jobs
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                ORDER BY next_attempt_at, id
                LIMIT 1
                FOR UPDATE SKIP LOCKED
                """, JobWorker::toClaimedJob)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void markDone(ClaimedJob job) {
        log.info("Job {} ({}, order {}) done", job.id(), job.type(), job.orderId());
        jdbc.update("UPDATE background_jobs SET status = 'DONE', finished_at = now() WHERE id = ?", job.id());
    }

    private void markFailed(ClaimedJob job, RuntimeException failure) {
        int attempts = job.attempts() + 1;
        String error = describe(failure);
        if (attempts >= properties.maxAttempts() || failure instanceof PermanentJobFailure) {
            log.error("Job {} ({}, order {}) is dead after {} attempts: {}",
                    job.id(), job.type(), job.orderId(), attempts, error);
            jdbc.update("""
                    UPDATE background_jobs
                    SET status = 'DEAD', attempts = ?, last_error = ?, finished_at = now()
                    WHERE id = ?
                    """, attempts, error, job.id());
            return;
        }
        long delayMillis = backoff(attempts).toMillis();
        log.warn("Job {} ({}, order {}) failed, attempt {} of {}, next try in {} ms: {}",
                job.id(), job.type(), job.orderId(), attempts, properties.maxAttempts(), delayMillis, error);
        jdbc.update("""
                UPDATE background_jobs
                SET attempts = ?, last_error = ?, next_attempt_at = now() + ? * interval '1 millisecond'
                WHERE id = ?
                """, attempts, error, delayMillis, job.id());
    }

    // The failed transaction is rolled back first, so another worker may retry the job before this
    // records the failure. SKIP LOCKED leaves the count to that worker instead of waiting for it.
    private void recordFailure(long jobId, RuntimeException failure) {
        transactions.executeWithoutResult(tx -> jdbc.query("""
                SELECT id, type, order_id, attempts FROM background_jobs
                WHERE id = ? AND status = 'PENDING'
                FOR UPDATE SKIP LOCKED
                """, JobWorker::toClaimedJob, jobId)
                .forEach(job -> markFailed(job, failure)));
    }

    Duration backoff(int attempts) {
        int shift = Math.min(attempts - 1, MAX_BACKOFF_SHIFT);
        Duration exponential = properties.baseBackoff().multipliedBy(1L << shift);
        Duration capped = exponential.compareTo(properties.maxBackoff()) > 0 ? properties.maxBackoff() : exponential;
        long half = capped.toMillis() / 2;
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(half + 1));
    }

    private static ClaimedJob toClaimedJob(ResultSet rs, int row) throws SQLException {
        return new ClaimedJob(
                rs.getLong("id"), JobType.valueOf(rs.getString("type")), rs.getLong("order_id"), rs.getInt("attempts"));
    }

    private static String describe(RuntimeException failure) {
        String text = (failure.getClass().getSimpleName() + ": " + failure.getMessage())
                .replaceAll("\\p{Cntrl}", " ");
        return text.length() > MAX_ERROR_LENGTH ? text.substring(0, MAX_ERROR_LENGTH) : text;
    }
}
