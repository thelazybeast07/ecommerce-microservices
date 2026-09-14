package com.ecommerce.order.exception;

/** Mapped to HTTP 409: the order cannot make this transition from its current status. */
public class InvalidOrderStateException extends RuntimeException {

    public InvalidOrderStateException(String message) {
        super(message);
    }
}
