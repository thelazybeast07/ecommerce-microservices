package com.ecommerce.user.configuration;

import com.ecommerce.user.service.JwtService;
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
 * Reads the bearer token on every request and records who the caller is.
 *
 * <p>Extends {@link OncePerRequestFilter} rather than implementing Filter directly: a single
 * HTTP request can pass through the filter chain more than once internally (forwards, error
 * dispatches), and this base class guarantees the body runs exactly once per request.
 *
 * <p>Note what this filter does NOT do: it never rejects anything. If the token is missing or
 * bad, it simply leaves the request unauthenticated and passes it along. Deciding whether an
 * unauthenticated request is acceptable belongs to {@link SecurityConfig} - some endpoints
 * (login, registration) are fine without one. Keeping "identify" separate from "authorize"
 * is what lets one filter serve both public and protected endpoints.
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

        // Already authenticated on this request? Leave it alone.
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.TokenPayload payload = jwtService.parseToken(token);

                // Spring Security expects roles to be prefixed with "ROLE_". That prefix is a
                // convention its @PreAuthorize("hasRole('ADMIN')") support relies on, so we add
                // it here and keep it out of our own enum.
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + payload.role()));

                var authentication = new UsernamePasswordAuthenticationToken(
                        payload.userId(),   // the principal: the caller's UUID
                        null,               // no credentials; the token already proved it
                        authorities);
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // From here on, anything in this request can ask who the caller is.
                SecurityContextHolder.getContext().setAuthentication(authentication);

            } catch (JwtException | IllegalArgumentException ex) {
                // Expired, tampered, wrong issuer, malformed. Log the reason for us, tell the
                // caller nothing: a precise message would help someone probe the token format.
                log.debug("Rejecting token: {}", ex.getMessage());
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    /** Pulls the token out of "Authorization: Bearer &lt;token&gt;", or null if absent. */
    private static String extractToken(HttpServletRequest request) {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            String value = header.substring(PREFIX.length()).trim();
            return value.isEmpty() ? null : value;
        }
        return null;
    }
}
