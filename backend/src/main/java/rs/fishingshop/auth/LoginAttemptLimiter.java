package rs.fishingshop.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptLimiter {

    private static final int MAX_TRACKED_KEYS = 10_000;

    private record Window(Instant startedAt, int failures) {
    }

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final AuthProperties properties;
    private final Clock clock;

    LoginAttemptLimiter(AuthProperties properties) {
        this.properties = properties;
        this.clock = Clock.systemUTC();
    }

    Duration blockedFor(String email, String address) {
        Duration byEmail = remaining(emailKey(email), properties.maxFailuresPerEmail());
        Duration byAddress = remaining(addressKey(address), properties.maxFailuresPerAddress());
        return byEmail.compareTo(byAddress) >= 0 ? byEmail : byAddress;
    }

    void recordFailure(String email, String address) {
        Instant now = clock.instant();
        if (windows.size() > MAX_TRACKED_KEYS) {
            windows.values().removeIf(window -> expired(window, now));
        }
        increment(emailKey(email), now);
        increment(addressKey(address), now);
    }

    void recordSuccess(String email) {
        windows.remove(emailKey(email));
    }

    private void increment(String key, Instant now) {
        windows.compute(key, (ignored, window) -> window == null || expired(window, now)
                ? new Window(now, 1)
                : new Window(window.startedAt(), window.failures() + 1));
    }

    private Duration remaining(String key, int limit) {
        Window window = windows.get(key);
        Instant now = clock.instant();
        if (window == null || expired(window, now) || window.failures() < limit) {
            return Duration.ZERO;
        }
        return Duration.between(now, window.startedAt().plus(properties.failureWindow()));
    }

    private boolean expired(Window window, Instant now) {
        return !window.startedAt().plus(properties.failureWindow()).isAfter(now);
    }

    private static String emailKey(String email) {
        return "email:" + (email == null ? "" : email.trim().toLowerCase(Locale.ROOT));
    }

    private static String addressKey(String address) {
        return "address:" + address;
    }
}
