package com.ecommerce.user.exception;

import lombok.Getter;

/**
 * Mapped to HTTP 429 with a Retry-After header.
 *
 * <p>The message is the same whichever limit was hit and whether or not the email exists, so the
 * response reveals nothing about which accounts are registered.
 */
@Getter
public class TooManyLoginAttemptsException extends RuntimeException {

    private final long retryAfterSeconds;

    public TooManyLoginAttemptsException(long retryAfterSeconds) {
        super("Too many login attempts. Try again in %d seconds.".formatted(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
