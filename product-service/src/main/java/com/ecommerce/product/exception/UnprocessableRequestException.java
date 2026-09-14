package com.ecommerce.product.exception;

/**
 * Mapped to HTTP 422: the request is syntactically valid but references something unusable,
 * e.g. creating a product in a category that exists but has been deactivated. Distinct from
 * 400 (malformed) and 404 (the thing in the URL does not exist).
 */
public class UnprocessableRequestException extends RuntimeException {

    public UnprocessableRequestException(String message) {
        super(message);
    }
}
