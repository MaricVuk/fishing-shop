package rs.fishingshop.admin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import rs.fishingshop.audit.AuditAction;
import rs.fishingshop.audit.AuditActor;
import rs.fishingshop.audit.AuditEntityType;
import rs.fishingshop.audit.AuditService;
import rs.fishingshop.catalog.PageResponse;
import rs.fishingshop.jobs.JobStatus;
import rs.fishingshop.jobs.JobType;

@Service
class AdminJobService {

    private static final String LOCK_TIMEOUT = "3s";
    private static final String COLUMNS =
            "id, type, order_id, status, attempts, next_attempt_at, last_error, created_at, finished_at";

    private final JdbcTemplate jdbc;
    private final AuditService audit;

    AdminJobService(JdbcTemplate jdbc, AuditService audit) {
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    AdminJobOverview overview(JobStatus status, JobType type, int page, int size) {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus each : JobStatus.values()) {
            counts.put(each, 0L);
        }
        jdbc.query("SELECT status, count(*) AS jobs FROM background_jobs GROUP BY status",
                rs -> {
                    counts.put(JobStatus.valueOf(rs.getString("status")), rs.getLong("jobs"));
                });
        OffsetDateTime oldest = jdbc.queryForObject("""
                SELECT min(next_attempt_at) FROM background_jobs
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                """, OffsetDateTime.class);

        StringBuilder where = new StringBuilder(" WHERE TRUE");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND status = ?");
            args.add(status.name());
        }
        if (type != null) {
            where.append(" AND type = ?");
            args.add(type.name());
        }
        long total = jdbc.queryForObject("SELECT count(*) FROM background_jobs" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<AdminJobRow> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM background_jobs" + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                AdminJobRow::from, pageArgs.toArray());
        int pages = (int) ((total + size - 1) / size);
        return new AdminJobOverview(counts, oldest == null ? null : oldest.toInstant(),
                new PageResponse<>(rows, page, size, total, pages));
    }

    @Transactional
    AdminJobRow retry(AuditActor actor, long id) {
        jdbc.execute("SET LOCAL lock_timeout = '" + LOCK_TIMEOUT + "'");
        AdminJobRow current = jdbc.query("SELECT " + COLUMNS + " FROM background_jobs WHERE id = ? FOR UPDATE",
                AdminJobRow::from, id).stream().findFirst().orElseThrow(() -> new JobNotFoundException(id));
        if (current.status() != JobStatus.DEAD) {
            throw new JobNotRetryableException(id, current.status());
        }
        AdminJobRow retried = jdbc.queryForObject("""
                UPDATE background_jobs
                SET status = 'PENDING', attempts = 0, next_attempt_at = now(), finished_at = NULL
                WHERE id = ?
                RETURNING
                """ + COLUMNS, AdminJobRow::from, id);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("status", current.status());
        before.put("attempts", current.attempts());
        before.put("lastError", current.lastError());
        before.put("finishedAt", current.finishedAt() == null ? null : current.finishedAt().toString());
        audit.record(actor, AuditAction.JOB_RETRIED, AuditEntityType.JOB, id, before,
                Map.of("status", JobStatus.PENDING, "type", current.type(), "orderId", current.orderId()));
        return retried;
    }
}
