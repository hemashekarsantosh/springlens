package io.springlens.recommendation.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ci_budgets")
public class CiBudget {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "environment", nullable = false)
    private String environment;

    @Column(name = "budget_ms", nullable = false)
    private int budgetMs;

    @Column(name = "alert_threshold_ms")
    private Integer alertThresholdMs;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CiBudget() {
    }

    public static CiBudget create(UUID projectId, UUID workspaceId, String environment,
                                   int budgetMs, Integer alertThresholdMs, boolean enabled,
                                   UUID createdBy) {
        var b = new CiBudget();
        b.id = UUID.randomUUID();
        b.projectId = projectId;
        b.workspaceId = workspaceId;
        b.environment = environment;
        b.budgetMs = budgetMs;
        b.alertThresholdMs = alertThresholdMs;
        b.enabled = enabled;
        b.createdBy = createdBy;
        b.createdAt = Instant.now();
        b.updatedAt = Instant.now();
        return b;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public String getEnvironment() { return environment; }
    public int getBudgetMs() { return budgetMs; }
    public Integer getAlertThresholdMs() { return alertThresholdMs; }
    public boolean isEnabled() { return enabled; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setBudgetMs(int budgetMs) { this.budgetMs = budgetMs; }
    public void setAlertThresholdMs(Integer alertThresholdMs) { this.alertThresholdMs = alertThresholdMs; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
