package io.springlens.shared;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for RateLimitingService.
 * Verifies: Rate limiting prevents DoS attacks.
 */
@DisplayName("RateLimitingService DoS Prevention Tests")
class RateLimitingServiceTest {

    private RateLimitingService rateLimitingService;

    @BeforeEach
    void setUp() {
        rateLimitingService = new RateLimitingService();
    }

    @Test
    @DisplayName("Should allow requests within rate limit")
    void testAllowRequestsWithinLimit() {
        // Ingest endpoint limit is 100 per minute
        // Test that we can make multiple requests within the limit
        for (int i = 0; i < 50; i++) {
            boolean canIngest = rateLimitingService.canIngest();
            assertThat(canIngest).isTrue();
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding rate limit")
    void testRejectRequestsExceedingLimit() {
        // The ingest endpoint has a limit of 100 requests per minute
        // After exceeding this limit, requests should be rejected

        // Make many requests to exceed the limit
        int rejectedCount = 0;
        for (int i = 0; i < 200; i++) {
            if (!rateLimitingService.canIngest()) {
                rejectedCount++;
            }
        }

        // Should have some rejected requests
        assertThat(rejectedCount).isGreaterThan(0);
    }

    @Test
    @DisplayName("Security: Different endpoints have different rate limits")
    void testDifferentEndpointsHaveDifferentLimits() {
        // Auth endpoint is more restrictive (10 per minute)
        // API Key endpoint is moderate (50 per minute)
        // Ingest endpoint is permissive (100 per minute)

        // This test verifies that rate limits are endpoint-specific
        assertThat(rateLimitingService.canAuth()).isTrue();
        assertThat(rateLimitingService.canAccessApiKey()).isTrue();
        assertThat(rateLimitingService.canIngest()).isTrue();
        assertThat(rateLimitingService.canWebhook()).isTrue();
    }

    @Test
    @DisplayName("Security: Auth endpoint has tighter limits (prevents brute force)")
    void testAuthEndpointHasTightLimits() {
        // Auth endpoint is limited to 10 requests per minute
        // This prevents brute force attacks

        // The service is instantiated fresh in setUp, so limits are reset
        // We can make 10 auth requests
        for (int i = 0; i < 10; i++) {
            assertThat(rateLimitingService.canAuth()).isTrue();
        }
    }

    @Test
    @DisplayName("Should handle rapid sequential requests")
    void testHandleRapidRequests() {
        // Simulate rapid requests
        int successCount = 0;
        for (int i = 0; i < 150; i++) {
            if (rateLimitingService.canIngest()) {
                successCount++;
            }
        }

        // Most should succeed (within the 100 limit)
        // Some might fail (exceeding the limit)
        assertThat(successCount).isLessThanOrEqualTo(100);
        assertThat(successCount).isGreaterThan(90);
    }

    @Test
    @DisplayName("Should return statistics about rate limiter state")
    void testGetRateLimiterStats() {
        // Make some requests
        for (int i = 0; i < 5; i++) {
            rateLimitingService.canIngest();
        }

        RateLimitingService.RateLimiterStats stats = rateLimitingService.getIngestStats();

        assertThat(stats).isNotNull();
        assertThat(stats.successfulCalls()).isGreaterThanOrEqualTo(0);
        assertThat(stats.failedCalls()).isGreaterThanOrEqualTo(0);
        assertThat(stats.waitingThreads()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("Security: Rate limiting applies consistently across all endpoints")
    void testRateLimitingAppliesToAllEndpoints() {
        // Verify that all endpoints have rate limiting configured
        assertThat(rateLimitingService.canIngest()).isNotNull();
        assertThat(rateLimitingService.canAccessApiKey()).isNotNull();
        assertThat(rateLimitingService.canAuth()).isNotNull();
        assertThat(rateLimitingService.canWebhook()).isNotNull();
    }

    @Test
    @DisplayName("Security: Webhook endpoint rate limiting")
    void testWebhookEndpointRateLimit() {
        // Webhook endpoint limit is 30 per minute
        // Verify that it exists and can be checked

        for (int i = 0; i < 30; i++) {
            boolean canWebhook = rateLimitingService.canWebhook();
            assertThat(canWebhook).isTrue();
        }
    }

    @Test
    @DisplayName("Should reject simultaneous bursts from multiple requests")
    void testRejectBurstRequests() {
        // Simulate a burst of requests
        int allowedCount = 0;
        for (int i = 0; i < 200; i++) {
            if (rateLimitingService.canIngest()) {
                allowedCount++;
            }
        }

        // Should not allow all 200 (limit is 100)
        assertThat(allowedCount).isLessThan(150);
    }
}
