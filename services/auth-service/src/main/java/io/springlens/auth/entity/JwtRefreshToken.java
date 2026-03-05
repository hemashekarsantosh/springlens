package io.springlens.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * JWT Refresh Token tracking entity.
 *
 * ✅ HARDENED: Refresh tokens are tracked to implement rotation:
 * - Each refresh token is issued with an expiration (7 days instead of 30)
 * - Used tokens are marked as rotated
 * - Prevents replay attacks by invalidating old tokens
 * - Reduces impact of token compromise (shorter lifetime)
 */
@Entity
@Table(name = "jwt_refresh_tokens", indexes = {
        @Index(name = "idx_refresh_token_user_id", columnList = "user_id"),
        @Index(name = "idx_refresh_token_workspace_id", columnList = "workspace_id")
})
public class JwtRefreshToken {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "token_jti", nullable = false, unique = true)
    private String tokenJti; // JWT ID claim - unique identifier for this token

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt; // When this token was used to request a new one

    @Column(name = "revoked_at")
    private Instant revokedAt; // When this token was explicitly revoked

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected JwtRefreshToken() {
    }

    /**
     * Factory method to create a new refresh token (7-day expiration).
     * ✅ SECURITY: Shorter lifetime (7 days) compared to old 30-day tokens.
     */
    public static JwtRefreshToken create(UUID userId, UUID workspaceId, String tokenJti) {
        var token = new JwtRefreshToken();
        token.id = UUID.randomUUID();
        token.userId = userId;
        token.workspaceId = workspaceId;
        token.tokenJti = tokenJti;
        token.createdAt = Instant.now();
        // ✅ FIXED: 7-day expiration instead of 30 days
        token.expiresAt = Instant.now().plus(7, ChronoUnit.DAYS);
        return token;
    }

    /**
     * Check if this refresh token is still valid (not expired, not rotated, not revoked).
     */
    public boolean isValid() {
        return revokedAt == null && rotatedAt == null && expiresAt.isAfter(Instant.now());
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getTokenJti() { return tokenJti; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRotatedAt() { return rotatedAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }

    // Setters for state transitions
    public void markAsRotated(Instant rotatedAt) {
        this.rotatedAt = rotatedAt;
    }

    public void revoke(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }
}
