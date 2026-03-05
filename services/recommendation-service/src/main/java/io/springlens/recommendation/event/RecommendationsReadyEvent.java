package io.springlens.recommendation.event;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kafka event published to recommendations.ready topic after recommendations are generated.
 * Consumed by notification-service.
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
