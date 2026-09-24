package com.ecommerce.user.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bucket arithmetic, with a fake clock. No sleeping: time moves when the test says so.
 */
class TokenBucketTest {

    private final AtomicLong now = new AtomicLong(0);

    private TokenBucket bucket(long capacity, Duration refillEvery) {
        return new TokenBucket(capacity, refillEvery, now::get);
    }

    private void advance(Duration d) {
        now.addAndGet(d.toNanos());
    }

    @Test
    @DisplayName("starts full, then refuses once every token is spent")
    void startsFull_thenEmpties() {
        TokenBucket b = bucket(3, Duration.ofMinutes(1));

        assertThat(b.tryConsume()).isTrue();
        assertThat(b.tryConsume()).isTrue();
        assertThat(b.tryConsume()).isTrue();
        assertThat(b.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("one token comes back per refill interval, not before")
    void refillsAtTheConfiguredRate() {
        TokenBucket b = bucket(2, Duration.ofMinutes(1));
        b.tryConsume();
        b.tryConsume();

        advance(Duration.ofSeconds(59));
        assertThat(b.tryConsume()).isFalse();

        advance(Duration.ofSeconds(1));
        assertThat(b.tryConsume()).isTrue();
        assertThat(b.tryConsume()).isFalse();
    }

    @Test
    @DisplayName("never refills beyond capacity, however long it sits idle")
    void neverExceedsCapacity() {
        TokenBucket b = bucket(5, Duration.ofSeconds(1));

        advance(Duration.ofDays(1));

        assertThat(b.availableTokens()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("reports how long to wait, rounded up to whole seconds")
    void reportsWaitTime() {
        TokenBucket b = bucket(1, Duration.ofMinutes(1));
        b.tryConsume();

        assertThat(b.secondsUntilNextToken()).isEqualTo(60);

        advance(Duration.ofSeconds(45));
        assertThat(b.secondsUntilNextToken()).isEqualTo(15);
    }

    @Test
    void reset_fillsTheBucketImmediately() {
        TokenBucket b = bucket(3, Duration.ofHours(1));
        b.tryConsume();
        b.tryConsume();
        b.tryConsume();

        b.reset();

        assertThat(b.availableTokens()).isEqualTo(3.0);
    }
}
