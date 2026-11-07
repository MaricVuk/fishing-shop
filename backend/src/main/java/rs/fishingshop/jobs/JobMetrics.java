package rs.fishingshop.jobs;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
class JobMetrics implements MeterBinder {

    private final JobQueueStats stats;
    private final JobWorkerHeartbeat heartbeat;

    JobMetrics(JobQueueStats stats, JobWorkerHeartbeat heartbeat) {
        this.stats = stats;
        this.heartbeat = heartbeat;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        for (JobStatus status : JobStatus.values()) {
            Gauge.builder("shop.jobs", stats, each -> each.countsByStatus().get(status))
                    .description("Background jobs by status")
                    .tag("status", status.name())
                    .register(registry);
        }
        TimeGauge.builder("shop.jobs.oldest.overdue", stats, TimeUnit.SECONDS,
                        each -> secondsSince(each.oldestOverdueSince()))
                .description("Age of the oldest pending job whose time has come, 0 when none")
                .register(registry);
        TimeGauge.builder("shop.jobs.worker.last.poll", heartbeat, TimeUnit.SECONDS,
                        each -> each.lastPollAt().map(JobMetrics::secondsSince).orElse(Double.NaN))
                .description("Time since the worker last finished a poll, NaN before the first poll")
                .register(registry);
    }

    private static double secondsSince(Instant instant) {
        return instant == null ? 0 : Duration.between(instant, Instant.now()).toMillis() / 1000.0;
    }
}
