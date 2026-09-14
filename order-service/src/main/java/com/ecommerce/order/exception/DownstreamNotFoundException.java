package com.ecommerce.order.exception;

/**
 * A downstream service returned 404. Thrown by the Feign error decoder and caught in the
 * service layer, which turns it into a 422 with a message naming what was missing.
 */
public class DownstreamNotFoundException extends RuntimeException {

    public DownstreamNotFoundException(String message) {
        super(message);
    }
}
