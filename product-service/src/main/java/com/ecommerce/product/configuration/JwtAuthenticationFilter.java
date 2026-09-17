package com.ecommerce.product.configuration;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Identical in shape to user-service's filter. Duplicated rather than shared: a common library
 * of security code would couple all three services' release cycles, and this class is small
 * enough that copying it is cheaper than the coupling.
 *
 * <p>It only IDENTIFIES the caller - it never rejects. SecurityConfig decides what an
 * unauthenticated request may still do, which here includes browsing the catalogue.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.TokenPayload payload = jwtService.parseToken(token);

                var authentication = new UsernamePasswordAuthenticationToken(
                        payload.userId(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + payload.role())));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException ex) {
                log.debug("Rejecting token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
