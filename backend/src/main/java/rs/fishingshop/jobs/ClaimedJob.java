package rs.fishingshop.jobs;

public record ClaimedJob(long id, JobType type, long orderId, int attempts) {
}
