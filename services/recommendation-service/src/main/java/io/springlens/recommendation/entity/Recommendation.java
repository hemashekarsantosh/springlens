package io.springlens.recommendation.entity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity mapping to the recommendations table.
 */
@Entity
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_name", nullable = false)
    private String environmentName;

    @Column(name = "rank", nullable = false)
    private int rank;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "estimated_savings_ms", nullable = false)
    private int estimatedSavingsMs;

    @Column(name = "estimated_savings_percent", nullable = false)
    private double estimatedSavingsPercent;

    @Column(name = "effort", nullable = false)
    private String effort;

    @Column(name = "status", nullable = false)
    private String status = "active";

    @Column(name = "code_snippet")
    private String codeSnippet;

    @Column(name = "config_snippet")
    private String configSnippet;

    @Column(name = "warnings", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<String> warnings;

    @Column(name = "affected_beans", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private List<String> affectedBeans;

    @Column(name = "graalvm_feasibility", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Map<String, Object> graalvmFeasibility;

    @Column(name = "applied_note")
    private String appliedNote;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Recommendation() {
    }

    public static Recommendation create(UUID snapshotId, UUID workspaceId, UUID projectId,
                                         String environmentName, int rank, String category,
                                         String title, String description,
                                         int estimatedSavingsMs, double estimatedSavingsPercent,
                                         String effort, String codeSnippet, String configSnippet,
                                         List<String> warnings, List<String> affectedBeans,
                                         Map<String, Object> graalvmFeasibility) {
        var r = new Recommendation();
        r.id = UUID.randomUUID();
        r.snapshotId = snapshotId;
        r.workspaceId = workspaceId;
        r.projectId = projectId;
        r.environmentName = environmentName;
        r.rank = rank;
        r.category = category;
        r.title = title;
        r.description = description;
        r.estimatedSavingsMs = estimatedSavingsMs;
        r.estimatedSavingsPercent = estimatedSavingsPercent;
        r.effort = effort;
        r.status = "active";
        r.codeSnippet = codeSnippet;
        r.configSnippet = configSnippet;
        r.warnings = warnings != null ? warnings : List.of();
        r.affectedBeans = affectedBeans != null ? affectedBeans : List.of();
        r.graalvmFeasibility = graalvmFeasibility;
        r.createdAt = Instant.now();
        r.updatedAt = Instant.now();
        return r;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getSnapshotId() { return snapshotId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public String getEnvironmentName() { return environmentName; }
    public int getRank() { return rank; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getEstimatedSavingsMs() { return estimatedSavingsMs; }
    public double getEstimatedSavingsPercent() { return estimatedSavingsPercent; }
    public String getEffort() { return effort; }
    public String getStatus() { return status; }
    public String getCodeSnippet() { return codeSnippet; }
    public String getConfigSnippet() { return configSnippet; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getAffectedBeans() { return affectedBeans; }
    public Map<String, Object> getGraalvmFeasibility() { return graalvmFeasibility; }
    public String getAppliedNote() { return appliedNote; }
    public Instant getAppliedAt() { return appliedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    // Setters
    public void setStatus(String status) { this.status = status; }
    public void setAppliedNote(String appliedNote) { this.appliedNote = appliedNote; }
    public void setAppliedAt(Instant appliedAt) { this.appliedAt = appliedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
