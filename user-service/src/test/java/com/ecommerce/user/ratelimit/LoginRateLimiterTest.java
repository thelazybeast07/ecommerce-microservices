package com.ecommerce.user.ratelimit;

import com.ecommerce.user.configuration.LoginRateLimitProperties;
import com.ecommerce.user.configuration.LoginRateLimitProperties.Limit;
import com.ecommerce.user.exception.TooManyLoginAttemptsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private final AtomicLong now = new AtomicLong(0);

    /** Small numbers so each rule is easy to see: 10 per IP, 3 per email. */
    private final LoginRateLimiter limiter = new LoginRateLimiter(
            new LoginRateLimitProperties(
                    new Limit(10L, Duration.ofSeconds(2)),
                    new Limit(3L, Duration.ofMinutes(1)),
                    1_000L),
            now::get);

    @Test
    @DisplayName("an email gets its allowance, then is refused with a wait time")
    void perEmail_limitApplies() {
        for (int i = 0; i < 3; i++) {
            limiter.checkAndConsume("198.51.100.1", "jane@example.com");
        }

        assertThatThrownBy(() -> limiter.checkAndConsume("198.51.100.1", "jane@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class)
                .satisfies(ex -> assertThat(((TooManyLoginAttemptsException) ex).getRetryAfterSeconds())
                        .isEqualTo(60));
    }

    @Test
    @DisplayName("an email that does not exist is limited exactly like one that does - no enumeration")
    void perEmail_appliesToUnknownEmailsToo() {
        // The limiter never looks accounts up; it only counts the submitted string.
        for (int i = 0; i < 3; i++) {
            limiter.checkAndConsume("198.51.100.1", "nobody-at-all@example.com");
        }

        assertThatThrownBy(() -> limiter.checkAndConsume("198.51.100.1", "nobody-at-all@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("a botnet: many IPs cannot get more than the email's allowance for one account")
    void perEmail_holdsAcrossDifferentIps() {
        limiter.checkAndConsume("10.0.0.1", "jane@example.com");
        limiter.checkAndConsume("10.0.0.2", "jane@example.com");
        limiter.checkAndConsume("10.0.0.3", "jane@example.com");

        assertThatThrownBy(() -> limiter.checkAndConsume("10.0.0.4", "jane@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("password spraying: one IP cannot try one password against every account")
    void perIp_holdsAcrossDifferentEmails() {
        for (int i = 0; i < 10; i++) {
            limiter.checkAndConsume("198.51.100.9", "user" + i + "@example.com");
        }

        assertThatThrownBy(() -> limiter.checkAndConsume("198.51.100.9", "user-11@example.com"))
                .isInstanceOf(TooManyLoginAttemptsException.class);
    }

    @Test
    @DisplayName("a successful login forgives earlier typos for that email")
    void success_resetsTheEmailBucket() {
        limiter.checkAndConsume("198.51.100.1", "jane@example.com");
        limiter.checkAndConsume("198.51.100.1", "jane@example.com");
        limiter.checkAndConsume("198.51.100.1", "jane@example.com");

        limiter.recordSuccess("jane@example.com");

        assertThatNoException().isThrownBy(
                () -> limiter.checkAndConsume("198.51.100.1", "jane@example.com"));
    }

    @Test
    @DisplayName("after waiting, attempts become available again")
    void refillsOverTime() {
        for (int i = 0; i < 3; i++) {
            limiter.checkAndConsume("198.51.100.1", "jane@example.com");
        }

        now.addAndGet(Duration.ofMinutes(1).toNanos());

        assertThatNoException().isThrownBy(
                () -> limiter.checkAndConsume("198.51.100.1", "jane@example.com"));
    }
}
