package com.ecommerce.order.exception;

/**
 * A downstream service refused the forwarded token (401 or 403).
 *
 * <p>Mapped to 401, not 500: this service is working correctly - the caller's credentials were
 * not accepted further down the chain, most often because the token expired mid-request.
 */
public class DownstreamAuthException extends RuntimeException {

    public DownstreamAuthException(String message) {
        super(message);
    }
}
