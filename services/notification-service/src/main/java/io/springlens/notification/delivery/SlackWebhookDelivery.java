package io.springlens.notification.delivery;

import io.springlens.notification.event.RecommendationsReadyEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Delivers startup analysis notifications to Slack incoming webhooks.
 */
@Component
public class SlackWebhookDelivery {

    private static final Logger log = LoggerFactory.getLogger(SlackWebhookDelivery.class);

    private final RestClient restClient;

    public SlackWebhookDelivery() {
        this.restClient = RestClient.builder().build();
    }

    public int deliver(String webhookUrl, RecommendationsReadyEvent event) {
        log.info("Delivering Slack notification snapshot={} workspace={}", event.snapshotId(), event.workspaceId());

        var payload = buildSlackPayload(event);

        try {
            var response = restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            int statusCode = response.getStatusCode().value();
            log.info("Slack delivery status={} snapshot={}", statusCode, event.snapshotId());
            return statusCode;
        } catch (Exception ex) {
            log.error("Slack delivery failed snapshot={} error={}", event.snapshotId(), ex.getMessage());
            throw ex;
        }
    }

    private Map<String, Object> buildSlackPayload(RecommendationsReadyEvent event) {
        String savingsSec = String.format("%.1f", event.totalPotentialSavingsMs() / 1000.0);

        String topRecsText = event.topRecommendations().stream()
                .limit(3)
                .map(r -> String.format("• *%s* — %s (%dms savings, %s effort)",
                        r.title(), r.category(), r.estimatedSavingsMs(), r.effort()))
                .collect(Collectors.joining("\n"));

        return Map.of(
                "blocks", List.of(
                        Map.of("type", "header",
                                "text", Map.of("type", "plain_text",
                                        "text", String.format("SpringLens: %d new recommendations for %s",
                                                event.recommendationCount(), event.environmentName()))),
                        Map.of("type", "section",
                                "text", Map.of("type", "mrkdwn",
                                        "text", String.format("*Potential startup savings: %ss*\n\nTop recommendations:\n%s",
                                                savingsSec, topRecsText.isEmpty() ? "_None_" : topRecsText))),
                        Map.of("type", "context",
                                "elements", List.of(Map.of("type", "mrkdwn",
                                        "text", String.format("Snapshot: `%s` | Project: `%s`",
                                                event.snapshotId(), event.projectId()))))));
    }
}
