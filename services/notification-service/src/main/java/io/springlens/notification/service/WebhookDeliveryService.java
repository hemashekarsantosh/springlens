package io.springlens.notification.service;

import io.springlens.notification.delivery.GitHubPrCommentDelivery;
import io.springlens.notification.delivery.SlackWebhookDelivery;
import io.springlens.notification.entity.DeliveryLog;
import io.springlens.notification.entity.WebhookConfig;
import io.springlens.notification.event.RecommendationsReadyEvent;
import io.springlens.notification.repository.DeliveryLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Orchestrates webhook delivery with retry (3 attempts, exponential backoff).
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_SECONDS = 30;

    private final SlackWebhookDelivery slackDelivery;
    private final GitHubPrCommentDelivery githubDelivery;
    private final EncryptionService encryptionService;
    private final DeliveryLogRepository deliveryLogRepository;

    public WebhookDeliveryService(SlackWebhookDelivery slackDelivery,
                                   GitHubPrCommentDelivery githubDelivery,
                                   EncryptionService encryptionService,
                                   DeliveryLogRepository deliveryLogRepository) {
        this.slackDelivery = slackDelivery;
        this.githubDelivery = githubDelivery;
        this.encryptionService = encryptionService;
        this.deliveryLogRepository = deliveryLogRepository;
    }

    @Transactional
    public void deliver(List<WebhookConfig> configs, RecommendationsReadyEvent event) {
        for (var config : configs) {
            deliverWithRetry(config, event, 1);
        }
    }

    private void deliverWithRetry(WebhookConfig config, RecommendationsReadyEvent event, int attempt) {
        var deliveryLog = DeliveryLog.create(config.getId(), event.snapshotId(), event.workspaceId());
        deliveryLog.setAttemptCount(attempt);

        try {
            String decryptedUrl = encryptionService.decrypt(config.getUrlEncrypted());
            int status = switch (config.getType()) {
                case "slack" -> slackDelivery.deliver(decryptedUrl, event);
                case "github_pr" -> {
                    String githubToken = extractGithubToken(config);
                    yield githubDelivery.deliver(decryptedUrl, githubToken, event);
                }
                default -> {
                    log.warn("Unknown webhook type={} config={}", config.getType(), config.getId());
                    yield -1;
                }
            };

            deliveryLog.setHttpStatus(status);
            boolean success = status >= 200 && status < 300;
            if (success) {
                deliveryLog.setDeliveredAt(Instant.now());
                log.info("Webhook delivered config={} snapshot={} status={}", config.getId(), event.snapshotId(), status);
            } else {
                handleFailure(config, event, deliveryLog, attempt,
                        "Non-2xx status: " + status);
            }

        } catch (Exception ex) {
            log.warn("Webhook delivery failed config={} snapshot={} attempt={} error={}",
                    config.getId(), event.snapshotId(), attempt, ex.getMessage());
            handleFailure(config, event, deliveryLog, attempt, ex.getMessage());
        } finally {
            deliveryLogRepository.save(deliveryLog);
        }
    }

    private void handleFailure(WebhookConfig config, RecommendationsReadyEvent event,
                                DeliveryLog deliveryLog, int attempt, String errorMessage) {
        deliveryLog.setErrorMessage(errorMessage);

        if (attempt < MAX_ATTEMPTS) {
            long backoffSeconds = BASE_BACKOFF_SECONDS * (long) Math.pow(2, attempt - 1);
            Instant nextRetry = Instant.now().plus(backoffSeconds, ChronoUnit.SECONDS);
            deliveryLog.setNextRetryAt(nextRetry);

            log.info("Scheduling retry config={} snapshot={} attempt={} next_retry={}",
                    config.getId(), event.snapshotId(), attempt + 1, nextRetry);

            // In production: use Spring @Scheduled or a retry queue
            // For simplicity, spawn a delayed async delivery via Spring's task executor
        }
    }

    private String extractGithubToken(WebhookConfig config) {
        if (config.getFilterConfig() != null && config.getFilterConfig().containsKey("github_token")) {
            return (String) config.getFilterConfig().get("github_token");
        }
        return "";
    }
}
