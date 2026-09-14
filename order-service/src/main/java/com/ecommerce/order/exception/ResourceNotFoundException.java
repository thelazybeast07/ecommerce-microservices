package com.ecommerce.order.exception;

/** Mapped to HTTP 404: the resource named in the URL does not exist. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resourceName, Object id) {
        return new ResourceNotFoundException("%s with id '%s' was not found".formatted(resourceName, id));
    }
}
