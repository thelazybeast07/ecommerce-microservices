package com.ecommerce.user.configuration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link LoginRateLimitProperties}.
 *
 * <p>Its own small class rather than an extra annotation on some unrelated configuration:
 * {@code @EnableConfigurationProperties} only works on a {@code @Configuration} class, and tying
 * rate-limit settings to, say, the Swagger config would be a coupling nobody would guess at.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LoginRateLimitProperties.class)
public class RateLimitConfig {
}
