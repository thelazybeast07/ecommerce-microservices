package com.ecommerce.order.exception;

/** One entry in the "errors" array of a 400 validation response. */
public record FieldValidationError(String field, String message) {
}
