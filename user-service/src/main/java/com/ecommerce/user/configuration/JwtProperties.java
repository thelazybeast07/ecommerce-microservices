package com.ecommerce.user.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding of the {@code jwt.*} settings. A typo in application.yml then fails at
 * startup instead of silently leaving the secret null until the first login attempt.
 *
 * @param secret        HMAC signing key; at least 32 characters for HS256
 * @param expiryMinutes how long an issued token stays valid
 * @param issuer        recorded in the token and checked when it is verified
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long expiryMinutes, String issuer) {
}
