package com.ecommerce.user.exception;

/**
 * Mapped to HTTP 401. Carries a fixed message on purpose: the caller must not be able to tell
 * "unknown email" from "wrong password" from "account deactivated".
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
