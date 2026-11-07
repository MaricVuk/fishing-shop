package rs.fishingshop.admin;

import java.time.Instant;
import java.util.Map;
import rs.fishingshop.catalog.PageResponse;
import rs.fishingshop.jobs.JobStatus;

record AdminJobOverview(Map<JobStatus, Long> counts, Instant oldestOverdueSince, PageResponse<AdminJobRow> jobs) {
}
