package rs.fishingshop.jobs;

import java.time.Instant;
import java.util.Map;

public record JobQueueSnapshot(Map<JobStatus, Long> counts, Instant oldestOverdueSince) {
}
