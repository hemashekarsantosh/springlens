package io.springlens.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Safe error response builder to prevent information disclosure.
 *
 * ✅ SECURITY: All error responses are generic and do NOT contain:
 * - Exception class names or stack traces
 * - SQL error messages
 * - Database details
 * - File paths
 * - Internal system information
 *
 * Detailed error information is logged server-side only (never exposed to clients).
 */
public class ErrorResponseBuilder {

    private static final Logger log = LoggerFactory.getLogger(ErrorResponseBuilder.class);

    /**
     * Generic 400 Bad Request error (user input validation failure).
     */
    public static ErrorResponse badRequest(String message) {
        logIfVerbose("Bad Request: {}", message);
        return ErrorResponse.of("BAD_REQUEST", "Invalid request", null);
    }

    /**
     * Specific bad request with custom message (e.g., validation details).
     * SECURITY: Only use if message is user-facing and contains no sensitive data.
     */
    public static ErrorResponse badRequestWithMessage(String message) {
        logIfVerbose("Bad Request: {}", message);
        return ErrorResponse.of("BAD_REQUEST", message, null);
    }

    /**
     * 401 Unauthorized (authentication failure).
     */
    public static ErrorResponse unauthorized() {
        return ErrorResponse.of("UNAUTHORIZED", "Authentication required", null);
    }

    /**
     * 403 Forbidden (authorization failure).
     */
    public static ErrorResponse forbidden() {
        return ErrorResponse.of("FORBIDDEN", "Access denied", null);
    }

    /**
     * 404 Not Found.
     */
    public static ErrorResponse notFound() {
        return ErrorResponse.of("NOT_FOUND", "Resource not found", null);
    }

    /**
     * 409 Conflict (duplicate resource, etc.).
     */
    public static ErrorResponse conflict(String message) {
        logIfVerbose("Conflict: {}", message);
        return ErrorResponse.of("CONFLICT", "Request conflicts with current state", null);
    }

    /**
     * 429 Rate Limited.
     */
    public static ErrorResponse rateLimited() {
        return ErrorResponse.of("RATE_LIMITED", "Too many requests. Please try again later.", null);
    }

    /**
     * 500 Internal Server Error (generic).
     * SECURITY: Never expose details of internal errors to clients.
     */
    public static ErrorResponse internalError(Throwable ex) {
        log.error("Internal server error", ex);
        return ErrorResponse.of("INTERNAL_ERROR", "Request processing failed", null);
    }

    /**
     * Handle validation errors from Spring's MethodArgumentNotValidException.
     * SECURITY: Generic message only; detailed validation errors logged server-side.
     */
    public static ErrorResponse validationError(String details) {
        logIfVerbose("Validation error: {}", details);
        return ErrorResponse.of("VALIDATION_ERROR", "Request validation failed", null);
    }

    /**
     * Handle database/repository errors.
     * SECURITY: Never expose database error messages to clients.
     */
    public static ErrorResponse databaseError(Throwable ex) {
        log.error("Database error", ex);
        return ErrorResponse.of("DATA_ERROR", "Request processing failed", null);
    }

    /**
     * Handle external service call failures.
     * SECURITY: Never expose details of external service errors.
     */
    public static ErrorResponse externalServiceError(Throwable ex) {
        log.error("External service error", ex);
        return ErrorResponse.of("SERVICE_ERROR", "Request processing failed", null);
    }

    private static void logIfVerbose(String message, Object... args) {
        // Log detailed information server-side only
        // This is never sent to the client
        if (log.isDebugEnabled()) {
            log.debug(message, args);
        }
    }
}
