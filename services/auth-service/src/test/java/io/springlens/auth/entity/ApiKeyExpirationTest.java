package io.springlens.auth.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for API Key expiration security.
 * Verifies: API keys expire after 90 days to reduce impact of compromise.
 */
@DisplayName("ApiKey Expiration Security Tests")
class ApiKeyExpirationTest {

    @Test
    @DisplayName("Should create API key with 90-day default expiration")
    void testDefaultExpirationIs90Days() {
        // GIVEN: Creating a new API key
        UUID workspaceId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // WHEN: API key is created
        ApiKey key = ApiKey.create(
                workspaceId, projectId, "test-key",
                "hash123", "prefix_", userId);

        // THEN: Should have expiration set to ~90 days from now
        Instant expectedExpiration = Instant.now().plus(90, ChronoUnit.DAYS);
        Instant actualExpiration = key.getExpiresAt();

        assertThat(actualExpiration).isNotNull();
        // Allow 1 minute variance for test execution time
        assertThat(actualExpiration).isBetween(
                expectedExpiration.minus(1, ChronoUnit.MINUTES),
                expectedExpiration.plus(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Should report new API key as active")
    void testNewApiKeyIsActive() {
        // GIVEN: A newly created API key
        ApiKey key = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "test-key",
                "hash123", "prefix_", UUID.randomUUID());

        // WHEN: Checking if key is active
        boolean isActive = key.isActive();

        // THEN: Should report as active (not expired, not revoked)
        assertThat(isActive).isTrue();
    }

    @Test
    @DisplayName("Security: Should report expired API key as inactive")
    void testExpiredApiKeyIsInactive() {
        // GIVEN: An API key that has been manually set to expire in the past
        ApiKey key = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "test-key",
                "hash123", "prefix_", UUID.randomUUID());

        // Manually set expiration to yesterday
        key.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        // WHEN: Checking if key is active
        boolean isActive = key.isActive();

        // THEN: Should report as inactive (expired)
        assertThat(isActive).isFalse();
    }

    @Test
    @DisplayName("Security: Should report revoked API key as inactive")
    void testRevokedApiKeyIsInactive() {
        // GIVEN: An API key that has been revoked
        ApiKey key = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "test-key",
                "hash123", "prefix_", UUID.randomUUID());

        // Manually revoke the key
        key.setRevokedAt(Instant.now());

        // WHEN: Checking if key is active
        boolean isActive = key.isActive();

        // THEN: Should report as inactive (revoked)
        assertThat(isActive).isFalse();
    }

    @Test
    @DisplayName("Should allow renewal of expiration date")
    void testCanRenewExpirationDate() {
        // GIVEN: An API key nearing expiration
        ApiKey key = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "test-key",
                "hash123", "prefix_", UUID.randomUUID());

        // Current expiration is ~90 days away
        Instant originalExpiration = key.getExpiresAt();

        // WHEN: Expiration is renewed (extended by another 90 days)
        Instant newExpiration = Instant.now().plus(90, ChronoUnit.DAYS);
        key.setExpiresAt(newExpiration);

        // THEN: Expiration should be updated
        assertThat(key.getExpiresAt()).isNotEqualTo(originalExpiration);
        assertThat(key.getExpiresAt()).isEqualTo(newExpiration);
    }

    @Test
    @DisplayName("Should track last used time to detect suspicious activity")
    void testLastUsedTimestampTracking() {
        // GIVEN: A new API key
        ApiKey key = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "test-key",
                "hash123", "prefix_", UUID.randomUUID());

        assertThat(key.getLastUsedAt()).isNull();

        // WHEN: Key is used (simulate update)
        Instant now = Instant.now();
        key.setLastUsedAt(now);

        // THEN: Last used time should be recorded
        assertThat(key.getLastUsedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("Security: Expired key should not be usable even if not explicitly revoked")
    void testExpiredKeyNotUsable() {
        // GIVEN: An API key with expiration in the past
        ApiKey key = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "test-key",
                "hash123", "prefix_", UUID.randomUUID());

        // Set expiration to 1 day ago
        key.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));

        // WHEN: Checking if key is active
        boolean isActive = key.isActive();

        // THEN: Key should be considered inactive (expired)
        assertThat(isActive).isFalse();

        // Verify that it's not active even though revokedAt is null
        assertThat(key.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("Should calculate expiration window correctly")
    void testExpirationWindowCalculation() {
        // GIVEN: Multiple API keys created at different times
        ApiKey key1 = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "key1",
                "hash1", "prefix1", UUID.randomUUID());

        // Simulate a delay
        try {
            Thread.sleep(100); // Small delay
        } catch (InterruptedException e) {
            // Ignore
        }

        ApiKey key2 = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "key2",
                "hash2", "prefix2", UUID.randomUUID());

        // WHEN: Both keys are created
        // THEN: key1 should expire slightly before key2
        assertThat(key1.getExpiresAt()).isBefore(key2.getExpiresAt());
    }

    @Test
    @DisplayName("Compliance: API key expiration reduces blast radius of compromise")
    void testExpirationReducesCompromiseRisk() {
        // This test documents the security property:
        // If an API key is compromised, it will only be usable for up to 90 days
        // After that, the attacker must compromise a new key

        // GIVEN: A compromised API key created today
        ApiKey compromisedKey = ApiKey.create(
                UUID.randomUUID(), UUID.randomUUID(), "compromised-key",
                "hash", "prefix", UUID.randomUUID());

        Instant createdAt = compromisedKey.getCreatedAt();
        Instant expiresAt = compromisedKey.getExpiresAt();

        // WHEN: Calculating the window of compromise
        long windowDays = createdAt.until(expiresAt, ChronoUnit.DAYS);

        // THEN: The window should be ~90 days
        assertThat(windowDays).isGreaterThanOrEqualTo(89).isLessThanOrEqualTo(91);
    }
}
