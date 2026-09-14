package com.ecommerce.user.dto;

/** Shared regular expressions, so the same rule is not copied into several DTOs. */
final class ValidationPatterns {

    /** Loose E.164: optional '+', no leading zero, 8-15 digits. */
    static final String PHONE = "^\\+?[1-9]\\d{7,14}$";
    static final String PHONE_MESSAGE = "must be a phone number in international format, e.g. +14165550123";

    static final String COUNTRY = "^[A-Z]{2}$";
    static final String COUNTRY_MESSAGE = "must be an ISO 3166-1 alpha-2 country code, e.g. CA";

    private ValidationPatterns() {
    }
}
