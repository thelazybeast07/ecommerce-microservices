package com.ecommerce.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import com.ecommerce.order.configuration.JwtProperties;

/**
 * {@code @EnableFeignClients} scans for {@code @FeignClient} interfaces and creates an HTTP
 * client implementation for each at startup.
 */
@SpringBootApplication
@EnableFeignClients
@EnableConfigurationProperties(JwtProperties.class)
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
