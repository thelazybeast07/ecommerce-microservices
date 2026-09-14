package com.ecommerce.user.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Passwords are never stored in plain text, even in Phase 1.
 *
 * <p>Only spring-security-crypto is on the classpath, so this gives us BCrypt hashing without
 * enabling Spring Security's web filter chain. When authentication is introduced (OAuth2/JWT,
 * possibly an external identity provider), this bean may move or disappear.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
