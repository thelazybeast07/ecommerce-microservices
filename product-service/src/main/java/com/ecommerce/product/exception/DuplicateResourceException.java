package com.ecommerce.product.exception;

/** Mapped to HTTP 409: the resource would violate a uniqueness rule (duplicate SKU or category name). */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
