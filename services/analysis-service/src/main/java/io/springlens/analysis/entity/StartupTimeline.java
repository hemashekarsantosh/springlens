package io.springlens.analysis.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * JPA entity storing analyzed startup timeline results as JSONB.
 */
@Entity
@Table(name = "startup_timelines")
public class StartupTimeline {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "snapshot_id", nullable = false, unique = true)
    private UUID snapshotId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_name", nullable = false)
    private String environmentName;

    @Column(name = "git_commit_sha", nullable = false)
    private String gitCommitSha;

    @Column(name = "total_startup_ms", nullable = false)
    private int totalStartupMs;

    @Column(name = "bottleneck_count", nullable = false)
    private int bottleneckCount;

    @Column(name = "bean_count", nullable = false)
    private int beanCount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "timeline_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> timelineData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bean_graph_data", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> beanGraphData;

    @Column(name = "analyzed_at", nullable = false)
    private Instant analyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StartupTimeline() {
    }

    public static StartupTimeline create(UUID snapshotId, UUID workspaceId, UUID projectId,
                                          String environmentName, String gitCommitSha,
                                          int totalStartupMs, int bottleneckCount, int beanCount,
                                          Map<String, Object> timelineData,
                                          Map<String, Object> beanGraphData) {
        var t = new StartupTimeline();
        t.id = UUID.randomUUID();
        t.snapshotId = snapshotId;
        t.workspaceId = workspaceId;
        t.projectId = projectId;
        t.environmentName = environmentName;
        t.gitCommitSha = gitCommitSha;
        t.totalStartupMs = totalStartupMs;
        t.bottleneckCount = bottleneckCount;
        t.beanCount = beanCount;
        t.timelineData = timelineData;
        t.beanGraphData = beanGraphData;
        t.analyzedAt = Instant.now();
        t.createdAt = Instant.now();
        t.updatedAt = Instant.now();
        return t;
    }

    // Getters
    public UUID getId() { return id; }
    public UUID getSnapshotId() { return snapshotId; }
    public UUID getWorkspaceId() { return workspaceId; }
    public UUID getProjectId() { return projectId; }
    public String getEnvironmentName() { return environmentName; }
    public String getGitCommitSha() { return gitCommitSha; }
    public int getTotalStartupMs() { return totalStartupMs; }
    public int getBottleneckCount() { return bottleneckCount; }
    public int getBeanCount() { return beanCount; }
    public Map<String, Object> getTimelineData() { return timelineData; }
    public Map<String, Object> getBeanGraphData() { return beanGraphData; }
    public Instant getAnalyzedAt() { return analyzedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
