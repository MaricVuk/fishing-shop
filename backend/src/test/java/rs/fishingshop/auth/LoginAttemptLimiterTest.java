package rs.fishingshop.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class LoginAttemptLimiterTest {

    private static LoginAttemptLimiter limiter(int perPair, int perEmail, int perAddress) {
        return new LoginAttemptLimiter(new AuthProperties(
                true, Duration.ofHours(8), perPair, perEmail, perAddress, Duration.ofMinutes(15)));
    }

    @Test
    void aPairIsBlockedAfterItsLimitButAnotherAddressIsNot() {
        LoginAttemptLimiter limiter = limiter(3, 100, 100);

        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a@b.test", "1.1.1.1");
        }

        assertThat(limiter.blockedFor("a@b.test", "1.1.1.1")).isPositive();
        assertThat(limiter.blockedFor("A@B.test ", "1.1.1.1")).isPositive();
        assertThat(limiter.blockedFor("a@b.test", "2.2.2.2")).isZero();
    }

    @Test
    void failuresFromManyAddressesTripTheEmailLimit() {
        LoginAttemptLimiter limiter = limiter(3, 5, 100);

        for (int i = 0; i < 5; i++) {
            limiter.recordFailure("a@b.test", "9.9.9." + i);
        }

        assertThat(limiter.blockedFor("a@b.test", "8.8.8.8")).isPositive();
    }

    @Test
    void aSuccessClearsOnlyThePair() {
        LoginAttemptLimiter limiter = limiter(3, 100, 100);
        for (int i = 0; i < 3; i++) {
            limiter.recordFailure("a@b.test", "1.1.1.1");
        }

        limiter.recordSuccess("a@b.test", "1.1.1.1");

        assertThat(limiter.blockedFor("a@b.test", "1.1.1.1")).isZero();
    }

    @Test
    void wrongCurrentPasswordsDoNotCountAgainstTheAddress() {
        LoginAttemptLimiter limiter = limiter(100, 100, 3);

        for (int i = 0; i < 10; i++) {
            limiter.recordPasswordFailure("a@b.test", "1.1.1.1");
        }

        assertThat(limiter.blockedFor("c@d.test", "1.1.1.1")).isZero();
    }

    @Test
    void nonAsciiAndOverlongEmailsShareOneBucketPerAddress() {
        LoginAttemptLimiter limiter = limiter(3, 100, 100);

        limiter.recordFailure("vlasİnik@b.test", "1.1.1.1");
        limiter.recordFailure("vlasİİnik@b.test", "1.1.1.1");
        limiter.recordFailure("x".repeat(500) + "@b.test", "1.1.1.1");

        assertThat(limiter.blockedFor("anythingİ@b.test", "1.1.1.1")).isPositive();
        assertThat(limiter.blockedFor("vlasinik@b.test", "1.1.1.1")).isZero();
    }

    @Test
    void theNumberOfTrackedKeysStaysBounded() {
        LoginAttemptLimiter limiter = limiter(3, 100, 100);

        for (int i = 0; i < 40_000; i++) {
            limiter.recordFailure("user" + i + "@b.test", "10.0." + (i % 250) + "." + (i / 250));
        }

        assertThat(limiter.trackedKeys()).isLessThanOrEqualTo(LoginAttemptLimiter.MAX_TRACKED_KEYS + 3);
    }
}
