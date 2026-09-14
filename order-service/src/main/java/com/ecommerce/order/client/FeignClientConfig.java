package com.ecommerce.order.client;

import feign.Logger;
import feign.codec.ErrorDecoder;
import org.springframework.context.annotation.Bean;

/**
 * Shared Feign configuration.
 *
 * <p>NOT annotated with {@code @Configuration} on purpose. Feign configuration classes are
 * referenced from {@code @FeignClient(configuration = ...)} and instantiated per client; making
 * this a global {@code @Configuration} would apply these beans to the whole application context
 * as well, which is a classic subtle bug.
 */
public class FeignClientConfig {

    @Bean
    public ErrorDecoder errorDecoder() {
        return new ServiceErrorDecoder();
    }

    /** BASIC logs method, URL, status and timing - enough to debug without leaking payloads. */
    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
