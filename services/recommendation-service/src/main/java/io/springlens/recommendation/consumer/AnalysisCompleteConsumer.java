package io.springlens.recommendation.consumer;

import io.springlens.recommendation.engine.RecommendationEngine;
import io.springlens.recommendation.entity.Recommendation;
import io.springlens.recommendation.event.AnalysisCompleteEvent;
import io.springlens.recommendation.event.RecommendationsReadyEvent;
import io.springlens.recommendation.repository.RecommendationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Kafka consumer for analysis.complete topic.
 * Generates and persists recommendations, then publishes RecommendationsReadyEvent.
 */
@Component
public class AnalysisCompleteConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisCompleteConsumer.class);
    private static final String RECOMMENDATIONS_READY_TOPIC = "recommendations.ready";

    private final RecommendationEngine engine;
    private final RecommendationRepository recommendationRepository;
    private final KafkaTemplate<String, RecommendationsReadyEvent> kafkaTemplate;

    public AnalysisCompleteConsumer(RecommendationEngine engine,
                                     RecommendationRepository recommendationRepository,
                                     KafkaTemplate<String, RecommendationsReadyEvent> kafkaTemplate) {
        this.engine = engine;
        this.recommendationRepository = recommendationRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(topics = "analysis.complete", groupId = "recommendation-service",
            containerFactory = "analysisCompleteListenerContainerFactory")
    @Transactional
    public void consume(AnalysisCompleteEvent event) {
        log.info("Consuming AnalysisCompleteEvent snapshot={} project={} env={}",
                event.snapshotId(), event.projectId(), event.environmentName());

        try {
            // Idempotency: skip if we already have recommendations for this snapshot
            var existing = recommendationRepository.findBySnapshotId(event.snapshotId());
            if (!existing.isEmpty()) {
                log.info("Recommendations already exist for snapshot={}, skipping", event.snapshotId());
                return;
            }

            // Generate recommendations
            List<Recommendation> recommendations = engine.generateRecommendations(event);
            recommendationRepository.saveAll(recommendations);

            // Build event payload
            int totalSavings = recommendations.stream()
                    .mapToInt(Recommendation::getEstimatedSavingsMs)
                    .sum();

            List<RecommendationsReadyEvent.RecommendationSummary> topRecs = recommendations.stream()
                    .limit(3)
                    .map(r -> new RecommendationsReadyEvent.RecommendationSummary(
                            r.getId(), r.getCategory(), r.getTitle(),
                            r.getEstimatedSavingsMs(), r.getEffort()))
                    .toList();

            var readyEvent = new RecommendationsReadyEvent(
                    event.snapshotId(),
                    event.workspaceId(),
                    event.projectId(),
                    event.environmentName(),
                    recommendations.size(),
                    totalSavings,
                    topRecs,
                    Instant.now());

            kafkaTemplate.send(RECOMMENDATIONS_READY_TOPIC, event.snapshotId().toString(), readyEvent)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish RecommendationsReadyEvent snapshot={}", event.snapshotId(), ex);
                        } else {
                            log.debug("Published RecommendationsReadyEvent snapshot={}", event.snapshotId());
                        }
                    });

            log.info("Recommendations generated count={} snapshot={} total_savings_ms={}",
                    recommendations.size(), event.snapshotId(), totalSavings);

        } catch (Exception ex) {
            log.error("Recommendation generation failed snapshot={}", event.snapshotId(), ex);
            throw ex;
        }
    }
}
