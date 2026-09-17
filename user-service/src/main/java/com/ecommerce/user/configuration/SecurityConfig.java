package com.ecommerce.user.configuration;

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
 * Decides which requests need a token.
 *
 * <p>{@code @EnableMethodSecurity} switches on {@code @PreAuthorize} so individual methods can
 * add finer rules ("only an admin", "only your own record") on top of the path rules here.
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
            // CSRF protects browser form posts that rely on cookies. We use no cookies and no
            // sessions - the token travels in a header that a malicious site cannot make the
            // browser attach - so the protection has nothing to protect and only breaks clients.
            .csrf(csrf -> csrf.disable())

            // STATELESS: never create an HttpSession. Every request must carry its own proof.
            // This is what allows several copies of this service to run with no shared session
            // store - any copy can serve any request.
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // --- public: you cannot require a token to obtain a token ---
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // --- public: a new customer has no account yet ---
                .requestMatchers(HttpMethod.POST, "/api/v1/customers").permitAll()

                // --- public for local development only. In production these would be
                //     restricted to an internal network or removed entirely. ---
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Container and orchestrator probes call these with no credentials.
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()

                // --- everything else needs a valid token ---
                // DEFAULT DENY, and the order matters: this is the last rule, so any endpoint
                // added later is protected unless someone deliberately opens it. The opposite
                // arrangement ships new endpoints wide open, silently.
                .anyRequest().authenticated())

            // Our filter runs before Spring's username/password filter so that by the time
            // authorization is evaluated, the SecurityContext is already populated.
            // Replace Spring Security's default empty/HTML rejections with problem JSON,
            // so authentication failures look like every other error in the platform.
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(problemHandlers.authenticationEntryPoint())
                .accessDeniedHandler(problemHandlers.accessDeniedHandler()))

            // Our filter runs before Spring's username/password filter so that by the time
            // authorization is evaluated, the SecurityContext is already populated.
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
