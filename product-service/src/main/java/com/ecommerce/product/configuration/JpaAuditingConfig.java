package com.ecommerce.product.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** Same reasoning as user-service: kept separate from the main class so @WebMvcTest slices don't start JPA. */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing
public class JpaAuditingConfig {
}
