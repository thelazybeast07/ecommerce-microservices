package com.ecommerce.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.ecommerce.product.configuration.JwtProperties;

@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
