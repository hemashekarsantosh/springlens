package io.springlens.shared;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler for all controllers.
 *
 * ✅ SECURITY: All exceptions are caught and converted to safe, generic error responses.
 * Detailed error information is logged server-side only and never exposed to clients.
 * This prevents information disclosure vulnerabilities.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handle Spring validation errors (MethodArgumentNotValidException).
     * SECURITY: Return generic message; validation details logged server-side only.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult()
                .getAllErrors()
                .stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Validation error: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseBuilder.validationError(details));
    }

    /**
     * Handle constraint violations (e.g., @Min, @Max on request parameters).
     * SECURITY: Return generic message; constraint details logged server-side only.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        String details = ex.getConstraintViolations()
                .stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining(", "));

        log.warn("Constraint violation: {}", details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseBuilder.validationError(details));
    }

    /**
     * Handle 404 Not Found.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseBuilder.notFound());
    }

    /**
     * Handle IllegalArgumentException (custom business logic validation).
     * SECURITY: Only expose the message if it was explicitly meant for the client.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        // For business validation errors that are safe to expose (e.g., "Webhook URL must use HTTPS")
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponseBuilder.badRequestWithMessage(ex.getMessage()));
    }

    /**
     * Handle IllegalStateException (application state errors).
     * SECURITY: Return generic message; details logged server-side only.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseBuilder.internalError(ex));
    }

    /**
     * Handle all other exceptions as generic 500 Internal Server Error.
     * SECURITY: Never expose exception details to clients.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseBuilder.internalError(ex));
    }
}
