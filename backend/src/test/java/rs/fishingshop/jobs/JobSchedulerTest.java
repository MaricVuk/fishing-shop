package rs.fishingshop.jobs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class JobSchedulerTest {

    private final JobWorker worker = mock(JobWorker.class);
    private final JobWorkerHeartbeat heartbeat = new JobWorkerHeartbeat();
    private final JobScheduler scheduler = new JobScheduler(worker, heartbeat);

    @Test
    void aFinishedPollRecordsTheHeartbeat() {
        Instant before = Instant.now();

        scheduler.poll();

        assertThat(heartbeat.lastPollAt()).hasValueSatisfying(at -> assertThat(at).isAfterOrEqualTo(before));
    }

    @Test
    void aFailedPollLeavesTheHeartbeatAlone() {
        when(worker.runOnce()).thenThrow(new IllegalStateException("database is down"));

        assertThatThrownBy(scheduler::poll).isInstanceOf(IllegalStateException.class);

        assertThat(heartbeat.lastPollAt()).isEmpty();
    }
}
