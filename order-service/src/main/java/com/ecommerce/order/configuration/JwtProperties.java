package com.ecommerce.order.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Verification settings only. Note there is no expiry here: this service does not issue
 * tokens, so it has no say in how long they last - it only checks the expiry already inside.
 */
@EnableConfigurationProperties(JwtProperties.class)
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, String issuer) {
}
