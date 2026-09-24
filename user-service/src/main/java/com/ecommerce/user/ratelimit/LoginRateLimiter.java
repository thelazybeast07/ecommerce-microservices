package com.ecommerce.user.ratelimit;

import com.ecommerce.user.configuration.LoginRateLimitProperties;
import com.ecommerce.user.configuration.LoginRateLimitProperties.Limit;
import com.ecommerce.user.exception.TooManyLoginAttemptsException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Limits login attempts per client IP AND per submitted email.
 *
 * <p>Either limit alone has a hole. Per-IP only: a botnet spreads the guesses across thousands of
 * machines. Per-email only: one machine tries a single common password against every account.
 * An attempt needs a token from both.
 *
 * <p>The per-email bucket is keyed on whatever email was SUBMITTED, whether or not that account
 * exists. If unknown emails were never limited, a 429 would itself reveal which addresses are
 * registered - user enumeration through a side door.
 *
 * <p>Known limitation: the buckets live in this process's memory, so running three instances
 * gives an attacker three times the attempts. A shared counter in Redis is the production answer,
 * deferred to the Redis phase.
 */
@Slf4j
@Component
public class LoginRateLimiter {

    private final Limit perIp;
    private final Limit perEmail;
    private final LongSupplier nanoClock;
    private final Cache<String, TokenBucket> ipBuckets;
    private final Cache<String, TokenBucket> emailBuckets;

    @Autowired
    public LoginRateLimiter(LoginRateLimitProperties properties) {
        this(properties, System::nanoTime);
    }

    /** Package-private so tests can drive time directly. */
    LoginRateLimiter(LoginRateLimitProperties properties, LongSupplier nanoClock) {
        this.perIp = properties.perIp();
        this.perEmail = properties.perEmail();
        this.nanoClock = nanoClock;
        this.ipBuckets = newCache(properties.maxTrackedKeys(), perIp);
        this.emailBuckets = newCache(properties.maxTrackedKeys(), perEmail);
    }

    /**
     * Takes one attempt from both buckets, or throws.
     *
     * <p>Called BEFORE the database lookup and before BCrypt, so a refused attempt costs the server
     * almost nothing - the attacker cannot use the limiter itself to burn our CPU.
     *
     * <p>The IP token is taken first and is not returned if the email bucket then refuses. That is
     * deliberate: a script hammering a locked account should run down its IP budget too.
     *
     * @param clientIp the caller's address
     * @param email    the submitted email, already normalised (trimmed, lower-case)
     * @throws TooManyLoginAttemptsException if either bucket is empty
     */
    public void checkAndConsume(String clientIp, String email) {
        TokenBucket ipBucket = ipBuckets.get(clientIp, key -> newBucket(perIp));
        if (!ipBucket.tryConsume()) {
            log.warn("Login rate limit hit for ip={}", clientIp);
            throw new TooManyLoginAttemptsException(ipBucket.secondsUntilNextToken());
        }

        TokenBucket emailBucket = emailBuckets.get(email, key -> newBucket(perEmail));
        if (!emailBucket.tryConsume()) {
            // Log the IP, not the email: logs are read by more people than the database is.
            log.warn("Login rate limit hit for an email from ip={}", clientIp);
            throw new TooManyLoginAttemptsException(emailBucket.secondsUntilNextToken());
        }
    }

    /**
     * A successful login forgives earlier typos for that email. The IP bucket is left alone:
     * one correct password must not refill the budget for everyone behind the same address.
     */
    public void recordSuccess(String email) {
        TokenBucket bucket = emailBuckets.getIfPresent(email);
        if (bucket != null) {
            bucket.reset();
        }
    }

    private TokenBucket newBucket(Limit limit) {
        return new TokenBucket(limit.capacity(), limit.refillEvery(), nanoClock);
    }

    /**
     * Bounded so random emails cannot exhaust memory, and idle entries expire.
     *
     * <p>The expiry must be LONGER than the time a bucket takes to fill completely. Otherwise an
     * attacker could simply wait for a drained bucket to be evicted and get a brand-new full one
     * sooner than refilling would allow. With a safe margin, eviction only ever drops buckets that
     * would already be full.
     */
    private static Cache<String, TokenBucket> newCache(long maxSize, Limit limit) {
        Duration expiry = limit.timeToFill().multipliedBy(3).plusMinutes(5);
        return Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterAccess(expiry)
                .build();
    }
}
