package com.ecommerce.user.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Instant;
import java.util.List;

/**
 * Translates exceptions into RFC 9457 "Problem Details" JSON (application/problem+json).
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} gives consistent handling of Spring MVC's
 * own errors for free: malformed JSON, a non-UUID path variable, an unsupported media type and
 * so on all come back as 400/415 problem details instead of HTML error pages.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    /**
     * 401, not 403. The distinction matters: 401 means "I do not know who you are",
     * 403 means "I know who you are and you still may not do this".
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication failed", ex.getMessage());
    }

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

    /** Two requests updated the same row concurrently; the later one lost (see @Version). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification",
                "The resource was modified by another request. Reload it and try again.");
    }

    /**
     * Safety net for database constraints, e.g. two simultaneous registrations with the same
     * email that both pass the existsByEmail() check. The raw database message is logged, never
     * returned, because it can reveal table and constraint names.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "Data conflict", "The request conflicts with existing data.");
    }

    /** e.g. GET /api/v1/customers?sort=doesNotExist */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleUnknownProperty(PropertyReferenceException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                "Unknown property '%s'".formatted(ex.getPropertyName()));
    }

    /**
     * Raised by {@code @PreAuthorize} when a rule fails - for example a customer asking for
     * someone else's record.
     *
     * <p>This handler is NOT optional. {@code @PreAuthorize} throws inside the controller call,
     * so without an explicit handler the catch-all {@code Exception} handler below would catch
     * it and return 500. A permission failure reported as "internal server error" is both
     * misleading to the caller and hides a real signal from your logs.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "Access denied",
                "You do not have permission to perform this action.");
    }

    /** No usable credentials at all -> 401, as distinct from the 403 above. */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Authentication required",
                "A valid bearer token is required.");
    }

    /** Last resort. Log everything, reveal nothing. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "An unexpected error occurred.");
    }

    /** Bean Validation failures on @Valid @RequestBody: return every field error, not just the first. */
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
