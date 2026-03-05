package io.springlens.auth.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Audit log entity for tracking sensitive operations.
 * Provides immutable forensics trail for compliance and security monitoring.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_workspace_id", columnList = "workspace_id"),
        @Index(name = "idx_audit_user_id", columnList = "user_id"),
        @Index(name = "idx_audit_resource", columnList = "resource_type,resource_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_created_at", columnList = "created_at")
})
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id")
    private UUID userId; // Null for system operations

    @Column(name = "action", nullable = false, length = 50)
    private String action; // CREATE, UPDATE, DELETE, ENABLE, DISABLE, REVOKE, VERIFY

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType; // API_KEY, JWT_TOKEN, WEBHOOK, BILLING, USER_PROFILE

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "changes", columnDefinition = "TEXT")
    private String changes; // JSON diff or description

    @Column(name = "ip_address", length = 45)
    private String ipAddress; // IPv4 or IPv6

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "result", length = 20)
    private String result; // SUCCESS, FAILURE

    @Column(name = "error_message", length = 500)
    private String errorMessage; // Only for failures

    public AuditLog() {
    }

    public AuditLog(UUID workspaceId, UUID userId, String action, String resourceType, UUID resourceId,
                    String changes, String ipAddress, String userAgent) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.changes = changes;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.createdAt = Instant.now();
        this.result = "SUCCESS";
    }

    public static AuditLog success(UUID workspaceId, UUID userId, String action, String resourceType,
                                   UUID resourceId, String changes, String ipAddress, String userAgent) {
        return new AuditLog(workspaceId, userId, action, resourceType, resourceId, changes, ipAddress, userAgent);
    }

    public static AuditLog failure(UUID workspaceId, UUID userId, String action, String resourceType,
                                   UUID resourceId, String errorMessage, String ipAddress, String userAgent) {
        AuditLog log = new AuditLog(workspaceId, userId, action, resourceType, resourceId, null, ipAddress, userAgent);
        log.result = "FAILURE";
        log.errorMessage = errorMessage;
        return log;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getAction() {
        return action;
    }

    public String getResourceType() {
        return resourceType;
    }

    public UUID getResourceId() {
        return resourceId;
    }

    public String getChanges() {
        return changes;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
