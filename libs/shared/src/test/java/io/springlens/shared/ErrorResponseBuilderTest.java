package io.springlens.shared;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for ErrorResponseBuilder.
 * Verifies: Error responses are safe and do not disclose sensitive information.
 */
@DisplayName("ErrorResponseBuilder Information Disclosure Prevention Tests")
class ErrorResponseBuilderTest {

    @Test
    @DisplayName("Should return generic bad request error")
    void testBadRequestGeneric() {
        ErrorResponse error = ErrorResponseBuilder.badRequest("Invalid email format");

        // Should have code
        assertThat(error.code).isEqualTo("BAD_REQUEST");

        // Should NOT contain the specific validation message
        assertThat(error.message).isEqualTo("Invalid request");
        assertThat(error.message).doesNotContain("email");
        assertThat(error.message).doesNotContain("Invalid email format");
    }

    @Test
    @DisplayName("Should return generic unauthorized error")
    void testUnauthorizedGeneric() {
        ErrorResponse error = ErrorResponseBuilder.unauthorized();

        assertThat(error.code).isEqualTo("UNAUTHORIZED");
        assertThat(error.message).isEqualTo("Authentication required");
    }

    @Test
    @DisplayName("Should return generic forbidden error")
    void testForbiddenGeneric() {
        ErrorResponse error = ErrorResponseBuilder.forbidden();

        assertThat(error.code).isEqualTo("FORBIDDEN");
        assertThat(error.message).isEqualTo("Access denied");
    }

    @Test
    @DisplayName("Should return generic not found error")
    void testNotFoundGeneric() {
        ErrorResponse error = ErrorResponseBuilder.notFound();

        assertThat(error.code).isEqualTo("NOT_FOUND");
        assertThat(error.message).isEqualTo("Resource not found");
    }

    @Test
    @DisplayName("Security: Should NOT expose database error details")
    void testDatabaseErrorDoesNotExposeDetails() {
        Exception dbException = new RuntimeException(
                "SQL Error: Duplicate key value violates unique constraint \"users_email_key\"");

        ErrorResponse error = ErrorResponseBuilder.databaseError(dbException);

        // Should NOT contain SQL details
        assertThat(error.message).doesNotContain("SQL");
        assertThat(error.message).doesNotContain("constraint");
        assertThat(error.message).doesNotContain("Duplicate key");
        assertThat(error.message).isEqualTo("Request processing failed");
    }

    @Test
    @DisplayName("Security: Should NOT expose exception class names")
    void testInternalErrorDoesNotExposeStackTrace() {
        Exception ex = new NullPointerException("Cannot invoke \"String.length()\" because \"str\" is null");

        ErrorResponse error = ErrorResponseBuilder.internalError(ex);

        // Should NOT contain exception details
        assertThat(error.message).doesNotContain("NullPointerException");
        assertThat(error.message).doesNotContain("Cannot invoke");
        assertThat(error.message).doesNotContain(".length()");
        assertThat(error.message).isEqualTo("Request processing failed");
    }

    @Test
    @DisplayName("Security: Should NOT expose external service failures")
    void testExternalServiceErrorDoesNotExposeDetails() {
        Exception ex = new RuntimeException("Connection refused to auth-service:8084");

        ErrorResponse error = ErrorResponseBuilder.externalServiceError(ex);

        // Should NOT contain service details or hostnames
        assertThat(error.message).doesNotContain("auth-service");
        assertThat(error.message).doesNotContain("8084");
        assertThat(error.message).doesNotContain("Connection refused");
        assertThat(error.message).isEqualTo("Request processing failed");
    }

    @Test
    @DisplayName("Should return rate limit error")
    void testRateLimitError() {
        ErrorResponse error = ErrorResponseBuilder.rateLimited();

        assertThat(error.code).isEqualTo("RATE_LIMITED");
        assertThat(error.message).isEqualTo("Too many requests. Please try again later.");
    }

    @Test
    @DisplayName("Security: Should NOT expose file paths in errors")
    void testErrorDoesNotExposePaths() {
        Exception ex = new RuntimeException(
                "Failed to read file: /app/config/application-prod.yml: permission denied");

        ErrorResponse error = ErrorResponseBuilder.internalError(ex);

        // Should NOT contain file paths
        assertThat(error.message).doesNotContain("/app/config");
        assertThat(error.message).doesNotContain("application-prod.yml");
        assertThat(error.message).doesNotContain("permission denied");
    }

    @Test
    @DisplayName("Should provide custom bad request message (safe messages only)")
    void testCustomBadRequestMessage() {
        // Safe message that is user-facing (e.g., validation error from WebhookUrlValidator)
        ErrorResponse error = ErrorResponseBuilder.badRequestWithMessage(
                "Webhook URL must use HTTPS protocol");

        assertThat(error.code).isEqualTo("BAD_REQUEST");
        assertThat(error.message).isEqualTo("Webhook URL must use HTTPS protocol");
    }

    @Test
    @DisplayName("Should consistently use error codes")
    void testErrorCodesAreConsistent() {
        assertThat(ErrorResponseBuilder.badRequest("test").code).isEqualTo("BAD_REQUEST");
        assertThat(ErrorResponseBuilder.unauthorized().code).isEqualTo("UNAUTHORIZED");
        assertThat(ErrorResponseBuilder.forbidden().code).isEqualTo("FORBIDDEN");
        assertThat(ErrorResponseBuilder.notFound().code).isEqualTo("NOT_FOUND");
        assertThat(ErrorResponseBuilder.rateLimited().code).isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("Should handle null exceptions gracefully")
    void testNullExceptionHandling() {
        // Should not throw NullPointerException
        ErrorResponse error = ErrorResponseBuilder.internalError(null);

        assertThat(error).isNotNull();
        assertThat(error.message).isEqualTo("Request processing failed");
    }
}
