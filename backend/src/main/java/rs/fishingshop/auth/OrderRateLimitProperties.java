package rs.fishingshop.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.orders")
record OrderRateLimitProperties(int maxPerAddress, Duration rateWindow) {
}
