package rs.fishingshop.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
class LoginAttemptLimiter {

    static final int MAX_TRACKED_KEYS = 10_000;

    private static final int MAX_EMAIL_LENGTH = 100;
    private static final Pattern PLAIN_EMAIL = Pattern.compile("[\\x21-\\x7e]+");
    private static final String SHARED_BUCKET = "unsupported";

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
        String normalized = normalize(email);
        Duration blocked = remaining("pair:" + normalized + "|" + address, properties.maxFailuresPerPair());
        blocked = longer(blocked, remaining("email:" + normalized, properties.maxFailuresPerEmail()));
        return longer(blocked, remaining("address:" + address, properties.maxFailuresPerAddress()));
    }

    void recordFailure(String email, String address) {
        recordPasswordFailure(email, address);
        increment("address:" + address, clock.instant());
    }

    // A wrong current password comes from a logged in employee, so it must not block everybody
    // behind the same address.
    void recordPasswordFailure(String email, String address) {
        Instant now = clock.instant();
        evictIfFull(now);
        String normalized = normalize(email);
        increment("pair:" + normalized + "|" + address, now);
        increment("email:" + normalized, now);
    }

    void recordSuccess(String email, String address) {
        windows.remove("pair:" + normalize(email) + "|" + address);
    }

    int trackedKeys() {
        return windows.size();
    }

    // The account lookup in the database lower-cases with its own rules, which differ from Java for
    // some characters (for example U+0130). Anything that is not plain ASCII therefore cannot get a
    // key of its own, or each spelling would get its own budget of attempts.
    private static String normalize(String email) {
        String trimmed = email == null ? "" : email.trim();
        if (trimmed.length() > MAX_EMAIL_LENGTH || !PLAIN_EMAIL.matcher(trimmed).matches()) {
            return SHARED_BUCKET;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private void increment(String key, Instant now) {
        windows.compute(key, (ignored, window) -> window == null || expired(window, now)
                ? new Window(now, 1)
                : new Window(window.startedAt(), window.failures() + 1));
    }

    private void evictIfFull(Instant now) {
        if (windows.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        windows.values().removeIf(window -> expired(window, now));
        int excess = windows.size() - MAX_TRACKED_KEYS + MAX_TRACKED_KEYS / 10;
        if (excess > 0) {
            windows.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getValue().startedAt()))
                    .limit(excess)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(windows::remove);
        }
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

    private static Duration longer(Duration first, Duration second) {
        return first.compareTo(second) >= 0 ? first : second;
    }
}
