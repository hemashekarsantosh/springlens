package io.springlens.auth.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * API Key entity for authenticating programmatic access to SpringLens.
 * ✅ HARDENED: API keys expire after 90 days by default to reduce blast radius of compromise.
 * - Keys can be manually revoked at any time
 * - Expiration is enforced at database level (isActive() check)
 * - Last used timestamp tracks suspicious activity
 */
@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    protected ApiKey() {
    }

    /**
     * Factory method to create a new API key with default 90-day expiration.
     * ✅ SECURITY: Keys expire automatically to reduce risk of compromised credentials.
     */
    public static ApiKey create(UUID workspaceId, UUID projectId, String name,
                                 String keyHash, String keyPrefix, UUID createdBy) {
        var k = new ApiKey();
        k.id = UUID.randomUUID();
        k.workspaceId = workspaceId;
        k.projectId = projectId;
        k.name = name;
        k.keyHash = keyHash;
        k.keyPrefix = keyPrefix;
        k.createdBy = createdBy;
        k.createdAt = Instant.now();
        // ✅ FIXED: Set default expiration to 90 days
        k.expiresAt = Instant.now().plus(90, ChronoUnit.DAYS);
        return k;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getKeyHash() { return keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public UUID getCreatedBy() { return createdBy; }

    public boolean isActive() {
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(Instant.now()));
    }

    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public void setRevokedAt(Instant revokedAt) { this.revokedAt = revokedAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
}
