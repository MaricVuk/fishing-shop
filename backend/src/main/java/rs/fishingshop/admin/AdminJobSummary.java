package rs.fishingshop.admin;

import java.time.Instant;
import java.util.Map;
import rs.fishingshop.jobs.JobStatus;

record AdminJobSummary(Map<JobStatus, Long> counts, Instant oldestOverdueSince) {
}
