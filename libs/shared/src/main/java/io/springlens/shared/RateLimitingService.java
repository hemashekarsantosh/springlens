package io.springlens.shared;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Rate limiting service for critical API endpoints.
 *
 * ✅ SECURITY: Implements per-endpoint rate limiting to prevent:
 * - DoS attacks (high volume requests)
 * - Brute force attacks (credential stuffing)
 * - Resource exhaustion
 *
 * Rate limits are applied per endpoint with different thresholds based on sensitivity.
 */
@Service
public class RateLimitingService {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingService.class);

    private final RateLimiter ingestLimiter;
    private final RateLimiter apiKeyLimiter;
    private final RateLimiter authLimiter;
    private final RateLimiter webhookLimiter;

    public RateLimitingService() {
        RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();

        // Ingest endpoint: 100 requests per minute (high volume is expected)
        this.ingestLimiter = registry.rateLimiter("ingest",
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .limitForPeriod(100)
                        .timeoutDuration(Duration.ofMillis(100))
                        .build());

        // API Key endpoints: 50 requests per minute
        this.apiKeyLimiter = registry.rateLimiter("api-key",
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .limitForPeriod(50)
                        .timeoutDuration(Duration.ofMillis(100))
                        .build());

        // Auth endpoints: 10 requests per minute (prevent brute force)
        this.authLimiter = registry.rateLimiter("auth",
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .limitForPeriod(10)
                        .timeoutDuration(Duration.ofMillis(100))
                        .build());

        // Webhook endpoints: 30 requests per minute
        this.webhookLimiter = registry.rateLimiter("webhook",
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofMinutes(1))
                        .limitForPeriod(30)
                        .timeoutDuration(Duration.ofMillis(100))
                        .build());
    }

    /**
     * Check if ingest endpoint should process request.
     */
    public boolean canIngest() {
        try {
            return ingestLimiter.executeSupplier(() -> true);
        } catch (Exception ex) {
            log.warn("Rate limit exceeded for ingest endpoint");
            return false;
        }
    }

    /**
     * Check if API key operation should proceed.
     */
    public boolean canAccessApiKey() {
        try {
            return apiKeyLimiter.executeSupplier(() -> true);
        } catch (Exception ex) {
            log.warn("Rate limit exceeded for API key endpoint");
            return false;
        }
    }

    /**
     * Check if auth operation should proceed.
     */
    public boolean canAuth() {
        try {
            return authLimiter.executeSupplier(() -> true);
        } catch (Exception ex) {
            log.warn("Rate limit exceeded for auth endpoint");
            return false;
        }
    }

    /**
     * Check if webhook operation should proceed.
     */
    public boolean canWebhook() {
        try {
            return webhookLimiter.executeSupplier(() -> true);
        } catch (Exception ex) {
            log.warn("Rate limit exceeded for webhook endpoint");
            return false;
        }
    }

    /**
     * Get current statistics for rate limiter.
     */
    public RateLimiterStats getIngestStats() {
        RateLimiter.Metrics metrics = ingestLimiter.getMetrics();
        return new RateLimiterStats(
                metrics.getNumberOfSuccessfulCalls(),
                metrics.getNumberOfFailedCalls(),
                metrics.getNumberOfWaitingThreads());
    }

    /**
     * Rate limiter statistics.
     */
    public record RateLimiterStats(
            long successfulCalls,
            long failedCalls,
            int waitingThreads) {
    }
}
