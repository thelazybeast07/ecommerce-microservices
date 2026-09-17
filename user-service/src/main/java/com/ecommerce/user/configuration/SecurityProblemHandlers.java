package com.ecommerce.user.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

/**
 * Makes Spring Security's own rejections look like every other error in the platform.
 *
 * <p>Without this, a request with no token gets Spring Security's default response: an empty
 * body, or an HTML login page. Every other failure in this service returns RFC 9457 problem
 * JSON, and a client should not have to special-case authentication.
 *
 * <p>These two run when the request is rejected by the FILTER CHAIN, before any controller is
 * reached. Rejections raised by {@code @PreAuthorize} happen later, inside the controller call,
 * and are handled by GlobalExceptionHandler instead.
 */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandlers {

    private final ObjectMapper objectMapper;

    /** No credentials, or credentials we could not accept -> 401. */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid bearer token is required. Obtain one from POST /api/v1/auth/login.");
    }

    /** We know who you are; you still may not do this -> 403. */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN,
                "Access denied",
                "You do not have permission to perform this action.");
    }

    private void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
