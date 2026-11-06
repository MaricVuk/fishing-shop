package rs.fishingshop.jobs;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBooleanProperty("shop.jobs.worker-enabled")
class JobScheduler {

    private final JobWorker worker;

    JobScheduler(JobWorker worker) {
        this.worker = worker;
    }

    @Scheduled(fixedDelayString = "${shop.jobs.poll-interval}")
    void poll() {
        worker.runOnce();
    }
}
