package rs.fishingshop.jobs;

import java.time.Duration;
import java.time.Instant;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

// Always UP: a dead mail job or a stalled worker must not make a platform restart the shop or stop
// routing customers to it. Alerting belongs on the shop.jobs metrics.
@Component("jobs")
class JobQueueHealthIndicator implements HealthIndicator {

    private final JobQueueStats stats;
    private final JobWorkerHeartbeat heartbeat;

    JobQueueHealthIndicator(JobQueueStats stats, JobWorkerHeartbeat heartbeat) {
        this.stats = stats;
        this.heartbeat = heartbeat;
    }

    @Override
    public Health health() {
        JobQueueSnapshot snapshot = stats.snapshot();
        Instant now = Instant.now();
        Health.Builder health = Health.up();
        snapshot.counts().forEach((status, count) -> health.withDetail(status.name().toLowerCase(), count));
        health.withDetail("oldestOverdueSeconds", snapshot.oldestOverdueSince() == null
                ? 0 : Duration.between(snapshot.oldestOverdueSince(), now).toSeconds());
        heartbeat.lastPollAt().ifPresent(
                last -> health.withDetail("workerLastPollSecondsAgo", Duration.between(last, now).toSeconds()));
        return health.build();
    }
}
