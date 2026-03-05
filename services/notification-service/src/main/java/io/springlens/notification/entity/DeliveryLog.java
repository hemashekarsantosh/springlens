package io.springlens.notification.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_log")
public class DeliveryLog {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "webhook_config_id", nullable = false)
    private UUID webhookConfigId;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 1;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DeliveryLog() {
    }

    public static DeliveryLog create(UUID webhookConfigId, UUID snapshotId, UUID workspaceId) {
        var dl = new DeliveryLog();
        dl.id = UUID.randomUUID();
        dl.webhookConfigId = webhookConfigId;
        dl.snapshotId = snapshotId;
        dl.workspaceId = workspaceId;
        dl.attemptCount = 1;
        dl.createdAt = Instant.now();
        return dl;
    }

    public UUID getId() { return id; }
    public UUID getWebhookConfigId() { return webhookConfigId; }
    public UUID getSnapshotId() { return snapshotId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorMessage() { return errorMessage; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getDeliveredAt() { return deliveredAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setHttpStatus(Integer httpStatus) { this.httpStatus = httpStatus; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setAttemptCount(int attemptCount) { this.attemptCount = attemptCount; }
    public void setNextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; }
    public void setDeliveredAt(Instant deliveredAt) { this.deliveredAt = deliveredAt; }
}
