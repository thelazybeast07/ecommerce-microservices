package com.ecommerce.user.ratelimit;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * A token bucket: holds up to {@code capacity} tokens, each attempt takes one, and tokens drip
 * back in at a steady rate.
 *
 * <p>Forgiving to people, hard on scripts. Someone who mistypes a password three times still
 * has tries left, and gets more back within minutes. A script working through a password list
 * gets a short burst, then one guess per refill interval.
 *
 * <p>Refill is computed lazily from elapsed time on each call, so there is no background thread
 * and an idle bucket costs nothing but its memory.
 *
 * <p>The clock is injected so tests can move time forward without sleeping.
 */
final class TokenBucket {

    private final long capacity;
    private final long nanosPerToken;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefillNanos;

    TokenBucket(long capacity, Duration refillEvery, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.nanosPerToken = refillEvery.toNanos();
        this.nanoClock = nanoClock;
        this.tokens = capacity;
        this.lastRefillNanos = nanoClock.getAsLong();
    }

    /** Takes one token if available. Synchronized: two requests must not both take the last one. */
    synchronized boolean tryConsume() {
        refill();
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }

    /** Whole seconds until at least one token is available; never less than 1 while empty. */
    synchronized long secondsUntilNextToken() {
        refill();
        if (tokens >= 1.0) {
            return 0;
        }
        double missing = 1.0 - tokens;
        long nanos = (long) Math.ceil(missing * nanosPerToken);
        return Math.max(1, (long) Math.ceil(nanos / 1_000_000_000.0));
    }

    /** Back to full. Used when a login succeeds, so earlier typos are forgiven. */
    synchronized void reset() {
        tokens = capacity;
        lastRefillNanos = nanoClock.getAsLong();
    }

    synchronized double availableTokens() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = nanoClock.getAsLong();
        long elapsed = now - lastRefillNanos;
        if (elapsed > 0) {
            tokens = Math.min(capacity, tokens + (double) elapsed / nanosPerToken);
            lastRefillNanos = now;
        }
    }
}
