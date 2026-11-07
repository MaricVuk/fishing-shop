package rs.fishingshop.jobs;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
public class JobWorkerHeartbeat {

    private final AtomicReference<Instant> lastPollAt = new AtomicReference<>();

    public void recordPoll() {
        lastPollAt.set(Instant.now());
    }

    public Optional<Instant> lastPollAt() {
        return Optional.ofNullable(lastPollAt.get());
    }
}
