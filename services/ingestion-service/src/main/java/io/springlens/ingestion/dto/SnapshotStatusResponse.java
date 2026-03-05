package io.springlens.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.springlens.ingestion.entity.StartupSnapshot;

import java.time.Instant;
import java.util.UUID;

public record SnapshotStatusResponse(
        @JsonProperty("snapshot_id") UUID snapshotId,
        String status,
        @JsonProperty("total_startup_ms") int totalStartupMs,
        @JsonProperty("bottleneck_count") int bottleneckCount,
        @JsonProperty("recommendation_count") int recommendationCount,
        @JsonProperty("completed_at") Instant completedAt) {

    public static SnapshotStatusResponse from(StartupSnapshot snapshot) {
        return new SnapshotStatusResponse(
                snapshot.getId(),
                snapshot.getStatus(),
                snapshot.getTotalStartupMs(),
                snapshot.getBottleneckCount(),
                snapshot.getRecommendationCount(),
                snapshot.getProcessedAt());
    }
}
