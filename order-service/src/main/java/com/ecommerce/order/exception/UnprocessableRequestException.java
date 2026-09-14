package com.ecommerce.order.exception;

/**
 * Mapped to HTTP 422: the request is well-formed but references something unusable - an unknown
 * or inactive customer, a missing address, a discontinued product, or a cart mixing currencies.
 */
public class UnprocessableRequestException extends RuntimeException {

    public UnprocessableRequestException(String message) {
        super(message);
    }
}
