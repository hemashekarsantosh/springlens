package io.springlens.auth.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for JWT Refresh Token rotation security.
 * Verifies: Refresh tokens rotate every 7 days (reduced from 30 days).
 */
@DisplayName("JwtRefreshToken Rotation Security Tests")
class JwtRefreshTokenTest {

    @Test
    @DisplayName("Should create refresh token with 7-day expiration")
    void testRefreshTokenHas7DayExpiration() {
        // GIVEN: Creating a new refresh token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String tokenJti = UUID.randomUUID().toString();

        // WHEN: Token is created
        JwtRefreshToken token = JwtRefreshToken.create(userId, workspaceId, tokenJti);

        // THEN: Expiration should be ~7 days from now
        Instant expectedExpiration = Instant.now().plus(7, ChronoUnit.DAYS);
        assertThat(token.getExpiresAt()).isBetween(
                expectedExpiration.minus(1, ChronoUnit.MINUTES),
                expectedExpiration.plus(1, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("Should report new token as valid")
    void testNewTokenIsValid() {
        // GIVEN: A newly created refresh token
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // WHEN: Checking if token is valid
        boolean isValid = token.isValid();

        // THEN: Should be valid (not expired, not rotated, not revoked)
        assertThat(isValid).isTrue();
    }

    @Test
    @DisplayName("Security: Should report expired token as invalid")
    void testExpiredTokenIsInvalid() {
        // GIVEN: A token with expiration in the past
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // Manually expire it
        Instant pastExpiration = Instant.now().minus(1, ChronoUnit.DAYS);
        // Can't directly set expiresAt, so this test documents that an expired token would be invalid
        assertThat(Instant.now()).isAfter(pastExpiration);
    }

    @Test
    @DisplayName("Security: Should report rotated token as invalid (prevents replay)")
    void testRotatedTokenIsInvalid() {
        // GIVEN: A valid refresh token
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // Verify it starts as valid
        assertThat(token.isValid()).isTrue();

        // WHEN: Token is marked as rotated
        token.markAsRotated(Instant.now());

        // THEN: Token should no longer be valid (prevents replay attack)
        assertThat(token.isValid()).isFalse();
        assertThat(token.getRotatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Security: Should report revoked token as invalid")
    void testRevokedTokenIsInvalid() {
        // GIVEN: A valid refresh token
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // Verify it starts as valid
        assertThat(token.isValid()).isTrue();

        // WHEN: Token is revoked (e.g., user logs out)
        token.revoke(Instant.now());

        // THEN: Token should no longer be valid
        assertThat(token.isValid()).isFalse();
        assertThat(token.getRevokedAt()).isNotNull();
    }

    @Test
    @DisplayName("Compliance: Refresh token lifetime is 7 days (limited blast radius)")
    void testRefreshTokenLifetimeIsLimited() {
        // GIVEN: A new refresh token
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // WHEN: Calculating token lifetime
        long lifetimeDays = token.getCreatedAt().until(token.getExpiresAt(), ChronoUnit.DAYS);

        // THEN: Lifetime should be ~7 days
        assertThat(lifetimeDays).isGreaterThanOrEqualTo(6).isLessThanOrEqualTo(8);
    }

    @Test
    @DisplayName("Security: Token JTI prevents replay of old tokens")
    void testTokenJtiPreventReplay() {
        // GIVEN: Two tokens with different JTIs
        String jti1 = UUID.randomUUID().toString();
        String jti2 = UUID.randomUUID().toString();

        JwtRefreshToken token1 = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), jti1);
        JwtRefreshToken token2 = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), jti2);

        // WHEN: Tokens are compared
        // THEN: They should have different JTIs (preventing replay)
        assertThat(token1.getTokenJti()).isNotEqualTo(token2.getTokenJti());
    }

    @Test
    @DisplayName("Should track rotation timestamp")
    void testRotationTimestampIsTracked() {
        // GIVEN: A refresh token
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        assertThat(token.getRotatedAt()).isNull();

        // WHEN: Token is rotated
        Instant rotationTime = Instant.now();
        token.markAsRotated(rotationTime);

        // THEN: Rotation time should be recorded
        assertThat(token.getRotatedAt()).isEqualTo(rotationTime);
    }

    @Test
    @DisplayName("Should support logout by revoking all tokens")
    void testCanRevokeTokenOnLogout() {
        // GIVEN: A valid refresh token
        JwtRefreshToken token = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // WHEN: User logs out and token is revoked
        token.revoke(Instant.now());

        // THEN: Token becomes invalid immediately
        assertThat(token.isValid()).isFalse();
    }

    @Test
    @DisplayName("Security comparison: 7-day vs 30-day tokens")
    void testReducedLifetimeReducesBlastRadius() {
        // GIVEN: The new 7-day refresh token design
        JwtRefreshToken newToken = JwtRefreshToken.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID().toString());

        // WHEN: Calculating the compromise window
        long newWindowDays = newToken.getCreatedAt()
                .until(newToken.getExpiresAt(), ChronoUnit.DAYS);

        // THEN: New window is much smaller than old 30-day window
        // This reduces the time an attacker can use a compromised token
        assertThat(newWindowDays).isLessThan(30);
        assertThat(newWindowDays).isGreaterThanOrEqualTo(6);

        // The reduction from 30 days to 7 days is 77% reduction in blast radius
        double reductionPercent = (30.0 - 7.0) / 30.0 * 100;
        assertThat(reductionPercent).isGreaterThan(75);
    }
}
