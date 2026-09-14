package com.ecommerce.product.exception;

/** Mapped to HTTP 409: the operation is not allowed in the resource's current state. */
public class InvalidResourceStateException extends RuntimeException {

    public InvalidResourceStateException(String message) {
        super(message);
    }
}
