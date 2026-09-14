package com.ecommerce.product.dto;

/** Shared regular expressions, so the same rule is not copied into several DTOs. */
final class ValidationPatterns {

    /** ISO 4217 currency code, e.g. USD, CAD, INR. */
    static final String CURRENCY = "^[A-Z]{3}$";
    static final String CURRENCY_MESSAGE = "must be an ISO 4217 currency code, e.g. USD";

    /** Uppercase letters, digits and hyphens, e.g. TSHIRT-BLK-M. */
    static final String SKU = "^[A-Z0-9-]{3,64}$";
    static final String SKU_MESSAGE = "must be 3-64 characters of uppercase letters, digits or hyphens";

    private ValidationPatterns() {
    }
}
