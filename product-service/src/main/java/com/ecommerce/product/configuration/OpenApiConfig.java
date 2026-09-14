package com.ecommerce.product.configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI productServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Product Service API")
                .version("v1")
                .description("Product catalogue, categories, search and filtering for the e-commerce platform."));
    }
}
