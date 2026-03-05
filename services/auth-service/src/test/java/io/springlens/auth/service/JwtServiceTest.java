package io.springlens.auth.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for JwtService.
 * Verifies: JWT tokens are issued and validated correctly with Secrets Manager.
 */
@DisplayName("JwtService Tests")
class JwtServiceTest {

    private JwtService jwtService;
    private SecretsService mockSecretsService;

    @BeforeEach
    void setUp() {
        // Create mock SecretsService
        mockSecretsService = mock(SecretsService.class);

        // Mock the JWT secret (must be at least 256 bits for HS256)
        String testSecret = "this_is_a_test_secret_key_at_least_256_bits_long_for_hs256_signing_key";
        when(mockSecretsService.getSecret("springlens/jwt-secret"))
                .thenReturn(testSecret);

        // Initialize JwtService with mock
        jwtService = new JwtService(mockSecretsService, "https://api.springlens.io");
    }

    @Test
    @DisplayName("Should load JWT secret from Secrets Manager on initialization")
    void testJwtSecretLoadedFromSecretsManager() {
        // VERIFY: JwtService constructor called SecretsService.getSecret()
        verify(mockSecretsService, times(1)).getSecret("springlens/jwt-secret");
    }

    @Test
    @DisplayName("Should issue valid access token")
    void testIssueAccessToken() {
        // GIVEN: Valid user credentials
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String email = "user@example.com";
        String role = "admin";

        // WHEN: Access token is issued
        String token = jwtService.issueAccessToken(userId, workspaceId, email, role);

        // THEN: Should produce a non-null, non-empty token
        assertThat(token).isNotNull().isNotEmpty();

        // Verify token is a valid JWT
        assertThat(token.split("\\.")).hasSize(3); // JWT format: header.payload.signature
    }

    @Test
    @DisplayName("Should issue valid refresh token")
    void testIssueRefreshToken() {
        // GIVEN: Valid user credentials
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();

        // WHEN: Refresh token is issued
        String token = jwtService.issueRefreshToken(userId, workspaceId);

        // THEN: Should produce a non-null, non-empty token
        assertThat(token).isNotNull().isNotEmpty();

        // Verify token is a valid JWT
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("Should validate token and extract claims")
    void testValidateTokenExtractsClaims() {
        // GIVEN: An issued access token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String email = "user@example.com";
        String role = "admin";

        String token = jwtService.issueAccessToken(userId, workspaceId, email, role);

        // WHEN: Token is validated
        Claims claims = jwtService.validateToken(token);

        // THEN: Claims should be extracted correctly
        assertThat(claims).isNotNull();
        assertThat(claims.getSubject()).isEqualTo(userId.toString());
        assertThat(claims.get("workspace_id")).isEqualTo(workspaceId.toString());
        assertThat(claims.get("email")).isEqualTo(email);
        assertThat(claims.get("workspace_role")).isEqualTo(role);
        assertThat(claims.get("token_type")).isEqualTo("access");
    }

    @Test
    @DisplayName("Should identify access token correctly")
    void testIdentifyAccessToken() {
        // GIVEN: An access token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, workspaceId, "user@example.com", "admin");

        // WHEN: Token is validated and checked
        Claims claims = jwtService.validateToken(token);

        // THEN: Should identify as access token
        assertThat(jwtService.isAccessToken(claims)).isTrue();
    }

    @Test
    @DisplayName("Should identify refresh token correctly")
    void testIdentifyRefreshToken() {
        // GIVEN: A refresh token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueRefreshToken(userId, workspaceId);

        // WHEN: Token is validated and checked
        Claims claims = jwtService.validateToken(token);

        // THEN: Should not identify as access token (it's a refresh token)
        assertThat(jwtService.isAccessToken(claims)).isFalse();
    }

    @Test
    @DisplayName("Should extract user ID from claims")
    void testExtractUserId() {
        // GIVEN: An issued token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, workspaceId, "user@example.com", "admin");

        // WHEN: Token is validated and user ID extracted
        Claims claims = jwtService.validateToken(token);
        UUID extractedUserId = jwtService.extractUserId(claims);

        // THEN: Should extract correct user ID
        assertThat(extractedUserId).isEqualTo(userId);
    }

    @Test
    @DisplayName("Should extract workspace ID from claims")
    void testExtractWorkspaceId() {
        // GIVEN: An issued token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, workspaceId, "user@example.com", "admin");

        // WHEN: Token is validated and workspace ID extracted
        Claims claims = jwtService.validateToken(token);
        UUID extractedWorkspaceId = jwtService.extractWorkspaceId(claims);

        // THEN: Should extract correct workspace ID
        assertThat(extractedWorkspaceId).isEqualTo(workspaceId);
    }

    @Test
    @DisplayName("Should reject invalid/tampered tokens")
    void testRejectInvalidToken() {
        // GIVEN: An invalid token (tampered)
        String invalidToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.invalid_signature";

        // WHEN/THEN: Validation should fail
        assertThatThrownBy(() -> jwtService.validateToken(invalidToken))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should include issuer in token claims")
    void testIssuerIncludedInToken() {
        // GIVEN: An issued token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, workspaceId, "user@example.com", "admin");

        // WHEN: Token is validated
        Claims claims = jwtService.validateToken(token);

        // THEN: Should include correct issuer
        assertThat(claims.getIssuer()).isEqualTo("https://api.springlens.io");
    }

    @Test
    @DisplayName("Security: Access token should expire in 15 minutes")
    void testAccessTokenExpiration() {
        // GIVEN: An access token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId, workspaceId, "user@example.com", "admin");

        // WHEN: Token is validated
        Claims claims = jwtService.validateToken(token);

        // THEN: Expiration should be approximately 15 minutes from now
        long expirationTime = claims.getExpiration().getTime();
        long currentTime = System.currentTimeMillis();
        long expirationMinutes = (expirationTime - currentTime) / (1000 * 60);

        assertThat(expirationMinutes).isGreaterThanOrEqualTo(14).isLessThanOrEqualTo(15);
    }

    @Test
    @DisplayName("Security: Refresh token should expire in 30 days")
    void testRefreshTokenExpiration() {
        // GIVEN: A refresh token
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        String token = jwtService.issueRefreshToken(userId, workspaceId);

        // WHEN: Token is validated
        Claims claims = jwtService.validateToken(token);

        // THEN: Expiration should be approximately 30 days from now
        long expirationTime = claims.getExpiration().getTime();
        long currentTime = System.currentTimeMillis();
        long expirationDays = (expirationTime - currentTime) / (1000 * 60 * 60 * 24);

        assertThat(expirationDays).isGreaterThanOrEqualTo(29).isLessThanOrEqualTo(30);
    }
}
