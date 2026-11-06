package rs.fishingshop.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("shop.auth")
record AuthProperties(
        boolean secureCookies,
        Duration sessionMaxAge,
        int maxFailuresPerPair,
        int maxFailuresPerEmail,
        int maxFailuresPerAddress,
        Duration failureWindow) {
}
