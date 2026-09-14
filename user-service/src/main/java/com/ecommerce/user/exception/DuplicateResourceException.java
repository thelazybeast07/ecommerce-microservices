package com.ecommerce.user.exception;

/** Mapped to HTTP 409: the resource would violate a uniqueness rule. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
