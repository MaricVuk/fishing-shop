package rs.fishingshop.jobs;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.jobs")
public record JobProperties(
        boolean workerEnabled,
        Duration pollInterval,
        int batchSize,
        int maxAttempts,
        Duration baseBackoff,
        Duration maxBackoff) {
}
