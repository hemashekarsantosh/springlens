package io.springlens.ingestion.entity;

import io.springlens.ingestion.dto.StartupSnapshotRequest;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity mapping to startup_snapshots TimescaleDB hypertable.
 * The composite PK (id, captured_at) is required by TimescaleDB partitioning.
 */
@Entity
@Table(name = "startup_snapshots")
@IdClass(StartupSnapshotId.class)
public class StartupSnapshot {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Id
    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id")
    private UUID environmentId;

    @Column(name = "environment_name", nullable = false)
    private String environmentName;

    @Column(name = "git_commit_sha", nullable = false)
    private String gitCommitSha;

    @Column(name = "total_startup_ms", nullable = false)
    private int totalStartupMs;

    @Column(name = "spring_boot_version", nullable = false)
    private String springBootVersion;

    @Column(name = "java_version", nullable = false)
    private String javaVersion;

    @Column(name = "agent_version", nullable = false)
    private String agentVersion;

    @Column(name = "hostname")
    private String hostname;

    @Column(name = "status", nullable = false)
    private String status = "queued";

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Transient
    private int bottleneckCount;

    @Transient
    private int recommendationCount;

    protected StartupSnapshot() {
    }

    public static StartupSnapshot from(StartupSnapshotRequest request, UUID projectId, UUID workspaceId) {
        var snapshot = new StartupSnapshot();
        snapshot.id = UUID.randomUUID();
        snapshot.capturedAt = Instant.now();
        snapshot.workspaceId = workspaceId;
        snapshot.projectId = projectId;
        snapshot.environmentName = request.environment();
        snapshot.gitCommitSha = request.gitCommitSha();
        snapshot.totalStartupMs = request.totalStartupMs();
        snapshot.springBootVersion = request.springBootVersion();
        snapshot.javaVersion = request.javaVersion();
        snapshot.agentVersion = request.agentVersion();
        snapshot.hostname = request.hostname();
        snapshot.status = "queued";
        return snapshot;
    }

    // Getters
    public UUID getId() { return id; }
    public Instant getCapturedAt() { return capturedAt; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public UUID getEnvironmentId() { return environmentId; }
    public String getEnvironmentName() { return environmentName; }
    public String getGitCommitSha() { return gitCommitSha; }
    public int getTotalStartupMs() { return totalStartupMs; }
    public String getSpringBootVersion() { return springBootVersion; }
    public String getJavaVersion() { return javaVersion; }
    public String getAgentVersion() { return agentVersion; }
    public String getHostname() { return hostname; }
    public String getStatus() { return status; }
    public Instant getProcessedAt() { return processedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public int getBottleneckCount() { return bottleneckCount; }
    public int getRecommendationCount() { return recommendationCount; }

    // Setters
    public void setStatus(String status) { this.status = status; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }
    public void setBottleneckCount(int bottleneckCount) { this.bottleneckCount = bottleneckCount; }
    public void setRecommendationCount(int recommendationCount) { this.recommendationCount = recommendationCount; }
}
