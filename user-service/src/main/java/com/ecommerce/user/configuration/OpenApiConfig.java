package com.ecommerce.user.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    /**
     * Declaring the scheme here is what puts the "Authorize" button in Swagger UI. Without it
     * every protected endpoint returns 401 from the try-it-out panel and looks broken.
     */
    @Bean
    public OpenAPI userServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("User Service API")
                        .version("v1")
                        .description("""
                                Customer registration, profile and address management.

                                Most endpoints need a token: call POST /api/v1/auth/login, copy the
                                accessToken, then press Authorize above and paste it in."""))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Paste the accessToken value. Swagger adds the \"Bearer \" prefix.")))
                // Applies the scheme to every operation by default. Endpoints that are public
                // still work without a token; this only tells Swagger to offer the header.
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
