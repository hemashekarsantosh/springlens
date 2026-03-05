package io.springlens.notification.consumer;

import io.springlens.notification.event.RecommendationsReadyEvent;
import io.springlens.notification.repository.WebhookConfigRepository;
import io.springlens.notification.service.WebhookDeliveryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for recommendations.ready topic.
 * Looks up webhook configs and triggers delivery.
 */
@Component
public class RecommendationsReadyConsumer {

    private static final Logger log = LoggerFactory.getLogger(RecommendationsReadyConsumer.class);

    private final WebhookConfigRepository webhookConfigRepository;
    private final WebhookDeliveryService deliveryService;

    public RecommendationsReadyConsumer(WebhookConfigRepository webhookConfigRepository,
                                         WebhookDeliveryService deliveryService) {
        this.webhookConfigRepository = webhookConfigRepository;
        this.deliveryService = deliveryService;
    }

    @KafkaListener(topics = "recommendations.ready", groupId = "notification-service",
            containerFactory = "recommendationsReadyListenerContainerFactory")
    public void consume(RecommendationsReadyEvent event) {
        log.info("Consuming RecommendationsReadyEvent snapshot={} workspace={} project={}",
                event.snapshotId(), event.workspaceId(), event.projectId());

        var configs = webhookConfigRepository.findEnabledForDelivery(
                event.workspaceId(), event.projectId());

        if (configs.isEmpty()) {
            log.debug("No enabled webhook configs for workspace={} project={}",
                    event.workspaceId(), event.projectId());
            return;
        }

        log.info("Delivering to {} webhook configs snapshot={}", configs.size(), event.snapshotId());
        deliveryService.deliver(configs, event);
    }
}
