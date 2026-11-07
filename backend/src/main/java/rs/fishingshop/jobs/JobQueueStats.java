package rs.fishingshop.jobs;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class JobQueueStats {

    private final JdbcTemplate jdbc;

    JobQueueStats(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JobQueueSnapshot snapshot() {
        return new JobQueueSnapshot(countsByStatus(), oldestOverdueSince());
    }

    Map<JobStatus, Long> countsByStatus() {
        Map<JobStatus, Long> counts = new EnumMap<>(JobStatus.class);
        for (JobStatus each : JobStatus.values()) {
            counts.put(each, 0L);
        }
        jdbc.query("SELECT status, count(*) AS jobs FROM background_jobs GROUP BY status",
                rs -> {
                    counts.put(JobStatus.valueOf(rs.getString("status")), rs.getLong("jobs"));
                });
        return counts;
    }

    Instant oldestOverdueSince() {
        OffsetDateTime oldest = jdbc.queryForObject("""
                SELECT min(next_attempt_at) FROM background_jobs
                WHERE status = 'PENDING' AND next_attempt_at <= now()
                """, OffsetDateTime.class);
        return oldest == null ? null : oldest.toInstant();
    }
}
