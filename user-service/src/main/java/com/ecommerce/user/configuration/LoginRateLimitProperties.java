package com.ecommerce.user.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for {@link com.ecommerce.user.ratelimit.LoginRateLimiter}, bound from
 * {@code login-rate-limit.*} in application.yml.
 *
 * <p>Every value has a safe default, so a missing block in application.yml degrades to sensible
 * limits instead of a NullPointerException at the first login.
 *
 * @param perIp          limit per client IP address
 * @param perEmail       limit per submitted email, whether or not that account exists
 * @param maxTrackedKeys upper bound on buckets held in memory for each of the two limits
 */
@ConfigurationProperties(prefix = "login-rate-limit")
public record LoginRateLimitProperties(Limit perIp, Limit perEmail, Long maxTrackedKeys) {

    public LoginRateLimitProperties {
        if (perIp == null) {
            perIp = new Limit(50L, Duration.ofSeconds(2));
        }
        if (perEmail == null) {
            perEmail = new Limit(5L, Duration.ofMinutes(1));
        }
        if (maxTrackedKeys == null) {
            maxTrackedKeys = 100_000L;
        }
    }

    /**
     * @param capacity    attempts available when nobody has tried for a while
     * @param refillEvery how long it takes for ONE attempt to become available again
     */
    public record Limit(Long capacity, Duration refillEvery) {

        public Limit {
            if (capacity == null || capacity < 1) {
                throw new IllegalArgumentException("capacity must be at least 1");
            }
            if (refillEvery == null || refillEvery.isNegative() || refillEvery.isZero()) {
                throw new IllegalArgumentException("refill-every must be a positive duration");
            }
        }

        /** How long an empty bucket takes to fill completely. */
        public Duration timeToFill() {
            return refillEvery.multipliedBy(capacity);
        }
    }
}
