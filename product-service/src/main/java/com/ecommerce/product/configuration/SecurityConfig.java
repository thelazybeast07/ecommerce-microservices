package com.ecommerce.product.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * The catalogue is the one place where reads are genuinely public.
 *
 * <p>People browse a shop before they have an account, and requiring a login to see prices
 * would be a business decision, not a security one. Writes are a different matter: only staff
 * change the catalogue.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final SecurityProblemHandlers problemHandlers;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // --- public: browsing the shop needs no account ---
                // NOTE the ordering: /products/lookup is listed BEFORE /products/** so the
                // more specific rule wins. Rules are evaluated top to bottom, first match
                // applies, so a broad pattern placed first would swallow everything under it.
                .requestMatchers(HttpMethod.GET, "/api/v1/products/lookup").authenticated()
                .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/categories", "/api/v1/categories/**").permitAll()

                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                // --- everything else: default deny. Writes are additionally ADMIN-only,
                //     enforced by @PreAuthorize on the controller methods. ---
                .anyRequest().authenticated())

            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(problemHandlers.authenticationEntryPoint())
                .accessDeniedHandler(problemHandlers.accessDeniedHandler()))

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
