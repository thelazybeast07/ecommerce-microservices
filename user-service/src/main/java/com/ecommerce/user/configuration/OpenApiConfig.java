package com.ecommerce.user.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("User Service API")
                .version("v1")
                .description("Customer registration, profile and address management for the e-commerce platform."));
    }
}
