package com.ecommerce.user.exception;

/** Mapped to HTTP 404 by {@link GlobalExceptionHandler}. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceName, Object id) {
        return new ResourceNotFoundException("%s with id '%s' was not found".formatted(resourceName, id));
    }
}
