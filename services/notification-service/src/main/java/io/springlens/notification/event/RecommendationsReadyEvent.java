package io.springlens.notification.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event consumed from recommendations.ready topic.
 * Must mirror io.springlens.recommendation.event.RecommendationsReadyEvent schema.
 */
public record RecommendationsReadyEvent(
        UUID snapshotId,
        UUID workspaceId,
        UUID projectId,
        String environmentName,
        int recommendationCount,
        int totalPotentialSavingsMs,
        List<RecommendationSummary> topRecommendations,
        Instant generatedAt) implements Serializable {

    public record RecommendationSummary(
            UUID recommendationId,
            String category,
            String title,
            int estimatedSavingsMs,
            String effort) {
    }
}
