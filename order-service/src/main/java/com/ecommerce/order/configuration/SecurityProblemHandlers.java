package com.ecommerce.order.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** Same problem-JSON shape as every other error in the platform. */
@Component
@RequiredArgsConstructor
public class SecurityProblemHandlers {

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, ex) -> write(response, HttpStatus.UNAUTHORIZED,
                "Authentication required",
                "A valid bearer token is required. Obtain one from POST /api/v1/auth/login on user-service.");
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, ex) -> write(response, HttpStatus.FORBIDDEN,
                "Access denied", "You do not have permission to perform this action.");
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
