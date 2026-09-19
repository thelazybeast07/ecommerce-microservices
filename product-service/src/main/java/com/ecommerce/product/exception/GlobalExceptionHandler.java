package com.ecommerce.product.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

/**
 * Same contract as user-service's handler: RFC 9457 Problem Details for every error, so all
 * services in the platform speak one error format.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        return problem(HttpStatus.CONFLICT, "Duplicate resource", ex.getMessage());
    }

    @ExceptionHandler(InvalidResourceStateException.class)
    public ProblemDetail handleInvalidState(InvalidResourceStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid resource state", ex.getMessage());
    }

    @ExceptionHandler(UnprocessableRequestException.class)
    public ProblemDetail handleUnprocessable(UnprocessableRequestException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Unprocessable request", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified by another request. Reload it and try again.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing data.");
    }

    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                "Unknown property '%s'".formatted(ex.getPropertyName()));
    }

    /**
     * Raised by @PreAuthorize. NOT optional: without it the catch-all below would catch
     * this and report a permission failure as a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied",
                "You do not have permission to perform this action.");
    }

    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid bearer token is required.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        List<FieldValidationError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldValidationError(error.getField(), error.getDefaultMessage()))
                .toList();

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }    
}
