package com.ecommerce.user.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data JPA auditing so {@code @CreatedDate} and {@code @LastModifiedDate}
 * are filled in automatically.
 *
 * <p>This lives in its own class rather than on {@code UserServiceApplication} on purpose:
 * web-slice tests ({@code @WebMvcTest}) always load the main application class but do not
 * start JPA, and {@code @EnableJpaAuditing} there would make them fail.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfig {
}
