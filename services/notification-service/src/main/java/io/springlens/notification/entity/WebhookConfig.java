package io.springlens.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Webhook configuration entity.
 * URL is stored AES-256 encrypted using a service key from the environment.
 */
@Entity
@Table(name = "webhook_configs")
public class WebhookConfig {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "type", nullable = false)
    private String type;

    @Column(name = "url_encrypted", nullable = false)
    private String urlEncrypted;

    @Column(name = "filter_config", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private java.util.Map<String, Object> filterConfig;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WebhookConfig() {
    }

    public static WebhookConfig create(UUID workspaceId, UUID projectId, String type,
                                        String urlEncrypted) {
        var wc = new WebhookConfig();
        wc.id = UUID.randomUUID();
        wc.workspaceId = workspaceId;
        wc.projectId = projectId;
        wc.type = type;
        wc.urlEncrypted = urlEncrypted;
        wc.filterConfig = java.util.Map.of();
        wc.enabled = true;
        wc.createdAt = Instant.now();
        wc.updatedAt = Instant.now();
        return wc;
    }

    public UUID getId() { return id; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public String getType() { return type; }
    public String getUrlEncrypted() { return urlEncrypted; }
    public java.util.Map<String, Object> getFilterConfig() { return filterConfig; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUrlEncrypted(String urlEncrypted) { this.urlEncrypted = urlEncrypted; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setFilterConfig(java.util.Map<String, Object> filterConfig) { this.filterConfig = filterConfig; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
