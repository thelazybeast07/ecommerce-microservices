package com.ecommerce.order.exception;

/** A downstream service is unavailable, timed out, or returned 5xx. Mapped to HTTP 503. */
public class DownstreamServiceException extends RuntimeException {

    public DownstreamServiceException(String message) {
        super(message);
    }

    public DownstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
