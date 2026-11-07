package rs.fishingshop.jobs;

public interface JobHandler {

    JobType type();

    // Runs inside the worker's transaction and may run more than once for the same job, so it must be
    // idempotent. Throwing rolls back everything the handler wrote.
    void handle(ClaimedJob job);
}
